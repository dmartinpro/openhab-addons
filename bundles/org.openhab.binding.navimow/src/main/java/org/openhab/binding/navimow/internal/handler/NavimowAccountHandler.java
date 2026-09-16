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

import static org.openhab.binding.navimow.internal.NavimowBindingConstants.DEFAULT_POLLING_INTERVAL_S;
import static org.openhab.binding.navimow.internal.NavimowBindingConstants.OAUTH_AUTHORIZE_URL;
import static org.openhab.binding.navimow.internal.NavimowBindingConstants.OAUTH_CLIENT_ID;
import static org.openhab.binding.navimow.internal.NavimowBindingConstants.OAUTH_CLIENT_SECRET;
import static org.openhab.binding.navimow.internal.NavimowBindingConstants.OAUTH_TOKEN_URL;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.openhab.binding.navimow.internal.api.NavimowApiClient;
import org.openhab.binding.navimow.internal.api.NavimowAuthorizationProvider;
import org.openhab.binding.navimow.internal.api.NavimowCommand;
import org.openhab.binding.navimow.internal.api.dto.MqttUserInfo;
import org.openhab.binding.navimow.internal.api.dto.NavimowDevice;
import org.openhab.binding.navimow.internal.api.dto.NavimowDeviceStatus;
import org.openhab.binding.navimow.internal.api.exceptions.NavimowAuthenticationException;
import org.openhab.binding.navimow.internal.api.exceptions.NavimowCommunicationException;
import org.openhab.binding.navimow.internal.discovery.NavimowDiscoveryService;
import org.openhab.binding.navimow.internal.mqtt.NavimowMqttConnection;
import org.openhab.binding.navimow.internal.mqtt.NavimowMqttListener;
import org.openhab.binding.navimow.internal.mqtt.dto.MqttVehicleState;
import org.openhab.binding.navimow.internal.servlet.NavimowConnectServlet;
import org.openhab.core.auth.client.oauth2.AccessTokenResponse;
import org.openhab.core.auth.client.oauth2.OAuthClientService;
import org.openhab.core.auth.client.oauth2.OAuthException;
import org.openhab.core.auth.client.oauth2.OAuthFactory;
import org.openhab.core.auth.client.oauth2.OAuthResponseException;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BaseBridgeHandler;
import org.openhab.core.thing.binding.ThingHandlerService;
import org.openhab.core.types.Command;
import org.osgi.service.http.HttpService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link NavimowAccountHandler} is the bridge handler for a Navimow account. It owns the OAuth2
 * authorization flow (see {@link NavimowConnectServlet}), the REST API client, and the polling loop
 * that fetches every linked mower's status and pushes it to the corresponding
 * {@link NavimowMowerHandler}.
 *
 * <p>
 * REST polling (via {@code getVehicleStatus}) remains the bridge's only source of truth for
 * {@code ThingStatus} and every channel. MQTT support (see {@link NavimowMqttConnection}) is optional
 * (config parameter {@code enableMqtt}, default off) and purely additive: it pushes the same
 * {@code activity}/{@code battery-level} data REST polling already provides, just within milliseconds
 * of a real change instead of waiting for the next poll - see {@link NavimowMqttConnection}'s Javadoc
 * for why that turned out to be its actual value (mower position, the original goal, is not part of
 * this data at all). If MQTT fails to connect, the bridge stays fully functional on REST alone.
 *
 * <p>
 * The MQTT connection is opened from inside this handler, never handed off to a separate component -
 * see {@link NavimowMqttConnection}'s Javadoc for why that is not just a design preference.
 *
 * @author David Martin - Initial contribution
 */
@NonNullByDefault
public class NavimowAccountHandler extends BaseBridgeHandler
        implements NavimowAuthorizationProvider, NavimowMqttListener {

    private static final int RECONNECT_DELAY_S = 60;
    private static final int MIN_POLLING_INTERVAL_S = 10;

    private final Logger logger = LoggerFactory.getLogger(NavimowAccountHandler.class);
    private final HttpClient httpClient;
    private final HttpService httpService;
    private final OAuthFactory oAuthFactory;
    private final Map<String, NavimowMowerHandler> mowerHandlers = new ConcurrentHashMap<>();
    private final NavimowMqttConnection mqttConnection = new NavimowMqttConnection(this);

    private volatile @Nullable OAuthClientService oAuthClientService;
    private volatile @Nullable NavimowApiClient apiClient;
    private volatile @Nullable NavimowConnectServlet connectServlet;
    private volatile @Nullable ScheduledFuture<?> pollingJob;
    private volatile @Nullable ScheduledFuture<?> reconnectJob;

    /**
     * @param bridge the account bridge Thing this handler is for
     * @param httpClient the shared Jetty client used for all REST/MQTT-credential requests
     * @param httpService the OSGi HTTP service, passed through to {@link NavimowConnectServlet}
     * @param oAuthFactory used to create/tear down this bridge's {@code OAuthClientService}
     */
    public NavimowAccountHandler(Bridge bridge, HttpClient httpClient, HttpService httpService,
            OAuthFactory oAuthFactory) {
        super(bridge);
        this.httpClient = httpClient;
        this.httpService = httpService;
        this.oAuthFactory = oAuthFactory;
    }

    @Override
    public void initialize() {
        NavimowBridgeConfiguration config = getConfigAs(NavimowBridgeConfiguration.class);
        Integer pollingInterval = config.getPollingInterval();
        if (pollingInterval != null && pollingInterval < MIN_POLLING_INTERVAL_S) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "@text/conf-error-invalid-polling-interval");
            return;
        }

        OAuthClientService oAuthClientService = oAuthFactory.createOAuthClientService(thing.getUID().getAsString(),
                OAUTH_TOKEN_URL, OAUTH_AUTHORIZE_URL, OAUTH_CLIENT_ID, OAUTH_CLIENT_SECRET, null, false);
        this.oAuthClientService = oAuthClientService;
        this.apiClient = new NavimowApiClient(httpClient, this);

        updateStatus(ThingStatus.UNKNOWN);
        scheduler.execute(() -> openConnection(null, null));
    }

    /**
     * Called by {@link NavimowConnectServlet} once the user has completed the browser login and
     * Navimow redirected back with an authorization code.
     *
     * @param code the authorization code from the redirect
     * @param redirectUri the exact redirect_uri used in the authorization request (must match for
     *            the token exchange to succeed)
     */
    public void completeAuthorization(String code, String redirectUri) {
        scheduler.execute(() -> openConnection(code, redirectUri));
    }

    private void openConnection(@Nullable String code, @Nullable String redirectUri) {
        if (!authenticate(code, redirectUri)) {
            return;
        }
        freeConnectServlet();
        freeReconnectJob();
        NavimowBridgeConfiguration config = getConfigAs(NavimowBridgeConfiguration.class);
        startPolling(config);
        startMqttIfEnabled(config);
        updateStatus(ThingStatus.ONLINE);
    }

    private boolean authenticate(@Nullable String code, @Nullable String redirectUri) {
        OAuthClientService oAuthClientService = this.oAuthClientService;
        if (oAuthClientService == null) {
            logger.debug("Bridge not initialized, cannot authenticate");
            return false;
        }

        AccessTokenResponse accessTokenResponse;
        try {
            accessTokenResponse = code != null
                    ? oAuthClientService.getAccessTokenResponseByAuthorizationCode(code, redirectUri)
                    : oAuthClientService.getAccessTokenResponse();
        } catch (OAuthException | OAuthResponseException e) {
            logger.debug("OAuth authorization failed, a fresh login is required: {}", e.getMessage());
            startAuthorizationFlow();
            return false;
        } catch (IOException e) {
            scheduleReconnect(code, redirectUri, e.getMessage());
            return false;
        }

        if (accessTokenResponse == null || accessTokenResponse.getAccessToken() == null) {
            logger.debug("No access token available, a fresh login is required");
            startAuthorizationFlow();
            return false;
        }
        return true;
    }

    private void scheduleReconnect(@Nullable String code, @Nullable String redirectUri, @Nullable String message) {
        updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, message);
        freeReconnectJob();
        reconnectJob = scheduler.schedule(() -> openConnection(code, redirectUri), RECONNECT_DELAY_S, TimeUnit.SECONDS);
    }

    private void startAuthorizationFlow() {
        freeConnectServlet();
        NavimowConnectServlet servlet = new NavimowConnectServlet(this, httpService);
        this.connectServlet = servlet;
        servlet.startListening();
        updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                "@text/conf-error-authorization-required [\"" + servlet.getPath() + "\"]");
    }

    private void freeConnectServlet() {
        NavimowConnectServlet servlet = this.connectServlet;
        if (servlet != null) {
            servlet.dispose();
        }
        this.connectServlet = null;
    }

    private void freeReconnectJob() {
        ScheduledFuture<?> job = this.reconnectJob;
        if (job != null) {
            job.cancel(true);
        }
        this.reconnectJob = null;
    }

    @Override
    public String getAccessToken() throws NavimowAuthenticationException, NavimowCommunicationException {
        OAuthClientService oAuthClientService = this.oAuthClientService;
        if (oAuthClientService == null) {
            throw new NavimowAuthenticationException("Bridge not initialized");
        }
        try {
            AccessTokenResponse response = oAuthClientService.getAccessTokenResponse();
            String accessToken = response != null ? response.getAccessToken() : null;
            if (accessToken == null) {
                throw new NavimowAuthenticationException("No access token available");
            }
            return accessToken;
        } catch (OAuthException | OAuthResponseException e) {
            throw new NavimowAuthenticationException("Failed to obtain access token: " + e.getMessage(), e);
        } catch (IOException e) {
            throw new NavimowCommunicationException("Failed to obtain access token", e);
        }
    }

    private void startPolling(NavimowBridgeConfiguration config) {
        if (pollingJob == null) {
            int interval = Objects.requireNonNullElse(config.getPollingInterval(), DEFAULT_POLLING_INTERVAL_S);
            pollingJob = scheduler.scheduleWithFixedDelay(this::poll, 0, interval, TimeUnit.SECONDS);
        }
    }

    private void stopPolling() {
        ScheduledFuture<?> job = pollingJob;
        if (job != null) {
            job.cancel(true);
        }
        pollingJob = null;
    }

    /**
     * Opens the optional MQTT push connection if {@code enableMqtt} is set, off the calling thread so
     * a slow or failing MQTT handshake never delays the bridge going {@code ONLINE} on REST alone.
     *
     * <p>
     * Fetches a fresh device list and {@code mqtt/userInfo} each time, so this doubles as the
     * reconnect path after an OAuth token refresh - see {@link NavimowMqttConnection#connect} for why
     * that matters (the MQTT credentials may need to change together with the token).
     */
    private void startMqttIfEnabled(NavimowBridgeConfiguration config) {
        if (!config.isEnableMqtt()) {
            return;
        }
        NavimowApiClient client = apiClient;
        if (client == null) {
            return;
        }
        scheduler.execute(() -> {
            try {
                List<String> ids = deviceIds(client.getDevices());
                MqttUserInfo mqttUserInfo = client.getMqttUserInfo();
                mqttConnection.connect(mqttUserInfo, getAccessToken(), ids);
            } catch (NavimowAuthenticationException | NavimowCommunicationException e) {
                logger.debug("Could not establish MQTT connection, continuing with REST polling only: {}",
                        e.getMessage());
            }
        });
    }

    @Override
    public void onVehicleState(String deviceId, MqttVehicleState state) {
        NavimowMowerHandler handler = mowerHandlers.get(deviceId);
        if (handler != null) {
            handler.updateFromMqttState(state);
        }
    }

    /**
     * @param devices a device list as returned by {@link NavimowApiClient#getDevices()}
     * @return each device's id, dropping any entry the API returned without one
     */
    private static List<String> deviceIds(List<NavimowDevice> devices) {
        return devices.stream().map(d -> d.id).filter(Objects::nonNull).toList();
    }

    private synchronized void poll() {
        NavimowApiClient client = apiClient;
        if (client == null) {
            return;
        }
        try {
            List<NavimowDevice> devices = client.getDevices();
            Map<String, NavimowDeviceStatus> statuses = client.getDeviceStatuses(deviceIds(devices));

            updateStatus(ThingStatus.ONLINE);
            for (NavimowDevice device : devices) {
                String id = device.id;
                if (id == null) {
                    continue;
                }
                NavimowMowerHandler handler = mowerHandlers.get(id);
                if (handler != null) {
                    handler.updateFromDevice(device);
                    handler.updateFromStatus(statuses.get(id));
                } else {
                    logger.debug("No handler registered yet for mower '{}'", id);
                }
            }
        } catch (NavimowAuthenticationException e) {
            logger.debug("Poll rejected as unauthorized, attempting to refresh/renew authorization: {}",
                    e.getMessage());
            stopPolling();
            scheduler.execute(() -> openConnection(null, null));
        } catch (NavimowCommunicationException e) {
            logger.debug("Poll failed: {}", e.getMessage());
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, e.getMessage());
        }
    }

    /**
     * Sends a command to one mower, then triggers an immediate off-cycle poll so the UI reflects the
     * result within seconds rather than waiting for the next scheduled poll.
     *
     * @param deviceId the target device id
     * @param command the command to send
     * @throws NavimowAuthenticationException if the access token was rejected
     * @throws NavimowCommunicationException if the command could not be sent or was rejected
     */
    public void sendCommand(String deviceId, NavimowCommand command)
            throws NavimowAuthenticationException, NavimowCommunicationException {
        NavimowApiClient client = apiClient;
        if (client == null) {
            throw new NavimowCommunicationException("Bridge not initialized");
        }
        try {
            client.sendCommand(deviceId, command);
        } finally {
            scheduler.execute(this::poll);
        }
    }

    /**
     * Registers a mower handler so {@link #poll()} and {@link #onVehicleState} can dispatch updates to
     * it by device id. Called by {@code NavimowMowerHandler.initialize()}.
     *
     * @param deviceId the mower's device id
     * @param handler the handler to dispatch updates to
     */
    public void registerMowerHandler(String deviceId, NavimowMowerHandler handler) {
        mowerHandlers.put(deviceId, handler);
    }

    /**
     * Reverses {@link #registerMowerHandler}. Called by {@code NavimowMowerHandler.dispose()}.
     *
     * @param deviceId the mower's device id
     */
    public void unregisterMowerHandler(String deviceId) {
        mowerHandlers.remove(deviceId);
    }

    /**
     * @return the currently known devices on the account, for use by the discovery service
     * @throws NavimowAuthenticationException if the access token was rejected
     * @throws NavimowCommunicationException if the request failed
     */
    public List<NavimowDevice> getDevices() throws NavimowAuthenticationException, NavimowCommunicationException {
        NavimowApiClient client = apiClient;
        if (client == null) {
            throw new NavimowCommunicationException("Bridge not initialized");
        }
        return client.getDevices();
    }

    @Override
    public void dispose() {
        stopPolling();
        mqttConnection.disconnect();
        freeReconnectJob();
        freeConnectServlet();

        if (oAuthClientService != null) {
            oAuthFactory.ungetOAuthService(thing.getUID().getAsString());
        }
        oAuthClientService = null;
        apiClient = null;
    }

    @Override
    public void handleRemoval() {
        oAuthFactory.deleteServiceAndAccessToken(thing.getUID().getAsString());
        super.handleRemoval();
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        // The account bridge itself has no channels; nothing to do.
    }

    @Override
    public Collection<Class<? extends ThingHandlerService>> getServices() {
        return List.of(NavimowDiscoveryService.class);
    }
}
