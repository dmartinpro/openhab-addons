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
     */
    public DiagralDiscoveryService() {
        super(DiagralBridgeHandler.class,
                Set.of(THING_TYPE_ALARM_SYSTEM, THING_TYPE_MOTION_SENSOR, THING_TYPE_CONTACT_SENSOR, THING_TYPE_GROUP),
                DISCOVERY_TIMEOUT_SECONDS, true);
    }

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

        // Discover groups
        discoverGroups(bridgeHandler, config.groups);

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

    static <T> T getValueOrDefault(T value, T defaultValue) {
        return value == null ? defaultValue : value;
    }

    /**
     * Discovers sensor devices.
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
            if (device.id == null || device.type == null) {
                continue;
            }

            // Determine thing type based on device subtype
            ThingTypeUID thingTypeUID = getThingTypeForDevice(device);
            if (thingTypeUID == null) {
                logger.debug("Skipping device with unknown type: {} ({})", device.id, device.type);
                continue;
            }

            // Create thing UID
            String id = device.id; // Already checked for null above
            String deviceId = id.replaceAll("[^a-zA-Z0-9_]", "_");
            ThingUID thingUID = new ThingUID(thingTypeUID, bridgeUID, deviceId);

            // Build properties
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

            // Build label
            String label = device.name != null ? device.name + " (Sensor)" : "Diagral " + device.type;

            thingDiscovered(DiscoveryResultBuilder.create(thingUID).withBridge(bridgeUID).withLabel(label)
                    .withProperties(properties).withRepresentationProperty(CONFIG_DEVICE_ID).build());

            logger.debug("Discovered sensor: {} - {}", thingUID, label);
        }
    }

    /**
     * Discovers device groups.
     *
     * @param bridgeHandler the bridge handler
     * @param groups the list of groups from the configuration
     */
    private void discoverGroups(DiagralBridgeHandler bridgeHandler, @Nullable List<DiagralGroup> groups) {
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

            String label = group.name != null ? group.name + " (Group)" : "Diagral Group " + group.index;

            thingDiscovered(DiscoveryResultBuilder.create(thingUID).withBridge(bridgeUID).withLabel(label)
                    .withProperties(properties).withRepresentationProperty(CONFIG_GROUP_ID).build());

            logger.debug("Discovered group: {} - {}", thingUID, label);
        }
    }

    /**
     * Determines the appropriate thing type for a device based on its type and subtype.
     *
     * @param device the device
     * @return the thing type UID, or null if unknown
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

        // Could add more device types here (cameras, sirens, etc.) when implemented
        return null;
    }

    @Override
    public void initialize() {
        bridgeUID = thingHandler.getThing().getUID();
        thingHandler.registerDiscoveryListener(this);
        super.initialize();
    }

    @Override
    public void dispose() {
        super.dispose();
        removeOlderResults(getTimestampOfLastScan());
    }
}
