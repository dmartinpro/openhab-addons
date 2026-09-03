# Diagral Binding

This binding integrates Diagral alarm systems with openHAB through the Diagral cloud API.
It allows you to monitor and control your Diagral alarm system, including arming/disarming, monitoring sensors, and managing device groups.
The binding communicates with the Diagral cloud service using HMAC-SHA256 authenticated requests.

## Supported Things

This binding supports the following thing types:

- `bridge`: The Diagral Bridge - Represents the connection to the Diagral cloud API and handles authentication
- `alarm-system`: The main alarm system control - Provides status monitoring and mode control (arm/disarm)
- `motion-sensor`: Motion detection sensors - Reports motion detection status, enabled state, and battery level
- `contact-sensor`: Contact/door sensors - Reports contact state (open/closed), enabled state, and battery level
- `group`: Device groups - Allows controlling multiple devices together as a group

## Discovery

The binding supports automatic discovery of Diagral devices.

Once you configure and initialize the Diagral Bridge with valid credentials, the binding will automatically discover:
- The alarm system
- All motion sensors
- All contact sensors
- All device groups

Discovered devices will appear in the inbox and can be added with a single click.
The discovery process runs when the bridge comes online and can be manually triggered through the UI.

## Binding Configuration

This binding does not require any binding-level configuration.
All configuration is done at the thing level.

## Thing Configuration

### `bridge` Bridge Configuration

The bridge requires your Diagral account credentials and system information.

| Name            | Type    | Description                                      | Default | Required | Advanced |
|-----------------|---------|--------------------------------------------------|---------|----------|----------|
| username        | text    | Email address for your Diagral account           | N/A     | yes      | no       |
| password        | text    | Password for your Diagral account                | N/A     | yes      | no       |
| serialId        | text    | Serial ID of your Diagral box (DIAG56AAX format) | N/A     | yes      | no       |
| pinCode         | text    | PIN code for system control                      | N/A     | yes      | no       |
| refreshInterval | integer | Interval to poll for status updates (seconds)    | 60      | no       | yes      |

The refresh interval must be between 10 and 300 seconds.

### `alarm-system` Thing Configuration

The alarm system thing is automatically discovered and does not require manual configuration.
It uses the bridge's serial ID for identification.

### `motion-sensor` Thing Configuration

Motion sensors are automatically discovered.
If you need to manually configure one:

| Name     | Type | Description                                   | Default | Required | Advanced |
|----------|------|-----------------------------------------------|---------|----------|----------|
| deviceId | text | Unique ID of the device from Diagral system   | N/A     | yes      | no       |

### `contact-sensor` Thing Configuration

Contact sensors are automatically discovered.
If you need to manually configure one:

| Name     | Type | Description                                   | Default | Required | Advanced |
|----------|------|-----------------------------------------------|---------|----------|----------|
| deviceId | text | Unique ID of the device from Diagral system   | N/A     | yes      | no       |

### `group` Thing Configuration

Device groups are automatically discovered.
If you need to manually configure one:

| Name    | Type | Description                  | Default | Required | Advanced |
|---------|------|------------------------------|---------|----------|----------|
| groupId | text | Unique ID of the device group| N/A     | yes      | no       |

## Channels

### Alarm System Channels

| Channel           | Type   | Read/Write | Description                                                    |
|-------------------|--------|------------|----------------------------------------------------------------|
| armed-status      | String | Read Only  | Current armed status (OFF, FULL, PRESENCE, PARTIAL1, PARTIAL2) |
| mode-control      | String | Write Only | Control the alarm mode (OFF, FULL, PRESENCE, PARTIAL1, PARTIAL2)|
| anomalies-present | Switch | Read Only  | Indicates if any anomalies are present in the system           |
| anomaly-count     | Number | Read Only  | Number of active anomalies in the system                       |

### Motion Sensor Channels

| Channel       | Type   | Read/Write | Description                                      |
|---------------|--------|------------|--------------------------------------------------|
| motion        | Switch | Read Only  | Motion detection state (ON=motion, OFF=no motion)|
| enabled       | Switch | Read/Write | Device enabled state (ON=enabled, OFF=disabled); sending a command enables/disables (un-inhibits/inhibits) the device |
| low-battery   | Switch | Read Only  | Low battery indicator (ON=low, OFF=normal)       |

### Contact Sensor Channels

| Channel       | Type    | Read/Write | Description                                      |
|---------------|---------|------------|--------------------------------------------------|
| contact       | Contact | Read Only  | Contact state (OPEN/CLOSED)                      |
| enabled       | Switch  | Read/Write | Device enabled state (ON=enabled, OFF=disabled); sending a command enables/disables (un-inhibits/inhibits) the device |
| low-battery   | Switch  | Read Only  | Low battery indicator (ON=low, OFF=normal)       |

### Device Group Channels

| Channel | Type   | Read/Write | Description                          |
|---------|--------|------------|--------------------------------------|
| active  | Switch | Read/Write | Group activation state (ON/OFF)      |
| status  | String | Read Only  | Group status description             |

## Full Example

### Thing Configuration

```java
// Bridge configuration - replace with your actual credentials
Bridge diagral:bridge:home "Diagral Alarm Bridge" [
    username="your.email@example.com",
    password="your_password",
    serialId="DIAG56AAX123456",
    pinCode="1234",
    refreshInterval=60
] {
    // Alarm system - automatically discovered or manually configured
    Thing alarm-system alarm "Home Alarm System"

    // Motion sensors - automatically discovered or manually configured
    Thing motion-sensor living_room "Living Room Motion" [deviceId="sensor001"]
    Thing motion-sensor hallway "Hallway Motion" [deviceId="sensor002"]

    // Contact sensors - automatically discovered or manually configured
    Thing contact-sensor front_door "Front Door" [deviceId="sensor010"]
    Thing contact-sensor garage_door "Garage Door" [deviceId="sensor011"]

    // Device groups - automatically discovered or manually configured
    Thing group ground_floor "Ground Floor Group" [groupId="1"]
}
```

### Item Configuration

```java
// Alarm System Items
String   Alarm_Status        "Alarm Status [%s]"           {channel="diagral:alarm-system:home:alarm:armed-status"}
String   Alarm_Control       "Alarm Control"               {channel="diagral:alarm-system:home:alarm:mode-control"}
Switch   Alarm_Anomalies     "Anomalies Present"           {channel="diagral:alarm-system:home:alarm:anomalies-present"}
Number   Alarm_AnomalyCount  "Anomaly Count [%d]"          {channel="diagral:alarm-system:home:alarm:anomaly-count"}

// Living Room Motion Sensor Items
Switch   LivingRoom_Motion   "Motion [%s]"                 {channel="diagral:motion-sensor:home:living_room:motion"}
Switch   LivingRoom_Enabled  "Enabled [%s]"                {channel="diagral:motion-sensor:home:living_room:enabled"}
Number   LivingRoom_Battery  "Battery [%d %%]"             {channel="diagral:motion-sensor:home:living_room:battery-level"}
Switch   LivingRoom_LowBatt  "Low Battery [%s]"            {channel="diagral:motion-sensor:home:living_room:low-battery"}

// Hallway Motion Sensor Items
Switch   Hallway_Motion      "Motion [%s]"                 {channel="diagral:motion-sensor:home:hallway:motion"}
Switch   Hallway_Enabled     "Enabled [%s]"                {channel="diagral:motion-sensor:home:hallway:enabled"}
Number   Hallway_Battery     "Battery [%d %%]"             {channel="diagral:motion-sensor:home:hallway:battery-level"}
Switch   Hallway_LowBatt     "Low Battery [%s]"            {channel="diagral:motion-sensor:home:hallway:low-battery"}

// Front Door Contact Sensor Items
Contact  FrontDoor_Contact   "Front Door [%s]"             {channel="diagral:contact-sensor:home:front_door:contact"}
Switch   FrontDoor_Enabled   "Enabled [%s]"                {channel="diagral:contact-sensor:home:front_door:enabled"}
Switch   FrontDoor_LowBatt   "Low Battery [%s]"            {channel="diagral:contact-sensor:home:front_door:low-battery"}

// Garage Door Contact Sensor Items
Contact  GarageDoor_Contact  "Garage Door [%s]"            {channel="diagral:contact-sensor:home:garage_door:contact"}
Switch   GarageDoor_Enabled  "Enabled [%s]"                {channel="diagral:contact-sensor:home:garage_door:enabled"}
Switch   GarageDoor_LowBatt  "Low Battery [%s]"            {channel="diagral:contact-sensor:home:garage_door:low-battery"}

// Device Group Items
Switch   GroundFloor_Active  "Ground Floor Active [%s]"    {channel="diagral:group:home:ground_floor:active"}
String   GroundFloor_Status  "Ground Floor Status [%s]"    {channel="diagral:group:home:ground_floor:status"}
```

### Sitemap Configuration

```perl
sitemap diagral label="Diagral Alarm System" {
    Frame label="Alarm Control" {
        Text item=Alarm_Status label="Status [%s]"
        Selection item=Alarm_Control label="Control" mappings=[OFF="Disarm", FULL="Arm Full", PRESENCE="Presence", PARTIAL1="Partial 1", PARTIAL2="Partial 2"]
        Switch item=Alarm_Anomalies label="Anomalies"
        Text item=Alarm_AnomalyCount label="Anomaly Count [%d]"
    }

    Frame label="Motion Sensors" {
        Text item=LivingRoom_Motion label="Living Room Motion [%s]" icon="motion"
        Text item=Hallway_Motion label="Hallway Motion [%s]" icon="motion"
    }

    Frame label="Contact Sensors" {
        Text item=FrontDoor_Contact label="Front Door [%s]" icon="door"
        Text item=GarageDoor_Contact label="Garage Door [%s]" icon="garagedoor"
    }

    Frame label="Groups" {
        Switch item=GroundFloor_Active label="Ground Floor Active"
        Text item=GroundFloor_Status label="Ground Floor Status [%s]"
    }
}
```

## Known Limitations & Bugs

### Limitations

- **Cloud-only communication**: This binding uses the Diagral cloud API and requires internet connectivity. Local network communication is not supported.
- **Polling-based updates**: Status updates are retrieved by polling the API at the configured refresh interval (default 60 seconds). Real-time push notifications are not available.
- **API key storage**: API keys are stored in memory only and are regenerated on each openHAB restart.
- **Authentication requirements**: You must have a valid Diagral account with cloud access enabled for your system.
- **Device support**: Initial implementation focuses on alarm system control, motion sensors, contact sensors, and device groups. Other device types (cameras with video streaming, sirens, switches) may be added in future versions.
- **Rate limiting**: The Diagral API may impose rate limits. If you experience issues, try increasing the refresh interval.

### Known Bugs (Diagral Cloud API)

- **Enable/disable action reports a false failure**: The Diagral cloud API's per-device enable/disable endpoint (used by the `enabled` channel on sensors, and internally identical for other device types) has been observed to consistently respond with an **HTTP 500 error and an empty response body even when it successfully applied the action**. This appears to be a bug in the Diagral cloud service itself, not something under this binding's control — it was verified by checking the device's real "inhibited" state via the `/anomalies` endpoint immediately before and after a "failed" call, which confirmed the action had actually taken effect both times.

  The binding works around this: when this specific endpoint returns an HTTP 500, it verifies the device's actual resulting state via the anomalies endpoint before deciding whether to report a failure. If the verified state matches what was requested, the command is treated as successful (you'll see a `WARN` log entry noting the known API quirk, but the channel will update correctly).

  If the state doesn't match, or verification itself isn't possible, the original error is still reported — this also happens if you check too soon: the `/anomalies` endpoint itself appears to be a periodically-refreshed snapshot on Diagral's side rather than a live query, and has been observed to lag the real device state by tens of seconds. In that case the binding conservatively reports a failure even though the command may have actually succeeded, rather than risk falsely reporting success. Either way, the device's real state is always re-synced on the next poll (whether or not the command was reported as successful), so the channel will self-correct shortly regardless.

  You may occasionally see log lines like:
  ```
  WARN [internal.bridge.DiagralHttpClient] - Product 1 (SENSOR) action /disable returned HTTP 500 but the resulting device state was verified as applied - treating as a known Diagral API quirk, not a failure
  ```
  This is expected and does not indicate a problem with your setup.

## Troubleshooting

### Bridge stays OFFLINE with "Invalid credentials"

- Verify your username (email) and password are correct
- Ensure your serial ID is in the correct format (DIAG56AAX followed by numbers)
- Check that your PIN code is correct
- Verify your account has cloud access enabled

### Bridge shows OFFLINE with "Communication error"

- Check your internet connection
- Verify the Diagral cloud service is accessible
- Try increasing the refresh interval to avoid rate limiting

### Devices not discovered

- Ensure the bridge is ONLINE before triggering discovery
- Verify devices are properly configured in your Diagral system
- Check the openHAB logs for any error messages
- Try manually triggering discovery from the inbox

### Alarm commands not working

- Verify your PIN code is correct
- Check that your account has permission to control the alarm system
- Look for error messages in the openHAB logs
- Ensure the bridge is ONLINE

For additional help, check the openHAB community forum or the binding's GitHub repository.
