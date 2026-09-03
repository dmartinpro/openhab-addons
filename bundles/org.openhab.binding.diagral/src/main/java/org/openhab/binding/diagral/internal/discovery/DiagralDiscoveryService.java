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
package org.openhab.binding.diagral.internal.discovery;

import static org.openhab.binding.diagral.internal.DiagralBindingConstants.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.diagral.internal.bridge.DiagralBridgeHandler;
import org.openhab.binding.diagral.internal.dto.DiagralAlarm;
import org.openhab.binding.diagral.internal.dto.DiagralDevice;
import org.openhab.binding.diagral.internal.dto.DiagralGroup;
import org.openhab.binding.diagral.internal.dto.DiagralSystemConfiguration;
import org.openhab.binding.diagral.internal.dto.DiagralSystemDetails;
import org.openhab.core.config.discovery.AbstractThingHandlerDiscoveryService;
import org.openhab.core.config.discovery.DiscoveryResultBuilder;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.ThingUID;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ServiceScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link DiagralDiscoveryService} discovers Diagral devices and groups.
 *
 * <p>
 * This service automatically discovers:
 * <ul>
 * <li>Alarm system control</li>
 * <li>Motion sensors</li>
 * <li>Contact sensors</li>
 * <li>Sirens</li>
 * <li>Keypads</li>
 * <li>Plugs and generic transmitters</li>
 * <li>Cameras</li>
 * <li>Device groups</li>
 * </ul>
 * </p>
 *
 * <p>
 * Discovery is triggered when the bridge comes online and can be manually started
 * from the openHAB inbox.
 * </p>
 *
 * @author David Martin - Initial contribution
 */
@Component(scope = ServiceScope.PROTOTYPE, service = DiagralDiscoveryService.class)
@NonNullByDefault
public class DiagralDiscoveryService extends AbstractThingHandlerDiscoveryService<DiagralBridgeHandler> {

    private final Logger logger = LoggerFactory.getLogger(DiagralDiscoveryService.class);

    private static final int DISCOVERY_TIMEOUT_SECONDS = 30;

    private @Nullable ThingUID bridgeUID;

    /**
     * Creates a new DiagralDiscoveryService.
     *
     * <p>
     * Declares every non-bridge thing type this binding supports as discoverable, a 30-second scan
     * timeout, and {@code true} for the "background discovery enabled by default" flag - though note
     * this service doesn't actually implement background discovery ({@link #startScan()} only runs when
     * explicitly triggered from the inbox or by a bridge coming online); there's no periodic
     * re-scan.
     * </p>
     */
    public DiagralDiscoveryService() {
        super(DiagralBridgeHandler.class,
                Set.of(THING_TYPE_ALARM_SYSTEM, THING_TYPE_MOTION_SENSOR, THING_TYPE_CONTACT_SENSOR, THING_TYPE_GROUP,
                        THING_TYPE_SIREN, THING_TYPE_KEYPAD, THING_TYPE_PLUG, THING_TYPE_TRANSMITTER,
                        THING_TYPE_CAMERA),
                DISCOVERY_TIMEOUT_SECONDS, true);
    }

    /**
     * Runs one discovery scan: fetches the bridge's current system configuration and reports a
     * discovery result for the alarm system, every device category, and every group found in it.
     *
     * <p>
     * Requires the bridge to already be online with a fetched configuration - if either isn't available
     * yet, this logs a warning and returns without discovering anything (there's no retry; the user has
     * to re-trigger the scan once the bridge comes online).
     * </p>
     */
    @Override
    protected void startScan() {
        logger.debug("Starting Diagral device discovery");

        DiagralBridgeHandler bridgeHandler = thingHandler;
        if (bridgeHandler == null) {
            logger.warn("Bridge handler not available for discovery");
            return;
        }

        // Get system configuration from bridge
        DiagralSystemConfiguration config = bridgeHandler.getSystemConfiguration();
        if (config == null) {
            logger.warn("System configuration not available for discovery");
            return;
        }

        // Discover alarm system
        discoverAlarmSystem(bridgeHandler, config.alarm);

        // Discover devices
        discoverSensors(bridgeHandler, config.sensors);
        discoverSirens(bridgeHandler, config.sirens);
        discoverCommands(bridgeHandler, config.commands);
        discoverTransmitters(bridgeHandler, config.transmitters);
        discoverCameras(bridgeHandler, config.cameras);

        // Discover groups
        discoverGroups(bridgeHandler, config);

        logger.debug("Diagral device discovery completed");
    }

    /**
     * Discovers the alarm system thing.
     *
     * @param bridgeHandler the bridge handler
     */
    private void discoverAlarmSystem(DiagralBridgeHandler bridgeHandler, @Nullable DiagralAlarm alarmSystem) {
        if (alarmSystem == null) {
            return;
        }

        ThingUID bridgeUID = bridgeHandler.getThing().getUID();
        ThingUID thingUID = new ThingUID(THING_TYPE_ALARM_SYSTEM, bridgeUID, alarmSystem.getId());

        Map<String, Object> properties = new HashMap<>();
        properties.put(PROPERTY_VENDOR, VENDOR_DIAGRAL);

        if (alarmSystem.name != null) {
            properties.put(PROPERTY_ALARM_SYSTEM_NAME, alarmSystem.name);
        }
        if (alarmSystem.box != null && alarmSystem.box.serial != null) {
            properties.put(CONFIG_SERIAL_ID, alarmSystem.box.serial);
        }

        DiagralSystemDetails alarmDetails = bridgeHandler.getSystemDetails();
        if (alarmDetails != null) {
            properties.put(PROPERTY_ALARM_DEVICE_TYPE, getValueOrDefault(alarmDetails.deviceType, ""));
            properties.put(PROPERTY_ALARM_FIRMWARE_VERSION, getValueOrDefault(alarmDetails.firmwareVersion, ""));
            properties.put(PROPERTY_ALARM_IP_ADDRESS, getValueOrDefault(alarmDetails.ipAddress, ""));
            properties.put(PROPERTY_ALARM_IPODA_VERSION, getValueOrDefault(alarmDetails.ipodaVersion, ""));
            properties.put(PROPERTY_ALARM_MODE, getValueOrDefault(alarmDetails.mode, ""));
            properties.put(PROPERTY_ALARM_IS_ALARM_FILE_PRESENT,
                    getValueOrDefault(alarmDetails.isAlarmFilePresent, ""));
            properties.put(PROPERTY_ALARM_IS_MJPEG_ARCHIVE_VIDEO_SUPPORTED,
                    getValueOrDefault(alarmDetails.isMJPEGArchiveVideoSupported, ""));
            properties.put(PROPERTY_ALARM_IS_MASS_STORAGE_PRESENT,
                    getValueOrDefault(alarmDetails.isMassStoragePresent, ""));
            properties.put(PROPERTY_ALARM_IS_REMOTE_STARTUP_SHUTDOWN_ALLOWED,
                    getValueOrDefault(alarmDetails.isRemoteStartupShutdownAllowed, ""));
            properties.put(PROPERTY_ALARM_IS_VIDEO_PASSWORD_PROTECTED,
                    getValueOrDefault(alarmDetails.isVideoPasswordProtected, ""));
        }

        // Build label
        String label = alarmSystem.name != null ? alarmSystem.name + " (Diagral Alarm System)" : "Diagral Alarm System";

        thingDiscovered(DiscoveryResultBuilder.create(thingUID).withBridge(bridgeUID).withLabel(label)
                .withProperties(properties).withRepresentationProperty(CONFIG_SERIAL_ID).build());

        logger.debug("Discovered alarm system: {}", thingUID);
    }

    /**
     * Null-safely substitutes a default value.
     *
     * <p>
     * Used by {@link #discoverAlarmSystem} to avoid putting {@code null} into the discovery result's
     * properties map (which doesn't accept null values) for any of {@link DiagralSystemDetails}'s many
     * optional fields.
     * </p>
     *
     * @param <T> the value type
     * @param value the value, possibly null
     * @param defaultValue the value to use if {@code value} is null
     * @return {@code value} if non-null, otherwise {@code defaultValue}
     */
    static <T> T getValueOrDefault(T value, T defaultValue) {
        return value == null ? defaultValue : value;
    }

    /**
     * Discovers sensor devices (motion/contact - the actual thing type is resolved per-device).
     *
     * @param bridgeHandler the bridge handler
     * @param sensors the list of sensors from the configuration
     */
    private void discoverSensors(DiagralBridgeHandler bridgeHandler, @Nullable List<DiagralDevice> sensors) {
        if (sensors == null) {
            return;
        }

        ThingUID bridgeUID = bridgeHandler.getThing().getUID();

        for (DiagralDevice device : sensors) {
            if (device.getUniqueId() == null || device.type == null) {
                continue;
            }

            ThingTypeUID thingTypeUID = getThingTypeForDevice(device);
            if (thingTypeUID == null) {
                logger.debug("Skipping device with unknown type: {} ({})", device.getUniqueId(), device.type);
                continue;
            }

            discoverDevice(bridgeUID, thingTypeUID, device, "Sensor");
        }
    }

    /**
     * Discovers sirens.
     *
     * @param bridgeHandler the bridge handler
     * @param sirens the list of sirens from the configuration
     */
    private void discoverSirens(DiagralBridgeHandler bridgeHandler, @Nullable List<DiagralDevice> sirens) {
        if (sirens == null) {
            return;
        }

        ThingUID bridgeUID = bridgeHandler.getThing().getUID();
        for (DiagralDevice device : sirens) {
            discoverDevice(bridgeUID, THING_TYPE_SIREN, device, "Siren");
        }
    }

    /**
     * Discovers keypads (the API's "commands" device category).
     *
     * @param bridgeHandler the bridge handler
     * @param commands the list of keypads from the configuration
     */
    private void discoverCommands(DiagralBridgeHandler bridgeHandler, @Nullable List<DiagralDevice> commands) {
        if (commands == null) {
            return;
        }

        ThingUID bridgeUID = bridgeHandler.getThing().getUID();
        for (DiagralDevice device : commands) {
            discoverDevice(bridgeUID, THING_TYPE_KEYPAD, device, "Keypad");
        }
    }

    /**
     * Discovers cameras.
     *
     * @param bridgeHandler the bridge handler
     * @param cameras the list of cameras from the configuration
     */
    private void discoverCameras(DiagralBridgeHandler bridgeHandler, @Nullable List<DiagralDevice> cameras) {
        if (cameras == null) {
            return;
        }

        ThingUID bridgeUID = bridgeHandler.getThing().getUID();
        for (DiagralDevice device : cameras) {
            discoverDevice(bridgeUID, THING_TYPE_CAMERA, device, "Camera");
        }
    }

    /**
     * Discovers transmitters, branching per-device on {@code isPlug} to distinguish smart plugs (which
     * support enable/disable) from generic transmitters (which don't - see
     * {@link org.openhab.binding.diagral.internal.handler.DiagralTransmitterHandler}).
     *
     * @param bridgeHandler the bridge handler
     * @param transmitters the list of transmitters from the configuration
     */
    private void discoverTransmitters(DiagralBridgeHandler bridgeHandler, @Nullable List<DiagralDevice> transmitters) {
        if (transmitters == null) {
            return;
        }

        ThingUID bridgeUID = bridgeHandler.getThing().getUID();
        for (DiagralDevice device : transmitters) {
            if (Boolean.TRUE.equals(device.isPlug)) {
                discoverDevice(bridgeUID, THING_TYPE_PLUG, device, "Plug");
            } else {
                discoverDevice(bridgeUID, THING_TYPE_TRANSMITTER, device, "Transmitter");
            }
        }
    }

    /**
     * Builds and reports a discovery result for a single device.
     *
     * @param bridgeUID the bridge's thing UID
     * @param thingTypeUID the thing type to create
     * @param device the device
     * @param labelSuffix a short label suffix, e.g. "Siren"
     */
    private void discoverDevice(ThingUID bridgeUID, ThingTypeUID thingTypeUID, DiagralDevice device,
            String labelSuffix) {
        // Sirens, keypads, and transmitters have no "uid" in the API - only sensors do - so fall back to
        // the serial number to identify them (see DiagralDevice#getUniqueId).
        String id = device.getUniqueId();
        if (id == null) {
            return;
        }

        String deviceId = id.replaceAll("[^a-zA-Z0-9_]", "_");
        ThingUID thingUID = new ThingUID(thingTypeUID, bridgeUID, deviceId);

        Map<String, Object> properties = new HashMap<>();
        properties.put(CONFIG_DEVICE_ID, id);
        properties.put(PROPERTY_VENDOR, VENDOR_DIAGRAL);

        Integer deviceIndex = device.deviceIndex;
        if (deviceIndex != null) {
            properties.put(CONFIG_DEVICE_INDEX, deviceIndex);
        }

        String type = device.type;
        if (type != null) {
            properties.put(PROPERTY_DEVICE_TYPE, type);
        }
        String subtype = device.subtype;
        if (subtype != null) {
            properties.put(PROPERTY_DEVICE_SUBTYPE, subtype);
        }
        String serial = device.serial;
        if (serial != null) {
            properties.put("serial", serial);
        }

        String label = device.name != null && !device.name.isEmpty() ? device.name + " (" + labelSuffix + ")"
                : "Diagral " + labelSuffix;

        thingDiscovered(DiscoveryResultBuilder.create(thingUID).withBridge(bridgeUID).withLabel(label)
                .withProperties(properties).withRepresentationProperty(CONFIG_DEVICE_ID).build());

        logger.debug("Discovered {}: {} - {}", labelSuffix, thingUID, label);
    }

    /**
     * Discovers device groups.
     *
     * @param bridgeHandler the bridge handler
     * @param config the system configuration
     */
    private void discoverGroups(DiagralBridgeHandler bridgeHandler, DiagralSystemConfiguration config) {
        List<DiagralGroup> groups = config.groups;
        if (groups == null) {
            return;
        }

        ThingUID bridgeUID = bridgeHandler.getThing().getUID();

        for (DiagralGroup group : groups) {
            String groupId = String.valueOf(group.index);
            ThingUID thingUID = new ThingUID(THING_TYPE_GROUP, bridgeUID, "group_" + group.index);

            Map<String, Object> properties = new HashMap<>();
            properties.put(CONFIG_GROUP_ID, groupId);
            properties.put(PROPERTY_VENDOR, VENDOR_DIAGRAL);
            properties.put(PROPERTY_GROUP_ID, groupId);
            properties.put(PROPERTY_GROUP_INPUT_DELAY, group.inputDelay);
            properties.put(PROPERTY_GROUP_OUTPUT_DELAY, group.outputDelay);
            properties.put(PROPERTY_GROUP_MODES, getArmModesForGroup(group.index, config));

            String label = group.name != null ? group.name + " (Group)" : "Diagral Group " + group.index;

            thingDiscovered(DiscoveryResultBuilder.create(thingUID).withBridge(bridgeUID).withLabel(label)
                    .withProperties(properties).withRepresentationProperty(CONFIG_GROUP_ID).build());

            logger.debug("Discovered group: {} - {}", thingUID, label);
        }
    }

    /**
     * Builds a human-readable list of the arm modes a group belongs to, based on the presence/partial
     * group membership lists from the system configuration.
     *
     * <p>
     * The main FULL arm mode has no separate membership list in the API - it implicitly includes every
     * group - so it's intentionally not added here.
     * </p>
     *
     * @param groupIndex the group's index
     * @param config the system configuration
     * @return a comma-separated list of arm mode names, or an empty string if the group is only part of
     *         FULL arm
     */
    private static String getArmModesForGroup(int groupIndex, DiagralSystemConfiguration config) {
        List<String> modes = new ArrayList<>();
        if (containsGroup(config.presenceGroup, groupIndex)) {
            modes.add(MODE_PRESENCE);
        }
        if (containsGroup(config.partialGroup1, groupIndex)) {
            modes.add(MODE_PARTIAL1);
        }
        if (containsGroup(config.partialGroup2, groupIndex)) {
            modes.add(MODE_PARTIAL2);
        }
        return String.join(", ", modes);
    }

    /**
     * Null-safely checks whether a group index is present in a group-membership list.
     *
     * @param groupIndices the membership list (e.g. {@code config.presenceGroup}), possibly null
     * @param groupIndex the group index to look for
     * @return {@code true} if the list is non-null and contains {@code groupIndex}
     */
    private static boolean containsGroup(@Nullable List<Integer> groupIndices, int groupIndex) {
        return groupIndices != null && groupIndices.contains(groupIndex);
    }

    /**
     * Determines the appropriate thing type for a device found in the API's {@code sensors} list, based
     * on its type code and reference code.
     *
     * <p>
     * Only used by {@link #discoverSensors} - sirens/keypads/transmitters/cameras each come from their
     * own separate API list and always map to one fixed thing type (see {@link #discoverSirens} etc.),
     * so they don't need this per-device classification.
     * </p>
     *
     * @param device the device
     * @return the thing type UID, or null if the device's type/refCode combination isn't recognized
     */
    private @Nullable ThingTypeUID getThingTypeForDevice(DiagralDevice device) {
        String type = device.type;
        String refCode = device.refCode;

        if (type == null) {
            return null;
        }

        // Determine based on type and refCode (as subtype seems to always be 0)
        if (DEVICE_SENSOR_TYPE.equals(type)) {
            if (refCode != null) {
                if (refCode.equalsIgnoreCase(DEVICE_DIAG20AVK_CODE) || refCode.equalsIgnoreCase(DEVICE_DIAG21AVK_CODE)
                        || refCode.equalsIgnoreCase(DEVICE_DIAG36APX_CODE)) {
                    return THING_TYPE_MOTION_SENSOR;
                } else if (refCode.equalsIgnoreCase(DEVICE_DIAG30APK_CODE)) {
                    return THING_TYPE_CONTACT_SENSOR;
                }
            }
            // Default to motion sensor if refCode is unknown
            return THING_TYPE_MOTION_SENSOR;
        }

        // Add new refCode-based branches here if the "sensors" list ever contains a type code this
        // binding doesn't yet recognize
        return null;
    }

    /**
     * Records the bridge's thing UID and registers this service as the bridge's discovery listener.
     *
     * @see DiagralBridgeHandler#registerDiscoveryListener(DiagralDiscoveryService)
     */
    @Override
    public void initialize() {
        bridgeUID = thingHandler.getThing().getUID();
        thingHandler.registerDiscoveryListener(this);
        super.initialize();
    }

    /**
     * Cleans up on service shutdown: delegates to the superclass, then removes any inbox entries left
     * over from before the last scan (devices that were discovered previously but are no longer present
     * in the Diagral configuration).
     */
    @Override
    public void dispose() {
        super.dispose();
        removeOlderResults(getTimestampOfLastScan());
    }
}
