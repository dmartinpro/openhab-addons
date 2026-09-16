# Navimow Binding

This is the binding for **Segway Navimow robotic lawn mowers**, integrating via Segway's cloud REST API
(`https://navimow-fra.ninebot.com`) and, optionally, its MQTT push channel. There is no local/LAN protocol
support - everything goes through the cloud, the same way the official Navimow app and Home Assistant
integrations do.

Authentication uses a browser-based OAuth2 flow, reusing the `client_id`/`client_secret` pair shared by
every known third-party Navimow integration (reverse-engineered from two independent open-source Home
Assistant integrations, not officially published by Segway).

## Supported Things

| Thing        | Type   | Description                                                                          |
|--------------|--------|---------------------------------------------------------------------------------------|
| `account`    | Bridge | A Segway Navimow cloud account. Owns authentication and the REST polling/MQTT loop.    |
| `mower`      | Thing  | One robotic lawn mower linked to the account. Auto-discovered once the bridge is online. |

## Discovery

Once the `account` bridge is online, every mower linked to the account is discovered automatically and
added to the inbox. Discovery is driven entirely by the cloud API's device list - there is no local
network scanning.

## Bridge Configuration

The `account` bridge has no username/password or client id/secret configuration parameters:
authentication is done through a browser-based OAuth2 flow. Once the bridge Thing is created, check its
status - it will report `OFFLINE`/`CONFIGURATION_ERROR` with a link to open in a browser to sign in to
your Navimow account. Once signed in, the bridge goes `ONLINE` automatically.

| Name             | Type    | Description                                                                                                    | Default | Required | Advanced |
|------------------|---------|------------------------------------------------------------------------------------------------------------------|---------|----------|----------|
| `pollingInterval`| integer | How often, in seconds, to poll the cloud API for mower status.                                                  | 60      | no       | yes      |
| `enableMqtt`     | boolean | Opens an additional MQTT connection to the cloud so `activity`/`battery-level` updates arrive within milliseconds of a real state change, instead of waiting for the next REST poll. REST polling keeps running unaffected either way and remains the bridge's only source of truth for online/offline status. | false   | no       | yes      |

## Thing Configuration

### `mower` Thing Configuration

| Name | Type | Description                                                                             | Default | Required | Advanced |
|------|------|------------------------------------------------------------------------------------------|---------|----------|----------|
| `id` | text | The device id as reported by the cloud API. Normally filled in automatically by discovery. | N/A     | yes      | no       |

## Channels

| Channel         | Type              | Read/Write | Description                                                                                   |
|-----------------|-------------------|------------|-------------------------------------------------------------------------------------------------|
| `activity`      | String            | R          | The mower's current canonical activity: `idle`, `mowing`, `paused`, `docked`, `charging`, `returning`, `error`, `unknown`. |
| `control`       | String            | W          | Sends a command to the mower: `START`, `STOP`, `PAUSE`, `RESUME`, `DOCK`.                       |
| `battery-level` | Number (`system.battery-level`) | R | Battery level as a percentage (0-100%).                                                    |
| `model`         | String            | R          | The mower's model, e.g. `X430`, as reported by the cloud API. Mirrors the `modelId` Thing property (below) as a bindable channel, for UIs that want to key off the model without a separate Thing lookup. |

### Thing Properties

In addition to the channels above, the `mower` Thing reports the following properties, refreshed every
poll cycle:

| Property           | Description                                                                 |
|--------------------|-------------------------------------------------------------------------------|
| `modelId`          | The mower's model, e.g. `X430`. Also available as the `model` channel above. |
| `firmwareVersion`  | The mower's firmware version string.                                        |
| `batteryTier`      | A human-readable battery tier reported by the cloud API, e.g. `HIGH`.       |

## Full Example

### Thing Configuration

```java
Bridge navimow:account:myaccount [ pollingInterval=60, enableMqtt=false ] {
    Thing mower mymower [ id="22AAD2602Y0911" ]
}
```

### Item Configuration

```java
String    Navimow_Activity  "Activity"       { channel="navimow:mower:myaccount:mymower:activity" }
String    Navimow_Control   "Control"        { channel="navimow:mower:myaccount:mymower:control" }
Number    Navimow_Battery   "Battery [%d %%]" { channel="navimow:mower:myaccount:mymower:battery-level" }
String    Navimow_Model     "Model"          { channel="navimow:mower:myaccount:mymower:model" }
```

## Known Limitations

- **Cloud-only**: requires internet connectivity; no local/LAN fallback.
- **Polling-based updates by default** (60s interval). The optional `enableMqtt` push connection lowers
  the latency of `activity`/`battery-level` updates to milliseconds, but does not add any data REST
  polling doesn't already have - notably, **mower position is not available at all**: the MQTT push
  channel was investigated specifically for this and does not carry it (see below).
- `STOP` and `PAUSE` are indistinguishable from the reported state alone - both settle to the same
  `isPaused` state, despite using different underlying command verbs.
- The MQTT connection can only be opened from inside the account bridge's own process - the cloud API
  binds an access token to whichever process first used it, and rejects the same token used anywhere
  else (even a faithful re-implementation of the official client's request shape). This is a Segway
  backend behavior, not a limitation of this binding, but it does mean the MQTT connection cannot be
  independently diagnosed with an external tool - only through the bridge's own logs.

## Feedback

This binding is **not yet submitted to the official openHAB add-ons repository**. It has been built,
live-tested, and iterated against a real production Navimow X430 throughout development (every command,
the full REST status surface, and the MQTT push channel), but it hasn't had independent third-party
review or broad real-world testing across other Navimow models yet - issues and feedback are welcome.
