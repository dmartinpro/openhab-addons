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
package org.openhab.binding.diagral.internal.handler;

import static org.openhab.binding.diagral.internal.DiagralBindingConstants.*;

import java.util.List;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.diagral.internal.DiagralConfiguration;
import org.openhab.binding.diagral.internal.bridge.DiagralBridgeHandler;
import org.openhab.binding.diagral.internal.dto.DiagralDevice;
import org.openhab.binding.diagral.internal.dto.DiagralSystemConfiguration;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link DiagralSensorHandler} is an abstract base class for sensor handlers.
 *
 * <p>
 * This class provides common functionality for all sensor types including:
 * <ul>
 * <li>Configuration management</li>
 * <li>Bridge communication</li>
 * <li>Common channels (enabled, battery-level, low-battery)</li>
 * <li>Status updates</li>
 * </ul>
 * </p>
 *
 * <p>
 * Subclasses should implement {@link #updateSensorSpecificChannels(DiagralDevice)} to handle
 * sensor-type-specific channels (e.g., motion, contact).
 * </p>
 *
 * @author David Martin - Initial contribution
 */
@NonNullByDefault
public abstract class DiagralSensorHandler extends BaseThingHandler implements DiagralRefreshableHandler {

    private final Logger logger = LoggerFactory.getLogger(DiagralSensorHandler.class);
    private @Nullable String deviceId;
    private int deviceIndex = -1;

    /**
     * Constructs a new DiagralSensorHandler.
     *
     * @param thing the thing to handle
     */
    public DiagralSensorHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void initialize() {
        logger.debug("Initializing Diagral sensor handler");

        DiagralConfiguration config = getConfigAs(DiagralConfiguration.class);

        if (!config.isValidDevice()) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "Invalid device configuration");
            return;
        }

        this.deviceId = config.deviceId;
        this.deviceIndex = config.deviceIndex;

        // Check if bridge is available
        Bridge bridge = getBridge();
        if (bridge == null) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "No bridge configured");
            return;
        }

        // Check if bridge is online
        if (bridge.getStatus() != ThingStatus.ONLINE) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE);
            return;
        }

        updateStatus(ThingStatus.ONLINE);
        logger.debug("Diagral sensor handler initialized for device: {}", deviceId);

        // Initial status refresh
        refreshStatus();
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (command instanceof RefreshType) {
            refreshStatus();
            return;
        }

        if (CHANNEL_ENABLED.equals(channelUID.getId()) && command instanceof OnOffType onOffCommand) {
            setDeviceEnabled(onOffCommand == OnOffType.ON);
            return;
        }

        // Subclasses can override to handle specific commands
    }

    /**
     * Enables or disables (un-inhibits/inhibits) this device via the bridge.
     *
     * @param enabled true to enable, false to disable
     */
    private void setDeviceEnabled(boolean enabled) {
        String productType = getProductType();
        if (productType == null) {
            // The framework won't normally deliver a command for a read-only channel, but guard anyway.
            logger.warn("Enable/disable is not supported by the Diagral API for this device type");
            return;
        }

        DiagralBridgeHandler bridgeHandler = getBridgeHandler();
        if (bridgeHandler == null) {
            logger.warn("Cannot set device enabled state - bridge handler not available");
            return;
        }

        if (deviceIndex < 0) {
            logger.warn("Cannot set device enabled state - device index not available");
            return;
        }

        logger.debug("Setting device {} to {}", deviceId, enabled ? "enabled" : "disabled");

        if (enabled) {
            bridgeHandler.enableDevice(productType, deviceIndex);
        } else {
            bridgeHandler.disableDevice(productType, deviceIndex);
        }
    }

    /**
     * Gets the product type used for enable/disable API calls.
     *
     * <p>
     * Subclasses should override this if they represent a different product category (e.g. sirens,
     * keypads, plugs), or return {@code null} if the Diagral API doesn't support enable/disable for this
     * category at all (e.g. transmitters, cameras) - such subclasses should also declare their
     * {@code enabled} channel as read-only in {@code thing-types.xml}.
     * </p>
     *
     * @return the product type, or null if not supported
     */
    protected @Nullable String getProductType() {
        return PRODUCT_TYPE_SENSOR;
    }

    /**
     * Refreshes the sensor status from the bridge and updates all channels.
     */
    @Override
    public void refreshStatus() {
        String currentDeviceId = deviceId;
        if (currentDeviceId == null) {
            logger.debug("Cannot refresh status - device ID not set");
            return;
        }

        DiagralBridgeHandler bridgeHandler = getBridgeHandler();
        if (bridgeHandler == null) {
            logger.debug("Cannot refresh status - bridge handler not available");
            return;
        }

        DiagralSystemConfiguration config = bridgeHandler.getSystemConfiguration();
        if (config == null) {
            logger.debug("No system configuration available");
            return;
        }

        DiagralDevice device = findDevice(config, currentDeviceId);
        if (device == null) {
            logger.warn("Device not found in configuration: {}", currentDeviceId);
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "Device not found");
            return;
        }

        updateChannels(device);
    }

    /**
     * Finds a device in the system configuration by ID.
     *
     * @param config the system configuration
     * @param deviceId the device ID to find
     * @return the device, or null if not found
     */
    private @Nullable DiagralDevice findDevice(DiagralSystemConfiguration config, String deviceId) {
        List<DiagralDevice> devices = getDeviceList(config);
        if (devices == null) {
            return null;
        }

        for (DiagralDevice device : devices) {
            if (deviceId.equals(device.getUniqueId())) {
                return device;
            }
        }
        return null;
    }

    /**
     * Gets the device list this handler's devices are found in.
     *
     * <p>
     * Subclasses should override this if they represent a different device category (e.g. sirens,
     * keypads, transmitters, cameras).
     * </p>
     *
     * @param config the system configuration
     * @return the matching device list, or null if not available
     */
    protected @Nullable List<DiagralDevice> getDeviceList(DiagralSystemConfiguration config) {
        return config.sensors;
    }

    /**
     * Updates all channels with the current device state.
     *
     * @param device the device data from the API
     */
    private void updateChannels(DiagralDevice device) {
        // Update common sensor channels
        updateState(CHANNEL_ENABLED, OnOffType.from(!device.inhibited));

        if (device.anomalies != null && device.anomalies.containsKey(DEVICE_ANOMALY_POWER_SUPPLY_ALERT)
                && device.anomalies.get(DEVICE_ANOMALY_POWER_SUPPLY_ALERT)) {
            updateState(CHANNEL_LOW_BATTERY, OnOffType.ON);
        } else {
            updateState(CHANNEL_LOW_BATTERY, OnOffType.OFF);
        }

        // Let subclasses update their specific channels
        updateSensorSpecificChannels(device);
    }

    /**
     * Updates sensor-type-specific channels.
     *
     * <p>
     * Subclasses should override this method to update their specific channels
     * (e.g., motion detection, contact state).
     * </p>
     *
     * @param device the device data from the API
     */
    protected abstract void updateSensorSpecificChannels(DiagralDevice device);

    /**
     * Gets the bridge handler.
     *
     * @return the bridge handler, or null if not available
     */
    protected @Nullable DiagralBridgeHandler getBridgeHandler() {
        Bridge bridge = getBridge();
        if (bridge == null) {
            return null;
        }

        ThingHandler handler = bridge.getHandler();
        if (handler instanceof DiagralBridgeHandler bridgeHandler) {
            return bridgeHandler;
        }

        return null;
    }

    @Override
    public void bridgeStatusChanged(org.openhab.core.thing.ThingStatusInfo bridgeStatusInfo) {
        if (bridgeStatusInfo.getStatus() == ThingStatus.ONLINE) {
            updateStatus(ThingStatus.ONLINE);
            refreshStatus();
        } else {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE);
        }
    }
}
