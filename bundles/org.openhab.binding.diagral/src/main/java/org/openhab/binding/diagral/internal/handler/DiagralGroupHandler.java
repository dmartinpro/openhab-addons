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
import org.openhab.binding.diagral.internal.dto.DiagralSystemStatus;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.StringType;
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
 * The {@link DiagralGroupHandler} handles device groups.
 *
 * <p>
 * Groups in the Diagral system allow controlling multiple devices together.
 * This handler provides channels for activating/deactivating groups and monitoring
 * their status.
 * </p>
 *
 * <p>
 * Supported channels:
 * <ul>
 * <li>{@code active} - Group activation state (ON=active, OFF=inactive)</li>
 * <li>{@code status} - Group status description</li>
 * </ul>
 * </p>
 *
 * @author David Martin - Initial contribution
 */
@NonNullByDefault
public class DiagralGroupHandler extends BaseThingHandler implements DiagralRefreshableHandler {

    private final Logger logger = LoggerFactory.getLogger(DiagralGroupHandler.class);
    private @Nullable String groupId;

    /**
     * Constructs a new DiagralGroupHandler.
     *
     * @param thing the thing to handle
     */
    public DiagralGroupHandler(Thing thing) {
        super(thing);
    }

    /**
     * Reads and validates the group configuration, checks bridge availability, and performs an initial
     * status refresh.
     */
    @Override
    public void initialize() {
        logger.debug("Initializing Diagral group handler");

        DiagralConfiguration config = getConfigAs(DiagralConfiguration.class);

        if (!config.isValidGroup()) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "Invalid group configuration");
            return;
        }

        this.groupId = config.groupId;

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
        logger.debug("Diagral group handler initialized for group: {}", groupId);

        // Initial status refresh
        refreshStatus();
    }

    /**
     * Handles commands sent to this group's channels: {@link RefreshType} triggers a status refresh, and
     * an {@code OnOffType} on {@code active} activates/deactivates the group via the bridge.
     *
     * @param channelUID the channel the command targets
     * @param command the command received
     */
    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (command instanceof RefreshType) {
            refreshStatus();
            return;
        }

        String channelId = channelUID.getId();

        if (CHANNEL_GROUP_ACTIVE.equals(channelId) && command instanceof OnOffType onOffCommand) {
            setGroupActive(onOffCommand == OnOffType.ON);
        }
    }

    /**
     * Activates or deactivates the group.
     *
     * @param active true to activate, false to deactivate
     */
    private void setGroupActive(boolean active) {
        String currentGroupId = groupId;
        if (currentGroupId == null) {
            logger.warn("Cannot set group active - group ID not set");
            return;
        }

        DiagralBridgeHandler bridgeHandler = getBridgeHandler();
        if (bridgeHandler == null) {
            logger.warn("Cannot set group active - bridge handler not available");
            return;
        }

        logger.debug("Setting group {} to {}", currentGroupId, active ? "active" : "inactive");

        if (active) {
            bridgeHandler.activateGroup(currentGroupId);
        } else {
            bridgeHandler.disableGroup(currentGroupId);
        }
    }

    /**
     * Refreshes the group status from the bridge and updates all channels.
     */
    @Override
    public void refreshStatus() {
        String currentGroupId = groupId;
        if (currentGroupId == null) {
            logger.debug("Cannot refresh status - group ID not set");
            return;
        }

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

        updateChannels(status, currentGroupId);
    }

    /**
     * Updates all channels with the current group state.
     *
     * @param status the system status from the API
     * @param groupId the group ID
     */
    private void updateChannels(DiagralSystemStatus status, String groupId) {
        // Check if group is in activated groups list
        boolean isActive = false;
        List<Integer> activatedGroups = status.activatedGroups;

        if (activatedGroups != null) {
            try {
                int groupIndex = Integer.parseInt(groupId);
                isActive = activatedGroups.contains(groupIndex);
            } catch (NumberFormatException e) {
                logger.warn("Invalid group ID format: {}", groupId);
            }
        }

        updateState(CHANNEL_GROUP_ACTIVE, OnOffType.from(isActive));
        updateState(CHANNEL_GROUP_STATUS, new StringType(isActive ? "Active" : "Inactive"));
    }

    /**
     * Gets the bridge handler.
     *
     * @return the bridge handler, or null if not available
     */
    private @Nullable DiagralBridgeHandler getBridgeHandler() {
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

    /**
     * Mirrors this thing's status to the bridge's status: goes {@code ONLINE} (and refreshes) when the
     * bridge comes online, goes {@code OFFLINE} otherwise.
     *
     * @param bridgeStatusInfo the bridge's new status
     */
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
