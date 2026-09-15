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
package org.openhab.binding.navimow.internal.handler;

import static org.openhab.binding.navimow.internal.NavimowBindingConstants.CHANNEL_ACTIVITY;
import static org.openhab.binding.navimow.internal.NavimowBindingConstants.CHANNEL_BATTERY_LEVEL;
import static org.openhab.binding.navimow.internal.NavimowBindingConstants.CHANNEL_CONTROL;
import static org.openhab.binding.navimow.internal.NavimowBindingConstants.PROPERTY_BATTERY_TIER;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.navimow.internal.api.NavimowCommand;
import org.openhab.binding.navimow.internal.api.dto.NavimowActivity;
import org.openhab.binding.navimow.internal.api.dto.NavimowDevice;
import org.openhab.binding.navimow.internal.api.dto.NavimowDeviceStatus;
import org.openhab.binding.navimow.internal.api.exceptions.NavimowAuthenticationException;
import org.openhab.binding.navimow.internal.api.exceptions.NavimowCommunicationException;
import org.openhab.binding.navimow.internal.mqtt.dto.MqttVehicleState;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.ThingStatusInfo;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.openhab.core.types.UnDefType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link NavimowMowerHandler} is the Thing handler for a single Navimow mower. It has no direct
 * network access of its own - it registers itself with the owning {@link NavimowAccountHandler},
 * which pushes status updates to it once per poll cycle via {@link #updateFromStatus(NavimowDeviceStatus)}
 * and {@link #updateFromDevice(NavimowDevice)}, and forwards {@code control} channel commands back to
 * the bridge to be sent over REST.
 *
 * @author David Martin - Initial contribution
 */
@NonNullByDefault
public class NavimowMowerHandler extends BaseThingHandler {

    private final Logger logger = LoggerFactory.getLogger(NavimowMowerHandler.class);

    private @Nullable String deviceId;

    public NavimowMowerHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void initialize() {
        NavimowMowerConfiguration config = getConfigAs(NavimowMowerConfiguration.class);
        if (config.id.isBlank()) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "@text/conf-error-no-device-id");
            return;
        }
        deviceId = config.id;

        NavimowAccountHandler accountHandler = getAccountHandler();
        if (accountHandler == null) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_UNINITIALIZED);
            return;
        }
        accountHandler.registerMowerHandler(config.id, this);

        updateStatus(ThingStatus.UNKNOWN);
    }

    @Override
    public void dispose() {
        String id = deviceId;
        NavimowAccountHandler accountHandler = getAccountHandler();
        if (id != null && accountHandler != null) {
            accountHandler.unregisterMowerHandler(id);
        }
    }

    @Override
    public void bridgeStatusChanged(ThingStatusInfo bridgeStatusInfo) {
        if (bridgeStatusInfo.getStatus() == ThingStatus.ONLINE) {
            updateStatus(ThingStatus.UNKNOWN);
        } else {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE);
        }
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (command instanceof RefreshType) {
            return;
        }
        if (!CHANNEL_CONTROL.equals(channelUID.getId())) {
            return;
        }
        String id = deviceId;
        NavimowAccountHandler accountHandler = getAccountHandler();
        if (id == null || accountHandler == null) {
            logger.debug("Cannot handle command, mower not fully initialized");
            return;
        }

        NavimowCommand mowerCommand;
        try {
            mowerCommand = NavimowCommand.valueOf(command.toString());
        } catch (IllegalArgumentException e) {
            logger.warn("Unsupported control command: {}", command);
            return;
        }

        try {
            accountHandler.sendCommand(id, mowerCommand);
        } catch (NavimowAuthenticationException e) {
            // The bridge handler already reacts to authentication failures; nothing to do here.
            logger.debug("Command rejected as unauthorized: {}", e.getMessage());
        } catch (NavimowCommunicationException e) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, e.getMessage());
        }
    }

    /**
     * Applies one poll cycle's status snapshot to this mower's channels. Called by
     * {@link NavimowAccountHandler} once per poll cycle; never called concurrently for the same
     * handler.
     *
     * @param status this mower's status from the latest poll, or {@code null} if the API did not
     *            return an entry for it
     */
    public void updateFromStatus(@Nullable NavimowDeviceStatus status) {
        if (status == null) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                    "@text/offline.comm-error-no-status-returned");
            return;
        }

        updateStatus(ThingStatus.ONLINE);

        updateActivity(status.vehicleState);

        Integer batteryPercentage = status.getBatteryPercentage();
        updateState(CHANNEL_BATTERY_LEVEL,
                batteryPercentage != null ? new DecimalType(batteryPercentage) : UnDefType.UNDEF);

        String batteryTier = status.descriptiveCapacityRemaining;
        if (batteryTier != null) {
            updateProperty(PROPERTY_BATTERY_TIER, batteryTier);
        }
    }

    /**
     * Refreshes this mower's static device-metadata Thing properties. Called by
     * {@link NavimowAccountHandler} once per poll cycle, from the same {@code authList} response it
     * already fetches to resolve device ids - self-correcting rather than a one-time discovery-time
     * snapshot, so a firmware update is reflected without needing to re-discover the Thing.
     *
     * @param device this mower's entry from the latest {@code authList} response
     */
    public void updateFromDevice(NavimowDevice device) {
        String model = device.model;
        if (model != null) {
            updateProperty(Thing.PROPERTY_MODEL_ID, model);
        }
        String firmware = device.firmware;
        if (firmware != null) {
            updateProperty(Thing.PROPERTY_FIRMWARE_VERSION, firmware);
        }
    }

    /**
     * Applies one MQTT push message to this mower's channels. Called by {@link NavimowAccountHandler}
     * from {@link org.openhab.binding.navimow.internal.mqtt.NavimowMqttListener#onVehicleState} - may
     * be called concurrently with {@link #updateFromStatus(NavimowDeviceStatus)} from a different
     * thread. Both update the same two channels with last-write-wins semantics; this is intentional -
     * MQTT's whole value here is pushing the same {@code activity}/{@code battery-level} data REST
     * polling already provides, just with much lower latency (see {@link MqttVehicleState}'s Javadoc
     * for why position, the feature's original goal, turned out not to be part of this payload).
     *
     * @param state the parsed MQTT state message
     */
    public void updateFromMqttState(MqttVehicleState state) {
        String rawState = state.vehicleState;
        if (rawState != null) {
            updateActivity(rawState);
        }

        Integer battery = state.battery;
        if (battery != null) {
            updateState(CHANNEL_BATTERY_LEVEL, new DecimalType(battery));
        }
    }

    /**
     * Converts a raw {@code vehicleState} string into the canonical {@code activity} channel value.
     * Shared by {@link #updateFromStatus} (which always calls this, even for a {@code null} raw
     * state - {@link NavimowActivity#fromRawState} maps that to {@code UNKNOWN}) and
     * {@link #updateFromMqttState} (which only calls this when a raw state is actually present,
     * leaving the channel untouched otherwise rather than resetting it to {@code UNKNOWN}).
     *
     * @param rawState the raw state string, or {@code null}
     */
    private void updateActivity(@Nullable String rawState) {
        NavimowActivity activity = NavimowActivity.fromRawState(rawState);
        updateState(CHANNEL_ACTIVITY, new StringType(activity.name().toLowerCase()));
    }

    private @Nullable NavimowAccountHandler getAccountHandler() {
        Bridge bridge = getBridge();
        if (bridge != null && bridge.getHandler() instanceof NavimowAccountHandler accountHandler) {
            return accountHandler;
        }
        return null;
    }
}
