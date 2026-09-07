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

    private static final String BINDING_ID = "diagral";

    // List of all Thing Type UIDs
    public static final ThingTypeUID THING_TYPE_BRIDGE = new ThingTypeUID(BINDING_ID, "bridge");
    public static final ThingTypeUID THING_TYPE_ALARM_SYSTEM = new ThingTypeUID(BINDING_ID, "alarm-system");
    public static final ThingTypeUID THING_TYPE_MOTION_SENSOR = new ThingTypeUID(BINDING_ID, "motion-sensor");
    public static final ThingTypeUID THING_TYPE_CONTACT_SENSOR = new ThingTypeUID(BINDING_ID, "contact-sensor");
    public static final ThingTypeUID THING_TYPE_GROUP = new ThingTypeUID(BINDING_ID, "group");
    public static final ThingTypeUID THING_TYPE_SIREN = new ThingTypeUID(BINDING_ID, "siren");
    public static final ThingTypeUID THING_TYPE_KEYPAD = new ThingTypeUID(BINDING_ID, "keypad");
    public static final ThingTypeUID THING_TYPE_PLUG = new ThingTypeUID(BINDING_ID, "plug");
    public static final ThingTypeUID THING_TYPE_TRANSMITTER = new ThingTypeUID(BINDING_ID, "transmitter");
    public static final ThingTypeUID THING_TYPE_CAMERA = new ThingTypeUID(BINDING_ID, "camera");

    // List of all Channel IDs - Alarm System
    public static final String CHANNEL_ARMED_STATUS = "armed-status";
    public static final String CHANNEL_MODE_CONTROL = "mode-control";
    public static final String CHANNEL_ANOMALIES_PRESENT = "anomalies-present";
    public static final String CHANNEL_ANOMALY_COUNT = "anomaly-count";
    public static final String CHANNEL_CENTRAL_LOW_BATTERY = "central-low-battery";

    // List of all Channel IDs - Sensors
    public static final String CHANNEL_MOTION = "motion";
    public static final String CHANNEL_CONTACT = "contact";
    public static final String CHANNEL_ENABLED = "enabled";
    public static final String CHANNEL_LOW_BATTERY = "low-battery";

    // List of all Channel IDs - Group
    public static final String CHANNEL_GROUP_ACTIVE = "active";
    public static final String CHANNEL_GROUP_STATUS = "status";

    // Diagral API Constants
    public static final String API_BASE_URL = "https://appv3.tt-monitor.com/emerald/v1";
    public static final String API_ENDPOINT_LOGIN = "/users/authenticate/login";
    public static final String API_ENDPOINT_API_KEY = "/users/api_key";
    public static final String API_ENDPOINT_USER_SYSTEMS = "/users/systems";
    public static final String API_ENDPOINT_API_KEYS = "/api_keys";
    public static final String API_ENDPOINT_SYSTEMS = "/systems";
    public static final String API_ENDPOINT_CONFIGURATIONS = "/configurations";
    public static final String API_ENDPOINT_STATUS = "/status";
    public static final String API_ENDPOINT_START = "/start";
    public static final String API_ENDPOINT_STOP = "/stop";
    public static final String API_ENDPOINT_PRESENCE = "/presence";
    public static final String API_ENDPOINT_PARTIAL_START_1 = "/partial_start_1";
    public static final String API_ENDPOINT_PARTIAL_START_2 = "/partial_start_2";
    public static final String API_ENDPOINT_ACTIVATE_GROUP = "/activate_group";
    public static final String API_ENDPOINT_DISABLE_GROUP = "/disable_group";
    public static final String API_ENDPOINT_ANOMALIES = "/anomalies";
    public static final String API_ENDPOINT_ENABLE = "/enable";
    public static final String API_ENDPOINT_DISABLE = "/disable";

    // Diagral API Request Headers
    public static final String HEADER_X_HMAC = "X-HMAC";
    public static final String HEADER_X_TIMESTAMP = "X-TIMESTAMP";
    public static final String HEADER_X_APIKEY = "X-APIKEY";
    public static final String HEADER_X_PIN_CODE = "X-PIN-CODE";
    public static final String HEADER_AUTHORIZATION = "Authorization";
    public static final String HEADER_CONTENT_TYPE = "Content-Type";

    // Diagral System Modes
    public static final String MODE_OFF = "OFF";
    public static final String MODE_FULL = "FULL";
    public static final String MODE_PRESENCE = "PRESENCE";
    public static final String MODE_PARTIAL1 = "PARTIAL1";
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
    public static final String PRODUCT_TYPE_CENTRAL = "CENTRAL";
    public static final String PRODUCT_TYPE_SENSOR = "SENSOR";
    public static final String PRODUCT_TYPE_COMMAND = "COMMAND";
    public static final String PRODUCT_TYPE_ALARM = "ALARM";
    public static final String PRODUCT_TYPE_BOX = "BOX";
    public static final String PRODUCT_TYPE_PLUG = "PLUG";

    // Configuration Properties
    public static final String CONFIG_USERNAME = "username";
    public static final String CONFIG_PASSWORD = "password";
    public static final String CONFIG_SERIAL_ID = "serialId";
    public static final String CONFIG_PIN_CODE = "pinCode";
    public static final String CONFIG_DEVICE_ID = "deviceId";
    public static final String CONFIG_DEVICE_INDEX = "deviceIndex";
    public static final String CONFIG_GROUP_ID = "groupId";

    // Thing Properties
    public static final String PROPERTY_DEVICE_TYPE = "deviceType";
    public static final String PROPERTY_DEVICE_SUBTYPE = "deviceSubtype";
    public static final String PROPERTY_GROUP_ID = "groupId";
    public static final String PROPERTY_GROUP_INPUT_DELAY = "inputDelay";
    public static final String PROPERTY_GROUP_OUTPUT_DELAY = "outputDelay";
    public static final String PROPERTY_GROUP_MODES = "armModes";

    // Vendor constant
    public static final String VENDOR_DIAGRAL = "Diagral";
    public static final String VENDOR_PARAM = "DIAGRAL";

    // Alarm Details Properties. camelCase per the openHAB naming guideline for thing properties; the
    // firmware version is not listed here because it uses core's Thing.PROPERTY_FIRMWARE_VERSION.
    public static final String PROPERTY_ALARM_SYSTEM_NAME = "name";
    public static final String PROPERTY_ALARM_DEVICE_TYPE = "deviceType";
    public static final String PROPERTY_ALARM_IP_ADDRESS = "ipAddress";
    public static final String PROPERTY_ALARM_IPODA_VERSION = "ipodaVersion";
    public static final String PROPERTY_ALARM_MODE = "mode";
    public static final String PROPERTY_ALARM_IS_ALARM_FILE_PRESENT = "isAlarmFilePresent";
    public static final String PROPERTY_ALARM_IS_MJPEG_ARCHIVE_VIDEO_SUPPORTED = "isMjpegArchiveVideoSupported";
    public static final String PROPERTY_ALARM_IS_MASS_STORAGE_PRESENT = "isMassStoragePresent";
    public static final String PROPERTY_ALARM_IS_REMOTE_STARTUP_SHUTDOWN_ALLOWED = "isRemoteStartupShutdownAllowed";
    public static final String PROPERTY_ALARM_IS_VIDEO_PASSWORD_PROTECTED = "isVideoPasswordProtected";

    // Config status messages
    public static final String USERNAME_MISSING = "missing-username-configuration";
    public static final String PASSWORD_MISSING = "missing-password-configuration";
    public static final String PINCODE_MISSING = "missing-pincode-configuration";
    public static final String SERIALID_MISSING = "missing-serialid-configuration";

    // Device types
    public static final String DEVICE_SENSOR_TYPE = "2";

    // Device codes
    public static final String DEVICE_DIAG30APK_CODE = "9057"; // Détecteur d'ouverture miniature
    public static final String DEVICE_DIAG20AVK_CODE = "9000"; // capteur volumetrique standard
    public static final String DEVICE_DIAG21AVK_CODE = "9001"; // capteur volumetrique compatible animaux
    public static final String DEVICE_DIAG36APX_CODE = "9013"; // capteur volumetrique exterieur compatibles animaux

    // Device anomalies types
    public static final String DEVICE_ANOMALY_POWER_SUPPLY_ALERT = "powerSupplyAlert";
    public static final String DEVICE_ANOMALY_MAIN_POWERSUPPLY_ALERT = "mainPowerSupplyAlert";
    public static final String DEVICE_ANOMALY_SECOND_POWERSUPPLY_ALERT = "secondaryPowerSupplyAlert";

    // Anomaly name reported by the /anomalies endpoint's anomaly_names list (distinct shape from the
    // configuration endpoint's per-device anomalies map above)
    public static final String DEVICE_ANOMALY_NAME_INHIBITED = "inhibited";
}
