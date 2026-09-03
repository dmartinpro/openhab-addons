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

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.thing.ThingTypeUID;

/**
 * The {@link DiagralBindingConstants} class defines common constants, which are
 * used across the whole binding.
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
    // public static final String CHANNEL_BATTERY_LEVEL = "battery-level";
    public static final String CHANNEL_LOW_BATTERY = "low-battery";

    // List of all Channel IDs - Group
    public static final String CHANNEL_GROUP_ACTIVE = "active";
    public static final String CHANNEL_GROUP_STATUS = "status";

    // Diagral API Constants
    public static final String API_BASE_URL = "https://appv3.tt-monitor.com/emerald/v1";
    public static final String API_ENDPOINT_LOGIN = "/users/authenticate/login";
    public static final String API_ENDPOINT_API_KEY = "/users/api_key";
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
    public static final String CONFIG_REFRESH_INTERVAL = "refreshInterval";
    public static final String CONFIG_DEVICE_ID = "deviceId";
    public static final String CONFIG_DEVICE_INDEX = "deviceIndex";
    public static final String CONFIG_GROUP_ID = "groupId";

    // Thing Properties
    public static final String PROPERTY_DEVICE_TYPE = "deviceType";
    public static final String PROPERTY_DEVICE_SUBTYPE = "deviceSubtype";
    public static final String PROPERTY_DEVICE_ID = "deviceId";
    public static final String PROPERTY_GROUP_ID = "groupId";
    public static final String PROPERTY_VENDOR = "vendor";

    // Vendor constant
    public static final String VENDOR_DIAGRAL = "Diagral";
    public static final String VENDOR_PARAM = "DIAGRAL";

    // Alarm Details Properties
    public static final String PROPERTY_ALARM_SYSTEM_NAME = "Name";
    public static final String PROPERTY_ALARM_DEVICE_TYPE = "Device Type";
    public static final String PROPERTY_ALARM_FIRMWARE_VERSION = "Firmware Version";
    public static final String PROPERTY_ALARM_IP_ADDRESS = "IP Address";
    public static final String PROPERTY_ALARM_IPODA_VERSION = "Ipoda Version";
    public static final String PROPERTY_ALARM_MODE = "Mode";
    public static final String PROPERTY_ALARM_IS_ALARM_FILE_PRESENT = "Is Alarm File Present";
    public static final String PROPERTY_ALARM_IS_MJPEG_ARCHIVE_VIDEO_SUPPORTED = "Is MJPEG Archive Video Supported";
    public static final String PROPERTY_ALARM_IS_MASS_STORAGE_PRESENT = "Is Mass Storage Present";
    public static final String PROPERTY_ALARM_IS_REMOTE_STARTUP_SHUTDOWN_ALLOWED = "Is Remote Startup Shutdown Allowed";
    public static final String PROPERTY_ALARM_IS_VIDEO_PASSWORD_PROTECTED = "Is Video Password Protected";

    // Config status messages
    public static final String USERNAME_MISSING = "missing-username-configuration";
    public static final String PASSWORD_MISSING = "missing-password-configuration";
    public static final String PINCODE_MISSING = "missing-pincode-configuration";
    public static final String SERIALID_MISSING = "missing-serialid-configuration";

    // Device types
    public static final String DEVICE_SENSOR_TYPE = "2";
    public static final String DEVICE_KEYBOARD_TYPE = "3";
    public static final String DEVICE_SIREN_TYPE = "4";
    public static final String DEVICE_TRANSMITTER_TYPE = "5";

    // Device codes
    public static final String DEVICE_DIAG45ACK_CODE = "9012"; // keyboard
    public static final String DEVICE_TRANSMITTER5_CODE = "9037"; // unknown
    public static final String DEVICE_TRANSMITTER9_CODE = "9031"; // unknown
    public static final String DEVICE_DIAG30APK_CODE = "9057"; // Détecteur d'ouverture miniature
    public static final String DEVICE_DIAG50AAX_CODE = "9012"; // outdoor siren
    public static final String DEVICE_DIAG20AVK_CODE = "9000"; // capteur volumetrique standard
    public static final String DEVICE_DIAG21AVK_CODE = "9001"; // capteur volumetrique compatible animaux
    public static final String DEVICE_DIAG36APX_CODE = "9013"; // capteur volumetrique exterieur compatibles animaux

    // Device anomalies types
    public static final String DEVICE_ANOMALY_RADIO_ALERT = "radioAlert";
    public static final String DEVICE_ANOMALY_POWER_SUPPLY_ALERT = "powerSupplyAlert";
    public static final String DEVICE_ANOMALY_AUTOPROTECTION_MECHANICAL_ALERT = "autoprotectionMechanicalAlert";
    public static final String DEVICE_ANOMALY_LOOP_ALERT = "loopAlert";
    public static final String DEVICE_ANOMALY_MASK_ALERT = "maskAlert";
    public static final String DEVICE_ANOMALY_SENSOR_ALERT = "sensorAlert";
    public static final String DEVICE_ANOMALY_MEDIA_GSM_ALERT = "mediaGSMAlert";
    public static final String DEVICE_ANOMALY_MAIN_POWERSUPPLY_ALERT = "mainPowerSupplyAlert";
    public static final String DEVICE_ANOMALY_SECOND_POWERSUPPLY_ALERT = "secondaryPowerSupplyAlert";

    // Anomaly name reported by the /anomalies endpoint's anomaly_names list (distinct shape from the
    // configuration endpoint's per-device anomalies map above)
    public static final String DEVICE_ANOMALY_NAME_INHIBITED = "inhibited";
}
