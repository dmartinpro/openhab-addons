# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

This is `org.openhab.binding.diagral`, an openHAB binding bundle that lives inside the larger `openhab-addons` monorepo (parent reactor at `/Users/dmartin/Documents/projects/openhab-addons`). It integrates Diagral home alarm systems via Diagral's cloud REST API (`https://appv3.tt-monitor.com/emerald/v1`), using HMAC-SHA256 request signing. There is no local/LAN protocol support — everything goes through the cloud.

The repo-wide `AGENTS.md` at the monorepo root (`../../AGENTS.md`) has general openHAB add-on conventions (Java 21, import ordering, `mvn spotless:apply`, binding-skeleton creation script, etc.) — read it for anything not covered here. This file only covers what's specific to the Diagral binding.

## Documentation requirement (project-specific, stricter than the default openHAB guideline)

The user wants this bundle's source code thoroughly documented for maintainability, beyond openHAB's own guideline (which only requires Javadoc on public/protected/default-visibility members and exempts DTOs — see "Null-safety and visibility" below). For this bundle specifically:

- **Every class and interface** gets a class-level Javadoc block explaining both its functional role (why it exists, what part of the binding it belongs to) and relevant technical detail.
- **Every method — public, protected, package-private, *and* private** — gets a Javadoc block: a one-line functional summary, `@param`/`@return`/`@throws` as applicable, and a short "why/how" note for anything non-obvious. This applies to DTOs too, despite the general openHAB exemption.
- **Standing rule for all future work**: whenever a class or method in this bundle is created or edited, its Javadoc must be added or updated in the same change. Don't leave it for a later cleanup pass.

## Build & Test Commands

Run from **this bundle's directory** unless noted otherwise:

```bash
# Compile only
mvn compile

# Format code (required before committing — CI enforces this)
mvn spotless:apply

# Run tests
mvn test

# Full build (compile, test, static analysis, packaging)
mvn clean install
```

To build/test just this bundle from the **monorepo root** instead:

```bash
mvn clean install -pl org.openhab.binding.diagral
```

There is currently no `src/test` directory in this bundle — no unit tests exist yet. `mvn clean install` runs static code analysis; if it finds a Priority 1 issue the build fails. Check `target/code-analysis/report.html` after a build for the full report (Priority 1 = error, 2 = warning, 3 = info — fix at least all Priority 1s).

## Architecture

### Thing hierarchy

- `bridge` — `DiagralBridgeHandler`, holds cloud credentials, owns the HTTP client/auth session, and polls system status on a scheduled interval.
  - `alarm-system` — `DiagralSystemHandler`, arm/disarm control and anomaly reporting.
  - `motion-sensor` / `contact-sensor` — `DiagralMotionSensorHandler` / `DiagralContactSensorHandler`, both extend the shared abstract `DiagralSensorHandler` (`internal/handler/DiagralSensorHandler.java`), which handles config validation, bridge-status propagation, and the common `enabled`/`low-battery` channels. Subclasses only implement `updateSensorSpecificChannels(DiagralDevice)` for their type-specific channel (`motion` or `contact`).
  - `group` — `DiagralGroupHandler`, controls a device group's active state.

`DiagralHandlerFactory` (`internal/DiagralHandlerFactory.java`) maps `ThingTypeUID` → handler class; it's the single place new thing types must be registered.

**`alarm-system`'s `mode-control` vs. a `group`'s `active` channel are two genuinely different control paths, not
two views of the same thing.** `mode-control` arms/disarms the whole system via one of five named modes (`OFF`/
`FULL`/`PRESENCE`/`PARTIAL1`/`PARTIAL2`), each of which arms whichever groups are members of that mode (see each
group's discovery-time `armModes` property). Directly toggling one `group`'s `active` channel instead calls
`activate_group`/`disable_group`.

**The real API's `activated_groups` field (in the `/status` response) cannot be trusted at all - confirmed live
(2026-09-03) empty under every status this binding has observed, including a fully-settled named mode like
`PRESENCE`.** Arming/disarming is also not instant: for up to a zone's `outputDelay` seconds, `/status` reports a
transitional value - `TEMPO_1`/`TEMPO_2` while heading to `PARTIAL1`+`PRESENCE`/`PARTIAL2`, or `TEMPO_GROUP` for
both `FULL` (apparently implemented server-side as "activate every zone") and any direct single-group activation.
`DiagralBridgeHandler.isGroupActive()` is the single source of truth for a group's active state, and handles both
findings: while `armed-status` is one of the five named modes (`DiagralBindingConstants.NAMED_SYSTEM_MODES`), it
derives membership fresh from the cached `DiagralSystemConfiguration`'s per-mode group lists (`presenceGroup`/
`partialGroup1`/`partialGroup2`, or all groups for `FULL`) - self-correcting every poll regardless of how the mode
was set, including after a restart or a change made via the official e-ONE app. During any other (transitional)
status, it falls back to `activeGroupIds`, a best-effort bridge-local record kept current by `setSystemMode()`
(optimistically, to the target mode's membership, the moment the command succeeds) and by `activateGroup()`/
`disableGroup()` (a single group at a time). `DiagralGroupHandler` no longer touches `activated_groups` itself -
it just asks the bridge. See the README's "Known Bugs" section for the user-facing writeup.

**A command that times out client-side may still apply server-side** - confirmed live (2026-09-03) that the
Diagral cloud API can be slow enough to exceed the 10s request timeout while still processing the request.
`setSystemMode()`/`activateGroup()`/`disableGroup()` (like the pre-existing `enableDevice()`/`disableDevice()`)
always trigger an immediate re-poll in a `finally` block regardless of outcome, so the UI re-syncs to the real
state within one poll cycle rather than staying stale - potentially showing an incorrect armed/disarmed status -
until the next scheduled interval.

### Bridge / HTTP / auth split

Three collaborating classes under `internal/bridge/`:

- `DiagralAuthenticationManager` — holds credentials (username, password, serialId, pinCode) plus the current `apiKey`/`secretKey` pair; computes the HMAC-SHA256 signature (`timestamp.serialId.apiKey`, signed with the secret key) via `DiagralCryptoUtil`.
- `DiagralHttpClient` — does the actual Jetty HTTP calls. `authenticate()` runs a two-step flow: `login()` (username/password → bearer access token) then `generateApiKey()` (access token → apiKey/secretKey pair, stored in the auth manager). All subsequent signed requests use `X-HMAC` / `X-TIMESTAMP` / `X-APIKEY` (and `X-PIN-CODE` for pin-gated endpoints) headers instead of the bearer token. A 400/401/403 response clears the stored API keys, forcing re-authentication on the next poll.
- `DiagralBridgeHandler` — orchestrates the above, exposes cached `getSystemConfiguration()`/`getSystemDetails()` and live `getSystemStatus()` to child thing handlers, runs the polling loop (`scheduleWithFixedDelay`, interval from `DiagralBridgeConfiguration.refreshInterval`), and re-authenticates automatically when polling hits a `DiagralAuthenticationException`.

Child handlers never talk to the network directly — they always go through `getBridgeHandler()` on the parent bridge.

### Discovery

`DiagralDiscoveryService` (`internal/discovery/DiagralDiscoveryService.java`) is a `PROTOTYPE`-scoped `AbstractThingHandlerDiscoveryService<DiagralBridgeHandler>` registered via `DiagralBridgeHandler.getServices()`. It reads the bridge's cached `DiagralSystemConfiguration` and emits discovery results for the alarm system, sensors, and groups. Sensor thing-type resolution (`getThingTypeForDevice`) is based on `device.type` + `device.refCode` against the `DEVICE_*_CODE`/`DEVICE_*_TYPE` constants in `DiagralBindingConstants` — add new device codes there when supporting new hardware.

### DTOs and constants

`internal/dto/*` are plain Gson-mapped POJOs mirroring the Diagral API JSON (login, API key exchange, system status/configuration/details, devices, groups, anomalies). `DiagralBindingConstants` centralizes thing-type UIDs, channel IDs, API endpoints/headers, system modes, device type/refCode constants, and config-status message keys — check there first before hardcoding a string literal.

### Resources

- `src/main/resources/OH-INF/thing/thing-types.xml` — thing/channel definitions (must stay in sync with `DiagralBindingConstants` channel IDs).
- `src/main/resources/OH-INF/config/config.xml`, `OH-INF/addon/addon.xml`, `OH-INF/i18n/diagral.properties` — config descriptions, addon metadata, and translated labels/messages (including the `*_MISSING` config-status keys used by `DiagralBridgeHandler.getConfigStatus()`).

## openHAB Coding Guidelines

(From https://www.openhab.org/docs/developer/guidelines.html and https://www.openhab.org/docs/developer/bindings/ — apply these when writing or reviewing code in this bundle.)

### Naming

- Thing-type IDs, channel-group IDs, channel IDs: `lower-case-hyphen` (e.g. `armed-status`, `motion-sensor`) — matches `DiagralBindingConstants.CHANNEL_*`/`THING_TYPE_*` and `thing-types.xml`.
- Thing properties and config parameters: `camelCase` (e.g. `serialId`, `refreshInterval`).
- XML resource filenames: `lower-case-hyphen.xml`.

### Null-safety and visibility

- Every class except DTOs/classes literally named `*DTO` must be annotated `@NonNullByDefault` (all classes in this bundle already are, including the `internal/dto` package — keep new ones consistent).
- Mark nullable fields/returns explicitly with `@Nullable`.
- An OSGi `@Reference`-injected field that can't be null-checked at construction uses `@NonNullByDefault({})` (see `DiagralHandlerFactory`'s constructor pattern if adding new `@Reference` fields).
- Put implementation classes under `internal` (already the case here) unless they're meant to be used by other bindings/scripts.
- openHAB's own guideline requires JavaDoc on classes, interfaces, enums, constants, and default/protected/public fields and methods, exempting DTOs — but this bundle applies a **stricter, project-specific rule** (see "Documentation requirement" above): JavaDoc on every class/method including private ones and DTOs. Add an `@author` tag on classes; append new contributors below existing ones rather than replacing them.

### Logging

- Loggers are `private final Logger logger = LoggerFactory.getLogger(ThisClass.class);` — non-static, named `logger`. All handlers/bridge/client classes here already follow this.
- Use parameterized logging (`logger.warn("Failed for {}: {}", id, msg)`), never string concatenation.
- Log full stack traces only for genuine bugs; for expected user/config/network errors, log `e.getMessage()` only.
- Don't log for expected external failures (dropped connection, rate limiting) — reflect it via `updateStatus()` instead of (or in addition to sparingly) logging.
- Never log simple method entry/exit, and don't log a state change that's already communicated through `updateState()`/`updateStatus()`.
- Level guide: `error` = binding/system can't function or a real bug; `warn` = a recoverable setup/runtime issue; `info` = sparingly, e.g. start-up milestones; `debug` = unexpected-but-handled conditions, transient connection issues; `trace` = verbose payloads.
- Worth revisiting in this bundle: `DiagralBridgeHandler.authenticate()` and `poll()`'s re-authentication path currently use `logger.error` for authentication failures that are often just bad/expired credentials — consider whether `warn` plus a clear `ThingStatus` detail is more appropriate per this guidance.

### Threading and lifecycle

- Never spawn threads directly — use the handler's inherited `scheduler` (`ScheduledExecutorService`).
- For periodic jobs without a hard-real-time requirement, prefer `scheduler.scheduleWithFixedDelay(...)` over `scheduleAtFixedRate` (this bundle already does this in `DiagralBridgeHandler.startPolling`).
- `initialize()` and `dispose()` must return quickly and non-blocking — push slow work (auth, first poll) onto `scheduler.execute(...)`, as `DiagralBridgeHandler.initialize()` already does. Cancel every scheduled job in `dispose()`.
- Only ever set `ThingStatus.ONLINE`, `OFFLINE`, or `UNKNOWN` yourself; the framework manages `REMOVING`/`REMOVED`/etc. Always pair `OFFLINE` with a `ThingStatusDetail` and a human-readable message.
- `handleCommand()` must catch and translate communication exceptions into a status update rather than throwing — don't let exceptions escape lifecycle/command methods.
- Bridge handlers: children look up the bridge via `getBridge().getHandler()` (see `DiagralSensorHandler.getBridgeHandler()`); bridge status changes propagate to children through `bridgeStatusChanged(ThingStatusInfo)`, already implemented per-handler here.

### Formatting / static analysis

- `mvn spotless:check` verifies formatting (imports sorted/grouped, 2-space indent in `pom.xml`, tab indent + 120-col in other XML); `mvn spotless:apply` fixes it. Run this before every commit.
- `mvn clean install` also runs the guideline static analyzer; a Priority 1 finding fails the build (see Build & Test Commands above for the report path).

## Testing conventions (for when tests are added)

(From https://www.openhab.org/docs/developer/tests.html — this bundle has no tests yet, but new ones must follow these rules or Maven's Surefire plugin won't pick them up / the build guidelines will flag them.)

- Unit tests live under `src/test/java`, mirroring the `internal` package structure; test classes must be **named `*Test`** (singular suffix) or Surefire won't run them, and test methods need `@Test`.
- Use JUnit 5 (Jupiter) + Mockito: `@ExtendWith(MockitoExtension.class)` on the test class, `@Mock` for collaborators (e.g. mock the Jetty `HttpClient`/`ContentResponse` to unit-test `DiagralHttpClient`, or mock `ThingHandlerCallback` to unit-test the handlers without a live framework).
- Prefer Hamcrest matchers over raw JUnit asserts for anything beyond a null/boolean check — they give better failure output.
- `@BeforeEach`/`@AfterEach` for setup/teardown; free any resources opened in a test.
- Full OSGi integration tests (`JavaOSGiTest`, run from an `itests/` bundle with a `.bndrun`) are for scenarios that truly need a live framework/registry — this repo's `itests/` directory is at the monorepo root, not per-bundle, and openHAB's own guidance says to use them sparingly since they're much slower than mocked unit tests. Prefer a plain Mockito unit test first.

## Known incomplete areas

- `DiagralBridgeHandler.registerDiscoveryListener` has a `TODO: complete this function` — it currently just stores the listener without pushing already-known devices to it.
- `DiagralBridgeHandler.poll()` has a `TODO` noting that consecutive poll failures should eventually flip the bridge offline; currently a single failed poll is logged and ignored.
- `DiagralBridgeHandler.initialize()` never retries a failed *first* authentication attempt — confirmed live (2026-09-03) that a transient network failure during startup (the same flakiness `poll()` already tolerates) leaves the bridge permanently `OFFLINE`/`COMMUNICATION_ERROR` until it's manually reinitialized (disable/enable the Thing, or restart openHAB), even though the exact same failure mid-poll self-heals on the next scheduled cycle. Worth giving `initialize()` the same resilience `poll()` already has, e.g. scheduling a retry rather than giving up after one attempt.
- `DiagralBridgeHandler.isGroupActive()` doesn't yet trust the settled status `GROUP`, even though the real API reliably populates `activated_groups` while in that state. **Conclusively proven live (2026-09-04)** by adding `TRACE`-level logging to `isGroupActive()`/`getDisplayedMode()` (kept in the code, controlled by the logger's level, see the diagnostic-only Javadoc notes on both methods) and activating group 2 via the official e-ONE app (external to openHAB, nothing here was triggered through this binding). The exact trace sequence:

  ```
  11:46:34 Failed to get system status: Request timeout
  11:46:34 isGroupActive(1): status=null (not a named mode), activeGroupIds=[], result=false
  11:46:44 Failed to get system status: Request timeout
  11:46:44 isGroupActive(2): status=null (not a named mode), activeGroupIds=[], result=false
  11:46:46 HTTP response received: {"status":"TEMPO_GROUP","activated_groups":[]}
  11:47:48 isGroupActive(2): status=TEMPO_GROUP (not a named mode), activeGroupIds=[], result=false
  11:50:05 HTTP response received: {"status":"GROUP","activated_groups":[2]}
  11:50:05 isGroupActive(2): status=GROUP (not a named mode), activeGroupIds=[], result=false
  ```

  Two distinct, independently-confirmed mechanisms are visible here, both need fixing for full reliability:

  1. **Timing/race** (`11:46:34`-`11:46:44`): each child handler's own `getSystemStatus()` call independently re-fetches once the 5s cache expires; under real-world network latency (very common against this API, see the many `Request timeout`/`EOFException` lines throughout this bundle's logs) two consecutive handlers can each hit their own 10s timeout and see `status=null`, missing the real data entirely. This is the same root cause as the `refreshChildHandlers()` entry below - one status snapshot per poll, shared across handlers, would prevent it.
  2. **Logic gap** (`11:50:05`, the smoking gun): once status genuinely settles to `GROUP`, the API handed back `activated_groups:[2]` - the exact correct answer, precisely matching what was armed - and `isGroupActive()` still returned `false`, because `GROUP` isn't in `NAMED_SYSTEM_MODES`, so it falls straight to the empty `activeGroupIds` fallback without ever looking at `activated_groups`. This is a pure logic bug, not a timing issue - the correct data was sitting right there in the response and got ignored.

  **Proposed fix for mechanism 2** (not yet implemented - mechanism 1 needs the separate one-snapshot-per-poll fix below): add a constant (e.g. `SYSTEM_STATUS_GROUP = "GROUP"`) to `DiagralBindingConstants` alongside `MODE_TEMPO_GROUP`, and give `isGroupActive()` a second authoritative branch before the `activeGroupIds` fallback:

  ```java
  public boolean isGroupActive(String groupId) {
      DiagralSystemStatus status = getSystemStatus();
      String mode = status == null ? null : status.status;
      if (mode != null && NAMED_SYSTEM_MODES.contains(mode)) {
          Set<String> members = groupsForMode(mode);
          return members != null && members.contains(groupId);
      }
      if (SYSTEM_STATUS_GROUP.equals(mode) && status.activatedGroups != null) {
          boolean active = status.activatedGroups.stream().map(String::valueOf).anyMatch(groupId::equals);
          // Opportunistically resync activeGroupIds to match, mirroring how getDisplayedMode() already
          // refreshes lastKnownMode - keeps the fallback honest for the next TEMPO_GROUP-only read.
          if (active) {
              activeGroupIds.add(groupId);
          } else {
              activeGroupIds.remove(groupId);
          }
          return active;
      }
      return activeGroupIds.contains(groupId);
  }
  ```

  **Scope limitation of this fix, also confirmed live in the same trace above (the `11:47:48` line)**: it does not close the gap during the `TEMPO_GROUP` window itself, before settling to `GROUP` - `activated_groups` was empty at that point too, so there is currently no way to know which group is involved while still transitional. The fix above only helps once (and if) it settles to `GROUP`.

  **Do not** extend this same trust to `getDisplayedMode()`/`mode-control` - `GROUP` is correctly excluded from that channel by design (it's not one of the five arming modes), this fix is scoped to `isGroupActive()` only.
- `DiagralBridgeHandler.refreshChildHandlers()` processes every child handler sequentially within one `poll()` call, but each handler independently calls `getSystemStatus()` (5s TTL cache) rather than sharing one snapshot for the whole cycle. Confirmed live (2026-09-04) while testing group activation from Diagral's own e-ONE app (external to openHAB, so nothing here was triggered through this binding): `DiagralSystemHandler`'s own refresh calls the uncached `getAnomalies()`, which took over 6 seconds in one observed cycle, long enough to push a later handler's `getSystemStatus()` call past the cache TTL and trigger a fresh fetch that landed on a moment where reality had already moved on again. Result: a group's `active` channel stayed `ON` for over two minutes after `armed-status` had already correctly read back `OFF`, only catching up on the next full poll cycle. Not yet fixed; the likely fix is to compute one status snapshot per `poll()` invocation and pass it through to every child handler's refresh, rather than each one independently re-querying the cache.

## Out of scope: automatism "rudes" (shutters, gates, comfort relays)

Diagral's API models a device category called **rudes** (`pydiagral.models.Rudes`) — secondary home-automation
equipment that isn't security/intrusion related: roller shutters, gate/garage-door automatisms, and
comfort/lighting receivers. This binding does **not** implement it, and shouldn't unless the situation described
below changes:

- The `pydiagral` library exposes a `get_automatism_rudes()` method, but Diagral's official cloud API currently
  returns an **empty list** for it (`Rudes(rudes=[])`) regardless of what automatism hardware is actually
  installed. The cloud API today is deliberately scoped to core security functions only: arm/disarm state
  (Full/Night/Partial), intrusion alerts and anomaly reporting (battery, radio link), and webhooks. Community
  home-automation developers have asked Hager/Diagral to open this up, but as of now controlling "comfort"
  devices still requires the official e-ONE app (or the Diagral Secure ecosystem) — there's no API path for it.
- This is also why Phase 6 of the implementation plan (read-only exposure of rudes as a bridge property) is on
  hold: even if implemented, it would surface nothing, since the endpoint has no real data to return today. Revisit
  only if Diagral's API is observed to start returning non-empty `rudes` data.
