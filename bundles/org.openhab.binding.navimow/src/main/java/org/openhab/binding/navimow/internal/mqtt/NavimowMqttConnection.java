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
package org.openhab.binding.navimow.internal.mqtt;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Properties;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.openhab.binding.navimow.internal.api.dto.MqttUserInfo;
import org.openhab.binding.navimow.internal.api.exceptions.NavimowCommunicationException;
import org.openhab.binding.navimow.internal.mqtt.dto.MqttVehicleState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

/**
 * {@link NavimowMqttConnection} owns one WebSocket MQTT connection to the Navimow cloud's push
 * channel, using the Eclipse Paho MQTT v3 client.
 *
 * <p>
 * <b>Why Paho, and why this class must only ever be driven by {@code NavimowAccountHandler}:</b> the
 * account bridge's OAuth access token is bound to whichever process first used it - see
 * {@code NavimowBindingConstants.BUSINESS_CODE_OAUTH_INFO_ILLEGAL} for the full investigation. That
 * finding applies here unchanged: this connection must be opened from inside the same bridge process
 * whose token it carries, never from a standalone tool. Paho was chosen over openHAB core's own
 * {@code MqttBrokerConnection} because the latter has no API for a custom WebSocket path or HTTP
 * headers, both of which this connection needs ({@link MqttConnectOptions#setCustomWebSocketHeaders}
 * for the {@code Authorization} header, and the path folded directly into the server URI). Paho is
 * already a proven dependency in this reactor for exactly this kind of raw MQTT need (see the
 * {@code roborock} and {@code bambulab} bindings), which is why it was picked over the HiveMQ MQTT
 * Client library initially investigated - same capability, no new OSGi packaging risk.
 *
 * <p>
 * <b>Live-confirmed 2026-09-15</b> against a real Navimow X430: only {@code state} carries any
 * traffic - {@code event}/{@code attributes} were subscribed to during testing and produced nothing
 * across a full docked/running/docking cycle - and its payload does not include position (see
 * {@link MqttVehicleState}). This connection's actual, proven value is push latency on
 * {@code activity}/{@code battery-level}, not new data REST lacks.
 *
 * @author David Martin - Initial contribution
 */
@NonNullByDefault
public class NavimowMqttConnection {

    private static final String TOPIC_TEMPLATE = "/downlink/vehicle/%s/realtimeDate/state";
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int DISCONNECT_TIMEOUT_MS = 1_000;
    private static final int QOS_AT_MOST_ONCE = 0;

    private final Logger logger = LoggerFactory.getLogger(NavimowMqttConnection.class);
    private final NavimowMqttListener listener;
    private final Gson gson = new Gson();

    private volatile @Nullable MqttAsyncClient client;

    /**
     * @param listener receives every parsed {@code state} update, one call per device topic
     */
    public NavimowMqttConnection(NavimowMqttListener listener) {
        this.listener = listener;
    }

    /**
     * Opens a fresh MQTT connection and subscribes to the {@code state} topic of every given device.
     * Any previous connection is closed first, so this also serves as "reconnect with new
     * credentials" - the intended way to recover from an access-token refresh, since the MQTT
     * username/password/Authorization header may all need to change together with it.
     *
     * @param info broker connection info, freshly fetched from {@code mqtt/userInfo}
     * @param accessToken the bridge's current OAuth access token, sent as a Bearer WebSocket header
     * @param deviceIds the mowers to subscribe to
     * @throws NavimowCommunicationException if the connection info is incomplete or the connection
     *             attempt itself failed
     */
    public synchronized void connect(MqttUserInfo info, String accessToken, Collection<String> deviceIds)
            throws NavimowCommunicationException {
        disconnect();

        String host = info.mqttHost;
        String path = info.mqttUrl;
        if (host == null || path == null) {
            throw new NavimowCommunicationException("mqtt/userInfo response is missing mqttHost or mqttUrl");
        }

        try {
            MqttAsyncClient newClient = new MqttAsyncClient(host + path, MqttAsyncClient.generateClientId(),
                    new MemoryPersistence());
            newClient.setCallback(new LoggingMqttCallback());

            MqttConnectOptions options = new MqttConnectOptions();
            options.setUserName(info.userName);
            String password = info.pwdInfo;
            if (password != null) {
                options.setPassword(password.toCharArray());
            }
            Properties headers = new Properties();
            headers.setProperty("Authorization", "Bearer " + accessToken);
            options.setCustomWebSocketHeaders(headers);
            options.setAutomaticReconnect(true);
            options.setCleanSession(true);

            newClient.connect(options).waitForCompletion(CONNECT_TIMEOUT_MS);

            for (String deviceId : deviceIds) {
                newClient.subscribe(buildStateTopic(deviceId), QOS_AT_MOST_ONCE,
                        (topic, message) -> onMessage(deviceId, message));
            }

            client = newClient;
            logger.debug("MQTT connection established, subscribed to {} device(s)", deviceIds.size());
        } catch (MqttException e) {
            throw new NavimowCommunicationException("Failed to establish MQTT connection", e);
        }
    }

    /**
     * Builds the {@code state} topic for one device. Package-private (rather than folded inline into
     * {@link #connect}) so the exact topic format can be pinned by a unit test without needing a real
     * MQTT broker to connect to.
     *
     * @param deviceId the device id
     * @return the topic to subscribe to for that device's state updates
     */
    static String buildStateTopic(String deviceId) {
        return String.format(TOPIC_TEMPLATE, deviceId);
    }

    /**
     * Closes the connection, if any. Safe to call when already disconnected.
     */
    public synchronized void disconnect() {
        MqttAsyncClient current = client;
        client = null;
        if (current == null) {
            return;
        }
        try {
            if (current.isConnected()) {
                current.disconnect().waitForCompletion(DISCONNECT_TIMEOUT_MS);
            }
        } catch (MqttException e) {
            logger.debug("Error disconnecting MQTT client, closing anyway: {}", e.getMessage());
        } finally {
            try {
                current.close();
            } catch (MqttException e) {
                logger.debug("Error closing MQTT client: {}", e.getMessage());
            }
        }
    }

    /**
     * Parses one incoming message and forwards it to the listener. Exceptions are caught rather than
     * propagated - {@link org.eclipse.paho.client.mqttv3.IMqttMessageListener#messageArrived} runs on
     * Paho's own internal thread, and letting an exception escape there would take the connection
     * down over a single malformed message.
     *
     * @param deviceId the device this topic belongs to (captured from the subscribe call, since the
     *            topic string itself is only used for routing, not re-parsed)
     * @param message the raw MQTT message
     */
    private void onMessage(String deviceId, MqttMessage message) {
        try {
            String raw = new String(message.getPayload(), StandardCharsets.UTF_8);
            logger.trace("MQTT message for device '{}': {}", deviceId, raw);
            MqttVehicleState state = gson.fromJson(raw, MqttVehicleState.class);
            if (state != null) {
                listener.onVehicleState(deviceId, state);
            }
        } catch (JsonParseException e) {
            logger.debug("Failed to parse MQTT message for device '{}': {}", deviceId, e.getMessage());
        }
    }

    /**
     * Minimal {@link MqttCallback} used only for connection-lifecycle logging - actual messages are
     * handled per-subscription via {@link org.eclipse.paho.client.mqttv3.IMqttMessageListener} instead
     * of this callback's {@code messageArrived}, so that method is never invoked.
     */
    private class LoggingMqttCallback implements MqttCallback {

        @Override
        public void connectionLost(@Nullable Throwable cause) {
            logger.debug("MQTT connection lost: {}", cause != null ? cause.getMessage() : "unknown reason");
        }

        @Override
        public void messageArrived(@Nullable String topic, @Nullable MqttMessage message) {
            // Unused: every subscription is made with its own per-topic IMqttMessageListener.
        }

        @Override
        public void deliveryComplete(@Nullable IMqttDeliveryToken token) {
            // Nothing to do: this connection only subscribes, it never publishes.
        }
    }
}
