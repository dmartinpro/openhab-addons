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

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.diagral.internal.bridge.DiagralBridgeHandler;
import org.openhab.binding.diagral.internal.dto.DiagralAnomalies;
import org.openhab.binding.diagral.internal.dto.DiagralSystemConfiguration;
import org.openhab.binding.diagral.internal.dto.DiagralSystemStatus;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link DiagralSystemHandler} handles the main alarm system control.
 *
 * <p>
 * This handler provides channels for monitoring and controlling the alarm system's armed status,
 * mode selection, and anomaly reporting.
 * </p>
 *
 * <p>
 * Supported channels:
 * <ul>
 * <li>{@code armed-status} - Current system mode (OFF, FULL, PRESENCE, PARTIAL1, PARTIAL2)</li>
 * <li>{@code mode-control} - Command channel to change the system mode</li>
 * <li>{@code anomalies-present} - Switch indicating if any anomalies are present</li>
 * <li>{@code anomaly-count} - Number of active anomalies</li>
 * </ul>
 * </p>
 *
 * @author David Martin - Initial contribution
 */
@NonNullByDefault
public class DiagralSystemHandler extends DiagralBaseThingHandler {

    private final Logger logger = LoggerFactory.getLogger(DiagralSystemHandler.class);

    /**
     * Constructs a new DiagralSystemHandler.
     *
     * @param thing the thing to handle
     */
    public DiagralSystemHandler(Thing thing) {
        super(thing);
    }

    /**
     * Checks bridge availability and performs an initial status refresh.
     */
    @Override
    public void initialize() {
        logger.debug("Initializing Diagral system handler");

        if (!goOnlineIfBridgeAvailable()) {
            return;
        }

        logger.debug("Diagral system handler initialized");

        // Off-thread: refreshStatus() reaches the cloud API, and initialize() must return promptly.
        refreshStatusAsync();
    }

    /**
     * Handles commands sent to this system's channels: {@link RefreshType} triggers a status refresh, and
     * a {@code StringType} on {@code mode-control} sets the alarm system mode via the bridge.
     *
     * @param channelUID the channel the command targets
     * @param command the command received
     */
    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (command instanceof RefreshType) {
            refreshStatusAsync();
            return;
        }

        String channelId = channelUID.getId();

        if (CHANNEL_MODE_CONTROL.equals(channelId) && command instanceof StringType) {
            String mode = command.toString();
            setSystemMode(mode);
        }
    }

    /**
     * Sets the alarm system mode.
     *
     * @param mode the mode to set (OFF, FULL, PRESENCE, PARTIAL1, PARTIAL2)
     */
    private void setSystemMode(String mode) {
        DiagralBridgeHandler bridgeHandler = getBridgeHandler();
        if (bridgeHandler == null) {
            logger.warn("Cannot set system mode - bridge handler not available");
            return;
        }

        logger.debug("Setting system mode to: {}", mode);
        bridgeHandler.setSystemMode(mode);
    }

    /**
     * Refreshes the system status from the bridge and updates all channels.
     */
    @Override
    public void refreshStatus() {
        DiagralBridgeHandler bridgeHandler = getBridgeHandler();
        if (bridgeHandler == null) {
            logger.debug("Cannot refresh status - bridge handler not available");
            return;
        }

        DiagralSystemStatus status = bridgeHandler.getSystemStatus();
        if (status == null) {
            logger.debug("No system status available");
            return;
        }

        DiagralSystemConfiguration config = bridgeHandler.getSystemConfiguration();
        if (config == null) {
            logger.debug("No system configuration available");
            return;
        }

        updateChannels(status, config, bridgeHandler);
    }

    /**
     * Updates all channels with the current system status.
     *
     * @param status the system status from the API
     * @param config the system configuration from the API
     * @param bridgeHandler the bridge handler, used to fetch the current anomalies and the mode to display
     *            on {@code mode-control}
     */
    private void updateChannels(DiagralSystemStatus status, DiagralSystemConfiguration config,
            DiagralBridgeHandler bridgeHandler) {
        // Update armed status
        String statusStr = status.status;
        if (statusStr != null) {
            updateState(CHANNEL_ARMED_STATUS, new StringType(statusStr));
        }

        // Mirror the current mode onto mode-control itself, so this writable channel doesn't stay
        // permanently NULL (see DiagralBridgeHandler.getDisplayedMode() for how it's derived - only ever
        // one of the five named modes, holding the last selection during a transitional status).
        String displayedMode = bridgeHandler.getDisplayedMode();
        if (displayedMode != null) {
            updateState(CHANNEL_MODE_CONTROL, new StringType(displayedMode));
        }

        // Update central unit battery status
        if (config.centralInformation != null && config.centralInformation.anomalies != null
                && ((config.centralInformation.anomalies.containsKey(DEVICE_ANOMALY_MAIN_POWERSUPPLY_ALERT)
                        && config.centralInformation.anomalies.get(DEVICE_ANOMALY_MAIN_POWERSUPPLY_ALERT))
                        || (config.centralInformation.anomalies.containsKey(DEVICE_ANOMALY_SECOND_POWERSUPPLY_ALERT)
                                && config.centralInformation.anomalies.get(DEVICE_ANOMALY_SECOND_POWERSUPPLY_ALERT)))) {
            updateState(CHANNEL_CENTRAL_LOW_BATTERY, OnOffType.ON);
        } else {
            updateState(CHANNEL_CENTRAL_LOW_BATTERY, OnOffType.OFF);
        }

        // Update anomalies
        DiagralAnomalies anomalies = bridgeHandler.getAnomalies();
        int anomalyCount = anomalies != null ? anomalies.getTotalCount() : 0;
        updateState(CHANNEL_ANOMALIES_PRESENT, OnOffType.from(anomalyCount > 0));
        updateState(CHANNEL_ANOMALY_COUNT, new DecimalType(anomalyCount));
    }
}
