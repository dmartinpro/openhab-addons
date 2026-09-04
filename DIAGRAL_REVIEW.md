# Diagral Binding: Code Review & Improvement Plan

**Scope:** `bundles/org.openhab.binding.diagral` on branch `diagral-binding` (head `1e4affc18d`), 42 Java files / ~5.4k LOC plus `OH-INF` metadata and `README.md`.

**Method:** full read of every source file, `OH-INF` resource and the README, checked against `AGENTS.md`, the openHAB coding guidelines and the openHAB [Review Checklist](https://github.com/openhab/openhab-addons/wiki/Review-Checklist).

**Not run:** `mvn clean install` and `spotless` could not execute here. The openHAB snapshot repository (`openhab.jfrog.io/openhab/libs-snapshot`) returns HTTP 403 through this environment's proxy, so `org.openhab.core.bom:*:5.2.0-SNAPSHOT` cannot resolve. Every finding below is from code reading, not from a build or static-analysis report. Re-run the build locally before acting.

**Also note:** the branch is rooted on a February 2026 `main` and has no merge base with today's `main`. It needs rebasing before it can go upstream.

---

## Overall assessment

The binding is in good shape structurally: clean package layout (`bridge` / `handler` / `discovery` / `dto` / `exception`), consistent `@NonNullByDefault`, `@author` tags everywhere, a properly scoped `AbstractThingHandlerDiscoveryService`, and genuinely useful comments documenting hard-won findings about the Diagral cloud API's misbehaviour. The `CLAUDE.md` change log is unusually good engineering hygiene.

The problems are concentrated in four places:

1. **Two real user-visible defects** (fabricated sensor states, silent command failures) that matter more than usual because this is an alarm system.
1. **One security issue** (credentials in DEBUG logs).
1. **Lifecycle/threading**: blocking HTTP in `initialize()`, unsynchronised shared mutable state, and a cache-timestamp hack that papers over a design gap.
1. **Volume**: five near-identical handler classes, five near-identical discovery methods, triplicated bridge plumbing, and a pile of dead constants. Roughly 400 lines can go without losing any behaviour.

---

## P1 — Correctness and security (do these first)

### 1.1 Credentials are written to DEBUG logs

`DiagralHttpClient.java:721` logs every response body:

```java
logger.debug("HTTP response received: {}", responseBody);
```

This path is shared by `login()` and `generateApiKey()`, so a DEBUG-level log contains the account `access_token` and, worse, the `api_key` / `secret_key` pair — everything needed to sign requests against the alarm system. Users routinely paste DEBUG logs into forum threads.

**Fix:** move body logging to `trace`, and for the two authentication endpoints log only the status code and body length. `DiagralHttpClient.java:203` (`generateApiKey`'s `logger.debug(... , requestBody)`) should log the serial ID field, not the serialised body — the message already claims that's what it does.

### 1.2 Motion and contact channels publish invented values

- `DiagralMotionSensorHandler.java:61` → always `OnOffType.OFF`
- `DiagralContactSensorHandler.java:61` → always `OpenClosedType.CLOSED`

The API provides no such data, and both classes say so in a comment. The result is a `Contact` item that reports every door closed forever: a rule like `if (FrontDoor_Contact == OPEN)` can never fire, and the UI actively lies. This is exactly the case `AGENTS.md` forbids ("Do not map missing or unknown external values to valid values").

**Fix:** publish `UnDefType.UNDEF`, or drop the `motion` / `contact` channels from `thing-types.xml` until the API supports them. Update the README either way — it currently documents them as real detection states.

### 1.3 HTTP 400 destroys a valid session

`DiagralHttpClient.java:725` treats 400, 401 and 403 alike: clear the API keys and throw `DiagralAuthenticationException`. A 400 is a malformed request (a bad group payload, an unsupported mode), not an expired credential. One rejected command therefore forces a full username/password re-login.

**Fix:** clear keys on 401/403 only; map 400 to `DiagralApiException`.

### 1.4 Command failures never reach the user

`setSystemMode` (`:676`), `activateGroup` (`:709`), `disableGroup` (`:738`), `enableDevice` (`:913`) and `disableDevice` (`:946`) all catch `DiagralException`, log at `error`, and return `void`. The calling handler cannot tell success from failure, so nothing updates the Thing status and nothing corrects the channel. A failed disarm looks exactly like a successful one until the next poll.

**Fix:** let these methods propagate `DiagralException` (or return a result), and have the handlers translate it into `updateStatus(OFFLINE, COMMUNICATION_ERROR, ...)` plus a re-publish of the last known state. `handleCommand()` must still not throw.

### 1.5 Unboxing NPE on nullable anomaly flags

`DiagralSensorHandler.java:262` and `DiagralSystemHandler.java:189-192` use the pattern `map.containsKey(k) && map.get(k)`. `anomalies` is a `Map<String, Boolean>` from Gson, so a JSON `null` value gives `containsKey() == true` and `get() == null` → NPE on unboxing. From `poll()` it is swallowed by `refreshChildHandlers()`; from `initialize()` or `handleCommand()` it escapes.

**Fix:** `Boolean.TRUE.equals(map.get(k))`. In `DiagralSystemHandler` extract a `hasAnomaly(Map, String)` helper — the current four-line nested condition is unreadable.

### 1.6 Unknown sensors are silently classified as motion sensors

`DiagralDiscoveryService.getThingTypeForDevice()` falls through to `THING_TYPE_MOTION_SENSOR` for any unrecognised `refCode`. A new contact sensor model gets discovered as a motion sensor with a permanently-`OFF` motion channel.

**Fix:** skip unknown refCodes with a `debug` log (the loop already handles a `null` return), or introduce a generic `sensor` thing type carrying only the shared `enabled` / `low-battery` channels.

### 1.7 Duplicate device code constant

`DEVICE_DIAG45ACK_CODE = "9012"` (commented "keyboard") and `DEVICE_DIAG50AAX_CODE = "9012"` (commented "outdoor siren") hold the same value. Both are currently unused, so nothing breaks today, but one of them is wrong and will bite whoever extends `getThingTypeForDevice()`.

---

## P2 — Lifecycle, concurrency and performance

### 2.1 Blocking network I/O on framework threads

Every child handler ends `initialize()` with a synchronous `refreshStatus()`, and `bridgeStatusChanged()` does the same. `refreshStatus()` can reach `getSystemConfiguration()` or `getAnomalies()`, each a 10-second-timeout HTTP call. `DiagralSystemHandler` calls the _uncached_ `getAnomalies()`, which `CLAUDE.md` records taking 6+ seconds live.

`DiagralDiscoveryService.initialize()` has the same shape: it calls `registerDiscoveryListener(this)`, which synchronously invokes `listener.startScan(...)`, which fetches the configuration over HTTP.

**Fix:** wrap all of these in `scheduler.execute(...)`. `initialize()` and `dispose()` must return promptly.

### 2.2 Stale handler can resurrect polling after `dispose()`

`attemptInitialAuthentication()` reschedules itself on failure and calls `startPolling()` on success. `dispose()` cancels `authRetryJob`, but a task already executing at that moment can still create a fresh `pollingJob` on a disposed handler, which then polls forever.

**Fix:** a `volatile boolean disposed` set in `dispose()` and checked before scheduling the retry and before `startPolling()`.

### 2.3 Shared mutable state without synchronisation

`cachedSystemStatus`, `cachedSystemStatusTimestamp`, `cachedConfiguration`, `cachedDetails`, `lastKnownMode`, `consecutivePollFailures`, `activeGroupIds`'s companions, `diagralHttpClient`, `authManager` and `discoveryService` are written on scheduler threads and read on command and framework threads. Only `activeGroupIds` (a `ConcurrentHashMap` key set) and `DiagralAuthenticationManager` (`synchronized`) are safe.

**Fix:** mark these `volatile`, or better, fold the three caches into a single immutable snapshot record swapped atomically (see 2.4).

### 2.4 Replace the cache-timestamp hack with an explicit snapshot

`refreshChildHandlers()` (`DiagralBridgeHandler.java:443`) re-stamps `cachedSystemStatusTimestamp` to "now" before each child's `refreshStatus()`, so a slow handler earlier in the loop cannot expire the 5-second TTL for a later one. `CLAUDE.md` is honest that this was chosen to avoid changing a signature.

**Fix:** change `DiagralRefreshableHandler.refreshStatus()` to take the snapshot:

```java
record DiagralPollSnapshot(DiagralSystemStatus status,
                           @Nullable DiagralSystemConfiguration configuration,
                           @Nullable DiagralAnomalies anomalies) {}

void refreshStatus(DiagralPollSnapshot snapshot);
```

The bridge fetches status, configuration and anomalies **once** per poll and passes one immutable snapshot to every child. This removes the TTL entirely, removes the hack, fixes 2.3 for the caches, and fixes 2.6 in one move. It is the single highest-leverage change in this plan.

### 2.5 Configuration and details caches never expire

`getSystemConfiguration()` and `getSystemDetails()` cache indefinitely; only `enableDevice`/`disableDevice` invalidate the configuration. A device added, renamed or inhibited through the e-ONE app stays invisible until openHAB restarts, and the `enabled` channel — which is read from `device.inhibited` in the cached configuration — can be permanently stale.

**Fix:** refresh the configuration every N poll cycles (or on a TTL of a few minutes). Details can stay long-lived but should be re-fetched on re-authentication.

### 2.6 `getAnomalies()` is uncached and on the poll path

Called on every `DiagralSystemHandler.refreshStatus()`, i.e. once per poll plus once per command-triggered re-poll, with no cache. Solved by 2.4.

### 2.7 Every command fires an extra full poll

Five `finally` blocks call `scheduler.execute(this::poll)`. The intent (recovering from a timed-out-but-applied command) is right, but a rule arming several groups in sequence produces a burst of full polls against a rate-limited cloud API.

**Fix:** coalesce into one debounced re-poll — cancel and reschedule a single `ScheduledFuture` ~2 seconds out.

### 2.8 Sensor Thing can get stuck OFFLINE

`DiagralSensorHandler.refreshStatus()` sets `OFFLINE / CONFIGURATION_ERROR` when the device is missing from the configuration, but never restores `ONLINE` when it comes back. Combined with 2.5 this can be permanent.

**Fix:** call `updateStatus(ThingStatus.ONLINE)` on a successful refresh.

### 2.9 `deleteApiKey()` on every dispose costs a full login

`dispose()` fires a best-effort `deleteApiKey()`, which itself performs a fresh username/password `login()`. Every config edit, Thing disable, or openHAB restart therefore triggers an extra login plus a key deletion, and the next start pays for a new key pair.

**Fix:** consider deleting the key only in `handleRemoval()` (Thing genuinely removed) rather than on every `dispose()`.

### 2.10 Background discovery advertised but not implemented

The constructor passes `true` for background discovery, but `startBackgroundDiscovery()` is never overridden, so the toggle in the UI does nothing.

**Fix:** either implement it (re-scan after each successful configuration refresh) or pass `false`.

---

## P3 — Simplification and dead code (~400 lines removable)

### 3.1 Collapse five identical handler subclasses

`DiagralCameraHandler`, `DiagralKeypadHandler`, `DiagralPlugHandler`, `DiagralSirenHandler` and `DiagralTransmitterHandler` differ only in `getProductType()` and `getDeviceList()`, and all have an empty `updateSensorSpecificChannels()`.

**Fix:** one `DiagralDeviceHandler(Thing, @Nullable String productType, Function<DiagralSystemConfiguration, List<DiagralDevice>> deviceList)`, wired from `DiagralHandlerFactory`. Five files become zero.

### 3.2 Extract a shared base thing handler

`getBridgeHandler()`, `bridgeStatusChanged()`, the bridge-null / bridge-offline checks in `initialize()`, and the `RefreshType` branch of `handleCommand()` are copy-pasted verbatim across `DiagralSensorHandler`, `DiagralGroupHandler` and `DiagralSystemHandler`.

**Fix:** a `DiagralBaseThingHandler extends BaseThingHandler implements DiagralRefreshableHandler` holding all of it.

### 3.3 Collapse the discovery loops

`discoverSensors` / `discoverSirens` / `discoverCommands` / `discoverCameras` / `discoverTransmitters` are the same loop five times.

**Fix:** iterate a list of `(devices, thingTypeResolver, label)` triples.

### 3.4 Dead code to delete

- `DiagralCryptoUtil.hmacSha256(String,String,String)` and `hmacSha256(String,String,String,String)` — unused; the two overloads also differ only in arity, which is a trap.
- `DiagralAuthenticationManager.getSecretKey()` — unused, and it needlessly exposes the signing secret.
- `DiagralBridgeHandler.refreshConfiguration()` — unused.
- `DiagralBridgeHandler.diagralBridgeConfig` — only ever assigned (in `getConfigStatus()`), never read.
- `DiagralDiscoveryService.bridgeUID` — assigned in `initialize()`, never read.
- 15 unused constants in `DiagralBindingConstants`: `DEVICE_KEYBOARD_TYPE`, `DEVICE_SIREN_TYPE`, `DEVICE_TRANSMITTER_TYPE`, `DEVICE_DIAG45ACK_CODE`, `DEVICE_TRANSMITTER5_CODE`, `DEVICE_TRANSMITTER9_CODE`, `DEVICE_DIAG50AAX_CODE`, `CONFIG_REFRESH_INTERVAL`, `PROPERTY_DEVICE_ID`, and six `DEVICE_ANOMALY_*` entries.
- Commented-out `CHANNEL_BATTERY_LEVEL` constant and the two commented-out `<channel id="battery-level">` blocks in `thing-types.xml`.
- `DiagralClient` — a two-method interface implemented only by the bridge and used only by the bridge; either drop it or move the discovery-registration contract somewhere it earns its keep.

### 3.5 Model system modes as an enum

`MODE_OFF`/`FULL`/`PRESENCE`/`PARTIAL1`/`PARTIAL2` as loose strings force a `switch` in `DiagralHttpClient.setSystemMode()`, a second one in `DiagralBridgeHandler.groupsForMode()`, a separate `NAMED_SYSTEM_MODES` set, and a runtime `DiagralApiException("Invalid system mode")` for something the type system could reject.

**Fix:**

```java
enum DiagralMode {
    OFF(API_ENDPOINT_STOP), FULL(API_ENDPOINT_START), PRESENCE(API_ENDPOINT_PRESENCE),
    PARTIAL1(API_ENDPOINT_PARTIAL_START_1), PARTIAL2(API_ENDPOINT_PARTIAL_START_2);
    ...
}
```

Mode→endpoint, mode→group-membership and command validation then live in one place.

### 3.6 Build JSON with Gson, not string concatenation

`DiagralHttpClient.buildGroupsPayload()` (`:413`) hand-builds `{"groups":[N]}`. There is already a `Gson` instance and a `dto` package. Add a small request DTO. (`AGENTS.md`: use a parser for structured data.)

### 3.7 Java 21 modernisation

Convert the `switch` chains in `setSystemMode`, `isDeviceInhibited`, `groupsForMode` and `createHandler` to switch expressions; `DiagralPollSnapshot` from 2.4 wants to be a `record`.

### 3.8 Smaller items

- `Gson` in `DiagralHttpClient` can be `static final`.
- `DiagralDiscoveryService.discoverDevice()` calls `id.replaceAll(...)`, recompiling the regex per device; use a precompiled `Pattern`.
- Parse failures throw `DiagralApiException("...", HttpStatus.OK_200)` — using 200 as an error code is misleading. Use a dedicated exception or omit the status.
- `DiagralBridgeConfiguration.isValid()` conflates missing credentials with an out-of-range `refreshInterval`, producing the message "Invalid configuration: Check username, password, serialId, and pinCode" for a bad interval. It also duplicates `getConfigStatus()`, whose `== null` checks are unreachable on `@NonNullByDefault` fields initialised to `""`. Consolidate into one validation path.
- `DiagralAlarm` is the only DTO without `@NonNullByDefault`, and its `getId()` returns `null` without `@Nullable`. It also derives the Thing UID from the first 6 characters of the box serial, which is collision-prone.
- `DiagralGroupHandler.updateChannels(String groupId, ...)` shadows the field of the same name.
- `org.openhab.core.thing.ThingStatusInfo` is written fully qualified in three `bridgeStatusChanged` signatures instead of imported.
- `DiagralBindingConstants` should be `final` with a private constructor.
- `DiagralDiscoveryService.getValueOrDefault()` stores `""` for absent values; omitting the property is better than an empty one.

---

## P4 — Metadata, documentation and logging

### 4.1 No semantic tags or categories

`thing-types.xml` has no `<tags>` on any channel and no `<category>` on any thing type. This is an explicit Review Checklist item and `AGENTS.md` requirement. Add e.g. `Alarm`/`Status` on `armed-status`, `Switch`+`Power` on `enabled`, `LowBattery`+`Energy` on `low-battery`, `Presence` on `motion`, `OpenState` on `contact`.

### 4.2 `addon.xml` missing `<connection>cloud</connection>`

The binding is cloud-only. Compare `somfytahoma` / `netatmo`.

### 4.3 Channel state options do not match what is published

- `armed-status` declares options for the five named modes only, but the handler publishes raw `TEMPO_1`, `TEMPO_2`, `TEMPO_GROUP` and `GROUP` during transitions, so the UI shows untranslated codes.
- `mode-control` has `<command><options>` but no `<state><options>`, yet the handler now writes state to it (`DiagralSystemHandler`), so the same problem applies.
- `group-status` publishes hardcoded English `"Active"` / `"Inactive"` `StringType`s. Not localisable, and it duplicates the `active` switch. Drop it, or give it state options plus i18n keys.

### 4.4 Thing property naming

`PROPERTY_ALARM_DEVICE_TYPE = "Device Type"`, `"Firmware Version"`, `"Is MJPEG Archive Video Supported"` etc. use spaces and title case. openHAB convention is camelCase, and core already defines `Thing.PROPERTY_FIRMWARE_VERSION`, `PROPERTY_MODEL_ID`, `PROPERTY_SERIAL_NUMBER`, `PROPERTY_VENDOR` — use those. `discoverDevice()` also writes a bare `"serial"` literal while every sibling key comes from a constant.

### 4.5 README is out of date

- "Supported Things" lists 5 of the 10 implemented thing types (missing `siren`, `keypad`, `plug`, `transmitter`, `camera`); their channel tables are missing too.
- `central-low-battery` is not documented.
- The `deviceIndex` config parameter is not documented.
- The item example binds `...:battery-level`, a channel that does not exist.
- `mode-control` is documented "Write Only" but now reports state.
- `motion` and `contact` are described as real detection states (see 1.2).

### 4.6 Logging levels

`AGENTS.md` and the guidelines reserve `warn`/`error` for real defects; expected device and network failures belong in the Thing status.

- `logger.error` for expected failures: `authenticate()` (`:312`), the re-auth path (`:387`, with a full stack trace), and all five command methods (`:676`, `:709`, `:738`, `:913`, `:946`).
- `logger.info` on every startup and every re-auth: `"Authenticating with Diagral API..."` (`:307`), `"Bridge online - authentication successful"` (`:310`), `"Authentication successful"` (`DiagralHttpClient:110`), `"Deleted API key: ..."` (`:211`). Demote to `debug`.
- `DiagralCryptoUtil.hmacSha256` both logs at `error` with a stack trace _and_ throws — pick one.

`CLAUDE.md` already flags this area; the list above is the concrete set.

### 4.7 Authentication failures are always reported as configuration errors

`authenticate()` sets `OFFLINE / CONFIGURATION_ERROR` for every `DiagralAuthenticationException`, including a network timeout that `login()` wrapped as `"Login request failed"`. A transient outage then tells the user their credentials are wrong.

**Fix:** distinguish credential rejection (401/403 from the server) from communication failure, and use `COMMUNICATION_ERROR` for the latter.

### 4.8 No tests

`src/test` does not exist. `AGENTS.md` expects behavioural changes to come with tests, and reviewers will ask. Highest value first:

1. `DiagralHttpClient` status-code → exception mapping, and the HTTP-500 enable/disable quirk workaround (mock the Jetty `HttpClient` and `ContentResponse`).
1. `DiagralCryptoUtil.hmacSha256` against a known vector.
1. `DiagralBridgeHandler.groupsForMode()` / `isGroupActive()` across named, `GROUP` and transitional statuses — this is where the subtle bugs have historically been.
1. `getThingTypeForDevice()` refCode mapping, including the unknown-code case from 1.6.
1. `DiagralAnomalies.getTotalCount()` and the inhibited-device parsing, including `null` map values from 1.5.

---

## Suggested sequencing

| Phase | Content | Rough size |
|---|---|---|
| 1 | 1.1 credential logging, 1.3 HTTP 400, 1.5 unboxing NPE | small, ship immediately |
| 2 | 1.2 fabricated states, 1.4 command failure propagation, 1.6 unknown refCodes, 4.7 status detail | medium, user-visible |
| 3 | 2.4 poll snapshot (pulls in 2.3, 2.6), 2.1 non-blocking init, 2.2 dispose guard | medium, structural |
| 4 | 2.5 config TTL, 2.7 debounced re-poll, 2.8 ONLINE restore, 2.9 dispose login, 2.10 background discovery | small each |
| 5 | 3.1–3.4 deduplication and dead-code removal | large diff, zero behaviour change |
| 6 | 3.5–3.8 modernisation and small cleanups | small |
| 7 | 4.1–4.5 metadata, semantic tags, README | small |
| 8 | 4.6 logging levels, 4.8 tests | medium |

Phases 1, 2 and 7 are what an openHAB reviewer will insist on before merge. Phase 3 is where the remaining latent bugs live. Phase 5 is worth doing before the binding grows further, and is easiest to review as its own commit with no functional changes.

Run `mvn spotless:apply` and `mvn clean install` (plus `npx markdownlint-cli2 --config .github/markdownlint.yaml --fix README.md`) at the end of each phase; neither could be executed in this environment.
