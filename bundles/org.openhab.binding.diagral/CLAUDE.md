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
