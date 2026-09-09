/*
 * Copyright (c) 2010-2026 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.diagral.internal;

import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.thing.ThingTypeUID;

/**
 * The {@link DiagralBindingConstants} class defines common constants, which are
 * used across the whole binding.
 *
 * <p>
 * Centralizes everything that would otherwise be a magic string/UID scattered across the codebase:
 * thing-type UIDs (must match the IDs in {@code thing-types.xml}), channel IDs, Diagral cloud API base
 * URL/endpoint paths/request headers, arm-mode and per-device "product type" strings used by the
 * enable/disable API, config parameter and thing property keys, device type/reference codes used to
 * classify discovered sensors, and config-status message keys. Grouped into commented sections below;
 * add new constants to the matching section rather than creating a new one, and check here first before
 * hardcoding a string literal elsewhere in the binding.
 * </p>
 *
 * @author David Martin - Initial contribution
 */
@NonNullByDefault
public class DiagralBindingConstants {

    /**
     * OSGi/thing-type namespace prefix for every UID this binding defines - must match {@code bindingId} in
     * {@code thing-types.xml}.
     */
    private static final String BINDING_ID = "diagral";

    // List of all Thing Type UIDs
    /** UID of the {@code bridge} thing type - the cloud connection/credentials holder. */
    public static final ThingTypeUID THING_TYPE_BRIDGE = new ThingTypeUID(BINDING_ID, "bridge");
    /** UID of the {@code alarm-system} thing type - overall arm/disarm control and anomaly reporting. */
    public static final ThingTypeUID THING_TYPE_ALARM_SYSTEM = new ThingTypeUID(BINDING_ID, "alarm-system");
    /** UID of the {@code motion-sensor} thing type. */
    public static final ThingTypeUID THING_TYPE_MOTION_SENSOR = new ThingTypeUID(BINDING_ID, "motion-sensor");
    /** UID of the {@code contact-sensor} thing type. */
    public static final ThingTypeUID THING_TYPE_CONTACT_SENSOR = new ThingTypeUID(BINDING_ID, "contact-sensor");
    /** UID of the {@code group} thing type - a Diagral zone that can be armed/disarmed as a unit. */
    public static final ThingTypeUID THING_TYPE_GROUP = new ThingTypeUID(BINDING_ID, "group");
    /** UID of the {@code siren} thing type. */
    public static final ThingTypeUID THING_TYPE_SIREN = new ThingTypeUID(BINDING_ID, "siren");
    /** UID of the {@code keypad} thing type (the API's "commands" device category). */
    public static final ThingTypeUID THING_TYPE_KEYPAD = new ThingTypeUID(BINDING_ID, "keypad");
    /** UID of the {@code plug} thing type - a transmitter with its {@code isPlug} flag set. */
    public static final ThingTypeUID THING_TYPE_PLUG = new ThingTypeUID(BINDING_ID, "plug");
    /** UID of the {@code transmitter} thing type - a generic, non-plug radio transmitter. */
    public static final ThingTypeUID THING_TYPE_TRANSMITTER = new ThingTypeUID(BINDING_ID, "transmitter");
    /** UID of the {@code camera} thing type - inventory/anomaly visibility only. */
    public static final ThingTypeUID THING_TYPE_CAMERA = new ThingTypeUID(BINDING_ID, "camera");

    // List of all Channel IDs - Alarm System
    /**
     * Channel ID: the alarm system's read-only current status (ten possible values - see {@link #ALL_SYSTEM_STATUSES}).
     */
    public static final String CHANNEL_ARMED_STATUS = "armed-status";
    /** Channel ID: writable channel to arm/disarm the whole system via one of the five named modes. */
    public static final String CHANNEL_MODE_CONTROL = "mode-control";
    /** Channel ID: read-only Switch indicating whether any anomaly is currently reported. */
    public static final String CHANNEL_ANOMALIES_PRESENT = "anomalies-present";
    /** Channel ID: read-only count of anomalies currently reported. */
    public static final String CHANNEL_ANOMALY_COUNT = "anomaly-count";
    /** Channel ID: read-only Switch reflecting the central unit's own low-battery/power-supply alert. */
    public static final String CHANNEL_CENTRAL_LOW_BATTERY = "central-low-battery";
    /** Channel ID: batch-activates several groups at once from a comma-separated list of group IDs. */
    public static final String CHANNEL_ACTIVATE_GROUPS = "activate-groups";
    /** Channel ID: batch-disables several groups at once from a comma-separated list of group IDs. */
    public static final String CHANNEL_DISABLE_GROUPS = "disable-groups";

    // List of all Channel IDs - Sensors
    /** Channel ID: a motion sensor's motion-detected state (always {@code UNDEF} - the API has no live value). */
    public static final String CHANNEL_MOTION = "motion";
    /** Channel ID: a contact sensor's open/closed state (always {@code UNDEF} - the API has no live value). */
    public static final String CHANNEL_CONTACT = "contact";
    /** Channel ID: a device's enabled (un-inhibited) state, shared by every device thing type. */
    public static final String CHANNEL_ENABLED = "enabled";
    /** Channel ID: a device's low-battery indicator, shared by every device thing type. */
    public static final String CHANNEL_LOW_BATTERY = "low-battery";

    // List of all Channel IDs - Group
    /** Channel ID: a group's activation state (ON/OFF), on the {@code group} thing type. */
    public static final String CHANNEL_GROUP_ACTIVE = "active";
    /** Channel ID: a group's human-readable status description, on the {@code group} thing type. */
    public static final String CHANNEL_GROUP_STATUS = "status";
    /** Channel ID: a group's own numeric Diagral identifier, echoing its {@code groupId} configuration. */
    public static final String CHANNEL_GROUP_ID = "group-id";

    // Diagral API Constants
    /** Base URL of the Diagral cloud REST API every endpoint constant below is relative to. */
    public static final String API_BASE_URL = "https://appv3.tt-monitor.com/emerald/v1";
    /** Endpoint: username/password login, the first step of authentication - returns a bearer access token. */
    public static final String API_ENDPOINT_LOGIN = "/users/authenticate/login";
    /** Endpoint: exchanges the access token for a signed-request API key/secret pair. */
    public static final String API_ENDPOINT_API_KEY = "/users/api_key";
    /** Endpoint: lists the Diagral systems (boxes) associated with the authenticated account. */
    public static final String API_ENDPOINT_USER_SYSTEMS = "/users/systems";
    /** Endpoint path segment: appended under a system's API-keys collection to delete a superseded key. */
    public static final String API_ENDPOINT_API_KEYS = "/api_keys";
    /** Endpoint: the {@code /systems} collection every per-system endpoint below is nested under. */
    public static final String API_ENDPOINT_SYSTEMS = "/systems";
    /** Endpoint: the full device/group/central configuration for one system. */
    public static final String API_ENDPOINT_CONFIGURATIONS = "/configurations";
    /** Endpoint: the live armed/disarmed status for one system. */
    public static final String API_ENDPOINT_STATUS = "/status";
    /** Endpoint: arms the system fully ({@link #MODE_FULL}). */
    public static final String API_ENDPOINT_START = "/start";
    /** Endpoint: disarms the system ({@link #MODE_OFF}). */
    public static final String API_ENDPOINT_STOP = "/stop";
    /** Endpoint: arms the system in presence mode ({@link #MODE_PRESENCE}). */
    public static final String API_ENDPOINT_PRESENCE = "/presence";
    /** Endpoint: arms the system in partial mode 1 ({@link #MODE_PARTIAL1}). */
    public static final String API_ENDPOINT_PARTIAL_START_1 = "/partial_start_1";
    /** Endpoint: arms the system in partial mode 2 ({@link #MODE_PARTIAL2}). */
    public static final String API_ENDPOINT_PARTIAL_START_2 = "/partial_start_2";
    /** Endpoint: activates one or more groups directly, outside any whole-system mode. */
    public static final String API_ENDPOINT_ACTIVATE_GROUP = "/activate_group";
    /** Endpoint: disables one or more directly-activated groups. */
    public static final String API_ENDPOINT_DISABLE_GROUP = "/disable_group";
    /** Endpoint: the anomalies currently reported for a system (404 when there are none). */
    public static final String API_ENDPOINT_ANOMALIES = "/anomalies";
    /** Endpoint path segment: enables (un-inhibits) a device, appended after its product-type/index path. */
    public static final String API_ENDPOINT_ENABLE = "/enable";
    /** Endpoint path segment: disables (inhibits) a device, appended after its product-type/index path. */
    public static final String API_ENDPOINT_DISABLE = "/disable";

    // Diagral API Request Headers
    /** Header carrying the HMAC-SHA256 signature of a signed request. */
    public static final String HEADER_X_HMAC = "X-HMAC";
    /** Header carrying the timestamp a signed request's HMAC was computed over. */
    public static final String HEADER_X_TIMESTAMP = "X-TIMESTAMP";
    /** Header carrying the API key identifying which signing secret was used. */
    public static final String HEADER_X_APIKEY = "X-APIKEY";
    /** Header carrying the user's PIN code, required by pin-gated endpoints (e.g. arm/disarm). */
    public static final String HEADER_X_PIN_CODE = "X-PIN-CODE";
    /** Standard HTTP header carrying the bearer access token during the login/API-key-exchange steps. */
    public static final String HEADER_AUTHORIZATION = "Authorization";
    /** Standard HTTP header identifying the request body's media type (always JSON for this API). */
    public static final String HEADER_CONTENT_TYPE = "Content-Type";

    // Diagral System Modes
    /** The whole-system {@code status}/{@code mode-control} value meaning fully disarmed. */
    public static final String MODE_OFF = "OFF";
    /** The whole-system {@code status}/{@code mode-control} value meaning fully armed (every group). */
    public static final String MODE_FULL = "FULL";
    /** The whole-system {@code status}/{@code mode-control} value for presence mode. */
    public static final String MODE_PRESENCE = "PRESENCE";
    /** The whole-system {@code status}/{@code mode-control} value for partial mode 1. */
    public static final String MODE_PARTIAL1 = "PARTIAL1";
    /** The whole-system {@code status}/{@code mode-control} value for partial mode 2. */
    public static final String MODE_PARTIAL2 = "PARTIAL2";

    /**
     * The whole-system {@code status} value the real API reports during the exit delay while arming
     * settles into a partial-group-1-based mode.
     *
     * <p>
     * Observed live for both {@link #MODE_PARTIAL1} and {@link #MODE_PRESENCE}. On the installation this
     * was characterised on, {@code presenceGroup} and {@code partialGroup1} are the same group list, so
     * {@code PRESENCE} is effectively "arm partial group 1" and shares its timer - whether an installation
     * with differing lists would still report {@code TEMPO_1} for {@code PRESENCE} has not been observed.
     * Purely transitional: it precedes the settled named mode.
     * </p>
     */
    public static final String MODE_TEMPO_1 = "TEMPO_1";

    /**
     * The whole-system {@code status} value the real API reports during the exit delay while arming
     * settles into {@link #MODE_PARTIAL2}. Purely transitional, like {@link #MODE_TEMPO_1}.
     */
    public static final String MODE_TEMPO_2 = "TEMPO_2";

    /**
     * The whole-system {@code status} value the real API reports while the system is in installation /
     * device-enrollment mode, driven from the central unit or the official e-ONE app.
     *
     * <p>
     * A settled state rather than a transitional one - observed live (2026-02-27) holding for minutes at
     * a time across two separate sessions, with {@code activated_groups} empty throughout. Notably the
     * system is <em>not</em> armed while in it. Included here because it is a value {@code armed-status}
     * can genuinely publish; like every non-named status it needs no special handling in {@code
     * DiagralBridgeHandler.isGroupActive()}/{@code getDisplayedMode()}, both of which fall back correctly.
     * </p>
     */
    public static final String SYSTEM_STATUS_LEARNING_MODE = "LEARNING_MODE";

    /**
     * The whole-system {@code status} value the real API reports while one or more groups are in the
     * process of being armed directly via the {@code activate_group} endpoint (or via {@link #MODE_FULL},
     * which appears to be implemented server-side as "activate every group" - it reports this same value),
     * as opposed to a whole-system mode command ({@link #MODE_OFF}/{@link #MODE_FULL}/{@link
     * #MODE_PRESENCE}/{@link #MODE_PARTIAL1}/{@link #MODE_PARTIAL2}). This is purely transitional: it
     * precedes {@link #SYSTEM_STATUS_GROUP} once arming settles.
     *
     * <p>
     * Confirmed live (2026-09-04, see {@code DiagralBridgeHandler.isGroupActive()}'s Javadoc): {@code
     * activated_groups} stays empty for the whole duration of this transitional status - the API gives no
     * way to tell *which* group(s) are involved while still {@code TEMPO_GROUP}, unlike once it settles to
     * {@link #SYSTEM_STATUS_GROUP}. {@code DiagralBridgeHandler.isGroupActive()} falls back to its own
     * best-effort tracking for this status, since there is currently no way to do better.
     * </p>
     */
    public static final String MODE_TEMPO_GROUP = "TEMPO_GROUP";

    /**
     * The whole-system {@code status} value the real API reports once one or more directly-activated
     * groups have settled (the resting state that follows {@link #MODE_TEMPO_GROUP}).
     *
     * <p>
     * Unlike every other non-named status, {@code activated_groups} is reliably populated here - confirmed
     * live (2026-09-04) across three independent, isolated tests (one per group), each producing the exact
     * expected {@code activated_groups} list (e.g. {@code {"status":"GROUP","activated_groups":[2]}} for
     * group 2). {@code DiagralBridgeHandler.isGroupActive()} trusts this list directly for this status, the
     * same way it trusts config-derived membership for the five named modes in {@link
     * #NAMED_SYSTEM_MODES} - see that method's Javadoc for the full derivation and the live evidence that
     * motivated it.
     * </p>
     */
    public static final String SYSTEM_STATUS_GROUP = "GROUP";

    /**
     * Every {@code status} value this binding has observed the real API report, and therefore every value
     * the read-only {@code armed-status} channel can publish.
     *
     * <p>
     * Exists so {@code thing-types.xml}'s {@code armed-status} option list can be checked against it - an
     * undeclared value reaches the user as a raw, unlabelled string. Kept next to the constants it is
     * built from so adding a newly-observed status is a single edit rather than two that can drift apart.
     * </p>
     */
    public static final Set<String> ALL_SYSTEM_STATUSES = Set.of(MODE_OFF, MODE_FULL, MODE_PRESENCE, MODE_PARTIAL1,
            MODE_PARTIAL2, MODE_TEMPO_1, MODE_TEMPO_2, MODE_TEMPO_GROUP, SYSTEM_STATUS_GROUP,
            SYSTEM_STATUS_LEARNING_MODE);

    /**
     * The five whole-system modes for which the real API's {@code /status} response's {@code
     * activated_groups} list is authoritative via static per-mode configuration. Used by {@code
     * DiagralBridgeHandler.isGroupActive()} to detect when group membership can be derived from the cached
     * {@code DiagralSystemConfiguration} directly, as opposed to {@link #SYSTEM_STATUS_GROUP} (authoritative
     * a different way - see that constant's Javadoc) or {@link #MODE_TEMPO_GROUP}/any other unrecognized
     * status, where a group's active state must instead fall back to the bridge's own best-effort tracking
     * of directly-issued group actions.
     */
    public static final Set<String> NAMED_SYSTEM_MODES = Set.of(MODE_OFF, MODE_FULL, MODE_PRESENCE, MODE_PARTIAL1,
            MODE_PARTIAL2);

    // Diagral Product Types (used for the per-device enable/disable API)
    /** Product-type string for the central unit itself, used in enable/disable API paths. */
    public static final String PRODUCT_TYPE_CENTRAL = "CENTRAL";
    /** Product-type string for a sensor (motion/contact), used in enable/disable API paths. */
    public static final String PRODUCT_TYPE_SENSOR = "SENSOR";
    /** Product-type string for a keypad (the API's "command" category), used in enable/disable API paths. */
    public static final String PRODUCT_TYPE_COMMAND = "COMMAND";
    /** Product-type string for a siren, used in enable/disable API paths. */
    public static final String PRODUCT_TYPE_ALARM = "ALARM";
    /** Product-type string for the box (central controller unit) - not currently acted on by this binding. */
    public static final String PRODUCT_TYPE_BOX = "BOX";
    /** Product-type string for a plug, used in enable/disable API paths. */
    public static final String PRODUCT_TYPE_PLUG = "PLUG";

    // Configuration Properties
    /** Bridge config parameter name: the Diagral account's username (email). */
    public static final String CONFIG_USERNAME = "username";
    /** Bridge config parameter name: the Diagral account's password. */
    public static final String CONFIG_PASSWORD = "password";
    /** Bridge config parameter name: the Diagral box's serial ID. */
    public static final String CONFIG_SERIAL_ID = "serialId";
    /** Bridge config parameter name: the PIN code used for pin-gated commands. */
    public static final String CONFIG_PIN_CODE = "pinCode";
    /** Device config parameter name: the device's unique Diagral ID. */
    public static final String CONFIG_DEVICE_ID = "deviceId";
    /** Device config parameter name: the device's per-category numeric index. */
    public static final String CONFIG_DEVICE_INDEX = "deviceIndex";
    /** Group config parameter name: the group's numeric Diagral ID. */
    public static final String CONFIG_GROUP_ID = "groupId";

    // Thing Properties
    /** Discovery-time device thing property: the API's raw device {@code type} value. */
    public static final String PROPERTY_DEVICE_TYPE = "deviceType";
    /** Discovery-time device thing property: the API's raw device {@code subtype} value. */
    public static final String PROPERTY_DEVICE_SUBTYPE = "deviceSubtype";
    /** Discovery-time group thing property: the group's numeric Diagral ID (mirrors {@link #CONFIG_GROUP_ID}). */
    public static final String PROPERTY_GROUP_ID = "groupId";
    /** Discovery-time group thing property: the group's entry delay in seconds. */
    public static final String PROPERTY_GROUP_INPUT_DELAY = "inputDelay";
    /** Discovery-time group thing property: the group's exit delay in seconds. */
    public static final String PROPERTY_GROUP_OUTPUT_DELAY = "outputDelay";
    /** Discovery-time group thing property: the named modes ({@link #MODE_FULL} etc.) that arm this group. */
    public static final String PROPERTY_GROUP_MODES = "armModes";

    // Vendor constant
    /** Human-readable vendor name, set as {@code Thing.PROPERTY_VENDOR} on discovered things. */
    public static final String VENDOR_DIAGRAL = "Diagral";
    /** Vendor query-string value the API's login endpoint requires (distinct casing from {@link #VENDOR_DIAGRAL}). */
    public static final String VENDOR_PARAM = "DIAGRAL";

    // Alarm Details Properties. camelCase per the openHAB naming guideline for thing properties; the
    // firmware version is not listed here because it uses core's Thing.PROPERTY_FIRMWARE_VERSION.
    /** Discovery-time alarm-system thing property: the system's configured name. */
    public static final String PROPERTY_ALARM_SYSTEM_NAME = "name";
    /** Discovery-time alarm-system thing property: the central unit's device type. */
    public static final String PROPERTY_ALARM_DEVICE_TYPE = "deviceType";
    /** Discovery-time alarm-system thing property: the central unit's IP address. */
    public static final String PROPERTY_ALARM_IP_ADDRESS = "ipAddress";
    /** Discovery-time alarm-system thing property: the installed iPoda protocol version. */
    public static final String PROPERTY_ALARM_IPODA_VERSION = "ipodaVersion";
    /** Discovery-time alarm-system thing property: the central unit's operating mode string. */
    public static final String PROPERTY_ALARM_MODE = "mode";
    /** Discovery-time alarm-system thing property: whether an alarm recording file is present. */
    public static final String PROPERTY_ALARM_IS_ALARM_FILE_PRESENT = "isAlarmFilePresent";
    /** Discovery-time alarm-system thing property: whether MJPEG archive video playback is supported. */
    public static final String PROPERTY_ALARM_IS_MJPEG_ARCHIVE_VIDEO_SUPPORTED = "isMjpegArchiveVideoSupported";
    /** Discovery-time alarm-system thing property: whether the central unit has mass storage present. */
    public static final String PROPERTY_ALARM_IS_MASS_STORAGE_PRESENT = "isMassStoragePresent";
    /** Discovery-time alarm-system thing property: whether remote startup/shutdown is allowed. */
    public static final String PROPERTY_ALARM_IS_REMOTE_STARTUP_SHUTDOWN_ALLOWED = "isRemoteStartupShutdownAllowed";
    /** Discovery-time alarm-system thing property: whether video access is password-protected. */
    public static final String PROPERTY_ALARM_IS_VIDEO_PASSWORD_PROTECTED = "isVideoPasswordProtected";

    // Config status messages
    /** Config-status message key: reported when the bridge's username is missing. */
    public static final String USERNAME_MISSING = "missing-username-configuration";
    /** Config-status message key: reported when the bridge's password is missing. */
    public static final String PASSWORD_MISSING = "missing-password-configuration";
    /** Config-status message key: reported when the bridge's PIN code is missing. */
    public static final String PINCODE_MISSING = "missing-pincode-configuration";
    /** Config-status message key: reported when the bridge's serial ID is missing. */
    public static final String SERIALID_MISSING = "missing-serialid-configuration";

    // Device types
    /** The API's device {@code type} value identifying a sensor (motion or contact). */
    public static final String DEVICE_SENSOR_TYPE = "2";

    // Device codes
    /** Device {@code refCode} for the DIAG30APK miniature opening detector (contact sensor). */
    public static final String DEVICE_DIAG30APK_CODE = "9057"; // Détecteur d'ouverture miniature
    /** Device {@code refCode} for the DIAG20AVK standard volumetric sensor (motion sensor). */
    public static final String DEVICE_DIAG20AVK_CODE = "9000"; // capteur volumetrique standard
    /** Device {@code refCode} for the DIAG21AVK pet-immune volumetric sensor (motion sensor). */
    public static final String DEVICE_DIAG21AVK_CODE = "9001"; // capteur volumetrique compatible animaux
    /** Device {@code refCode} for the DIAG36APX outdoor pet-immune volumetric sensor (motion sensor). */
    public static final String DEVICE_DIAG36APX_CODE = "9013"; // capteur volumetrique exterieur compatibles animaux

    // Device anomalies types
    /** Anomaly map key: a generic power-supply alert on a device (see {@code DiagralDevice#anomalies}). */
    public static final String DEVICE_ANOMALY_POWER_SUPPLY_ALERT = "powerSupplyAlert";
    /** Anomaly map key: the central unit's main power-supply alert (see {@code DiagralCentral#anomalies}). */
    public static final String DEVICE_ANOMALY_MAIN_POWERSUPPLY_ALERT = "mainPowerSupplyAlert";
    /** Anomaly map key: the central unit's secondary (backup) power-supply alert. */
    public static final String DEVICE_ANOMALY_SECOND_POWERSUPPLY_ALERT = "secondaryPowerSupplyAlert";

    // Anomaly name reported by the /anomalies endpoint's anomaly_names list (distinct shape from the
    // configuration endpoint's per-device anomalies map above)
    /** Anomaly name reported by the {@code /anomalies} endpoint meaning the device is inhibited/disabled. */
    public static final String DEVICE_ANOMALY_NAME_INHIBITED = "inhibited";
}
