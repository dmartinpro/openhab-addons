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
import java.util.function.Function;

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
import org.openhab.core.thing.Thing;
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

        // Discover devices. Each category differs only in which thing type its devices map to, so they
        // all go through one loop with a classifier: fixed for the categories that map 1:1, per-device for
        // sensors (resolved from type/refCode) and transmitters (split on isPlug).
        ThingUID bridgeUID = bridgeHandler.getThing().getUID();
        discoverCategory(bridgeUID, config.sensors, DiagralDiscoveryService::classifySensor);
        discoverCategory(bridgeUID, config.sirens, fixedKind(THING_TYPE_SIREN, "Siren"));
        discoverCategory(bridgeUID, config.commands, fixedKind(THING_TYPE_KEYPAD, "Keypad"));
        discoverCategory(bridgeUID, config.transmitters, DiagralDiscoveryService::classifyTransmitter);
        discoverCategory(bridgeUID, config.cameras, fixedKind(THING_TYPE_CAMERA, "Camera"));

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

        // The id derives from the box serial, which the API may omit or return too short (see
        // DiagralAlarm#getId). Passing null - or a serial carrying a character ThingUID rejects - would
        // throw here and abort the whole scan, taking every other device down with it. Skip this one
        // result instead.
        String alarmId = alarmSystem.getId();
        if (alarmId == null) {
            logger.debug("Skipping alarm system discovery - no usable id could be derived from the box serial");
            return;
        }

        ThingUID bridgeUID = bridgeHandler.getThing().getUID();
        ThingUID thingUID = new ThingUID(THING_TYPE_ALARM_SYSTEM, bridgeUID, toUidSegment(alarmId));

        Map<String, Object> properties = new HashMap<>();
        properties.put(Thing.PROPERTY_VENDOR, VENDOR_DIAGRAL);

        String alarmName = alarmSystem.name;
        if (alarmName != null) {
            properties.put(PROPERTY_ALARM_SYSTEM_NAME, alarmName);
        }

        DiagralAlarm.Device box = alarmSystem.box;
        String boxSerial = box == null ? null : box.serial;
        if (boxSerial != null) {
            properties.put(CONFIG_SERIAL_ID, boxSerial);
            properties.put(Thing.PROPERTY_SERIAL_NUMBER, boxSerial);
        }

        DiagralSystemDetails alarmDetails = bridgeHandler.getSystemDetails();
        if (alarmDetails != null) {
            properties.put(PROPERTY_ALARM_DEVICE_TYPE, getValueOrDefault(alarmDetails.deviceType, ""));
            properties.put(Thing.PROPERTY_FIRMWARE_VERSION, getValueOrDefault(alarmDetails.firmwareVersion, ""));
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
        String label = alarmName != null ? alarmName + " (Diagral Alarm System)" : "Diagral Alarm System";

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
     * What a discovered device should become: which thing type, and the suffix its label gets.
     *
     * @param thingTypeUID the thing type to create for this device
     * @param labelSuffix a short human-readable category name, e.g. {@code "Siren"}
     */
    private record DeviceKind(ThingTypeUID thingTypeUID, String labelSuffix) {
    }

    /**
     * Builds a classifier for a category whose devices all map to the same thing type.
     *
     * @param thingTypeUID the thing type every device in the category becomes
     * @param labelSuffix the label suffix for the category
     * @return a classifier that ignores the device and always returns the same kind
     */
    private static Function<DiagralDevice, @Nullable DeviceKind> fixedKind(ThingTypeUID thingTypeUID,
            String labelSuffix) {
        DeviceKind kind = new DeviceKind(thingTypeUID, labelSuffix);
        return device -> kind;
    }

    /**
     * Classifies a device from the API's {@code sensors} list, whose thing type depends on the individual
     * device rather than the category (see {@link #getThingTypeForDevice}).
     *
     * @param device the device to classify
     * @return the kind to create, or {@code null} if the device's type/refCode isn't recognized
     */
    private static @Nullable DeviceKind classifySensor(DiagralDevice device) {
        ThingTypeUID thingTypeUID = getThingTypeForDevice(device);
        return thingTypeUID == null ? null : new DeviceKind(thingTypeUID, "Sensor");
    }

    /**
     * Classifies a device from the API's {@code transmitters} list, which mixes smart plugs (which support
     * enable/disable) with generic transmitters (which don't), distinguished only by {@code isPlug}.
     *
     * @param device the device to classify
     * @return the kind to create
     */
    private static DeviceKind classifyTransmitter(DiagralDevice device) {
        return Boolean.TRUE.equals(device.isPlug) ? new DeviceKind(THING_TYPE_PLUG, "Plug")
                : new DeviceKind(THING_TYPE_TRANSMITTER, "Transmitter");
    }

    /**
     * Reports a discovery result for every device in one category of the system configuration.
     *
     * <p>
     * Replaces the five near-identical per-category methods this class used to carry, which differed only
     * in the thing type and label they passed through to {@link #discoverDevice}.
     * </p>
     *
     * @param bridgeUID the bridge's thing UID
     * @param devices the category's device list, or {@code null} if the API omitted it
     * @param classifier decides what each device should become, or returns {@code null} to skip it
     */
    private void discoverCategory(ThingUID bridgeUID, @Nullable List<DiagralDevice> devices,
            Function<DiagralDevice, @Nullable DeviceKind> classifier) {
        if (devices == null) {
            return;
        }

        for (DiagralDevice device : devices) {
            DeviceKind kind = classifier.apply(device);
            if (kind == null) {
                logger.debug("Skipping device with unknown type: {} ({})", device.getUniqueId(), device.type);
                continue;
            }
            discoverDevice(bridgeUID, kind.thingTypeUID(), device, kind.labelSuffix());
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

        ThingUID thingUID = new ThingUID(thingTypeUID, bridgeUID, toUidSegment(id));

        Map<String, Object> properties = new HashMap<>();
        properties.put(CONFIG_DEVICE_ID, id);
        properties.put(Thing.PROPERTY_VENDOR, VENDOR_DIAGRAL);

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
            properties.put(Thing.PROPERTY_SERIAL_NUMBER, serial);
        }

        String label = device.name != null && !device.name.isEmpty() ? device.name + " (" + labelSuffix + ")"
                : "Diagral " + labelSuffix;

        thingDiscovered(DiscoveryResultBuilder.create(thingUID).withBridge(bridgeUID).withLabel(label)
                .withProperties(properties).withRepresentationProperty(CONFIG_DEVICE_ID).build());

        logger.debug("Discovered {}: {} - {}", labelSuffix, thingUID, label);
    }

    /**
     * Reduces an API-supplied identifier to characters {@link ThingUID} accepts.
     *
     * <p>
     * {@code ThingUID} rejects a segment containing anything outside letters, digits and underscores,
     * throwing {@code IllegalArgumentException} from its constructor - which, during a scan, would abort
     * the whole thing. Every id that reaches a {@code ThingUID} goes through here.
     * </p>
     *
     * @param id the raw identifier from the API
     * @return the same identifier with every unacceptable character replaced by an underscore
     */
    private static String toUidSegment(String id) {
        return id.replaceAll("[^a-zA-Z0-9_]", "_");
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
            properties.put(Thing.PROPERTY_VENDOR, VENDOR_DIAGRAL);
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
     * <p>
     * Package-private rather than private so {@code DiagralDeviceClassificationTest} can exercise it
     * directly: this decides which handler - and therefore which channels - a discovered device gets.
     * </p>
     *
     * @param device the device
     * @return the thing type UID, or null if the device's type/refCode combination isn't recognized
     */
    static @Nullable ThingTypeUID getThingTypeForDevice(DiagralDevice device) {
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
     * Registers this service as the bridge's discovery listener.
     *
     * @see DiagralBridgeHandler#registerDiscoveryListener(DiagralDiscoveryService)
     */
    @Override
    public void initialize() {
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
