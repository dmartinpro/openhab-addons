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
package org.openhab.binding.diagral.internal.bridge;

import static org.openhab.binding.diagral.internal.DiagralBindingConstants.CONFIG_PASSWORD;
import static org.openhab.binding.diagral.internal.DiagralBindingConstants.CONFIG_PIN_CODE;
import static org.openhab.binding.diagral.internal.DiagralBindingConstants.CONFIG_SERIAL_ID;
import static org.openhab.binding.diagral.internal.DiagralBindingConstants.CONFIG_USERNAME;
import static org.openhab.binding.diagral.internal.DiagralBindingConstants.PASSWORD_MISSING;
import static org.openhab.binding.diagral.internal.DiagralBindingConstants.PINCODE_MISSING;
import static org.openhab.binding.diagral.internal.DiagralBindingConstants.SERIALID_MISSING;
import static org.openhab.binding.diagral.internal.DiagralBindingConstants.USERNAME_MISSING;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.openhab.binding.diagral.internal.DiagralBridgeConfiguration;
import org.openhab.binding.diagral.internal.discovery.DiagralDiscoveryService;
import org.openhab.binding.diagral.internal.dto.DiagralAnomalies;
import org.openhab.binding.diagral.internal.dto.DiagralSystemConfiguration;
import org.openhab.binding.diagral.internal.dto.DiagralSystemDetails;
import org.openhab.binding.diagral.internal.dto.DiagralSystemStatus;
import org.openhab.binding.diagral.internal.exception.DiagralAuthenticationException;
import org.openhab.binding.diagral.internal.exception.DiagralException;
import org.openhab.binding.diagral.internal.handler.DiagralRefreshableHandler;
import org.openhab.core.config.core.status.ConfigStatusMessage;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.ConfigStatusBridgeHandler;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.thing.binding.ThingHandlerService;
import org.openhab.core.types.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link DiagralBridgeHandler} manages the connection to the Diagral cloud API.
 *
 * @author David Martin - Initial contribution
 */
@NonNullByDefault
public class DiagralBridgeHandler extends ConfigStatusBridgeHandler implements DiagralClient {

    /**
     * How long a fetched system status is reused before a fresh call is made. Collapses the handful
     * of {@link #getSystemStatus()} calls that child handlers make within the same poll tick down to
     * one real HTTP request, without meaningfully lagging behind the (much longer) polling interval.
     */
    private static final long SYSTEM_STATUS_CACHE_TTL_MS = 5000;

    private final Logger logger = LoggerFactory.getLogger(DiagralBridgeHandler.class);

    private final HttpClient httpClient;
    private @Nullable DiagralDiscoveryService discoveryService;
    private @NonNullByDefault({}) DiagralBridgeConfiguration diagralBridgeConfig = null;
    private @Nullable DiagralAuthenticationManager authManager;
    private @Nullable DiagralHttpClient diagralHttpClient;
    private @Nullable ScheduledFuture<?> pollingJob;
    private @Nullable DiagralSystemConfiguration cachedConfiguration;
    private @Nullable DiagralSystemDetails cachedDetails;
    private @Nullable DiagralSystemStatus cachedSystemStatus;
    private long cachedSystemStatusTimestamp;

    public DiagralBridgeHandler(Bridge bridge, HttpClient httpClient) {
        super(bridge);
        this.httpClient = httpClient;
    }

    @Override
    public void initialize() {
        logger.debug("Initializing Diagral bridge handler");

        DiagralBridgeConfiguration config = getConfigAs(DiagralBridgeConfiguration.class);

        // Validate configuration
        if (!config.isValid()) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "Invalid configuration: Check username, password, serialId, and pinCode");
            return;
        }

        // Initialize authentication manager and HTTP client
        DiagralAuthenticationManager manager = new DiagralAuthenticationManager(config.username, config.password,
                config.serialId, config.pinCode);
        authManager = manager;
        diagralHttpClient = new DiagralHttpClient(httpClient, manager);

        // Start authentication and polling in background
        scheduler.execute(() -> {
            try {
                authenticate();
                startPolling(config.refreshInterval);
            } catch (DiagralException e) {
                logger.error("Initialization failed", e);
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, e.getMessage());
            }
        });
    }

    @Override
    public void dispose() {
        logger.debug("Disposing Diagral bridge handler");

        stopPolling();

        DiagralHttpClient client = diagralHttpClient;
        DiagralAuthenticationManager manager = authManager;
        if (client != null && manager != null && manager.isAuthenticated()) {
            // Best-effort, fire-and-forget: delete the API key so it doesn't stay registered against
            // the account indefinitely. Must not block dispose(), so this runs off the calling thread.
            scheduler.execute(() -> {
                try {
                    client.deleteApiKey();
                } catch (DiagralException e) {
                    logger.debug("Failed to delete API key on dispose: {}", e.getMessage());
                }
            });
        }

        diagralHttpClient = null;
        authManager = null;
        cachedConfiguration = null;
        cachedSystemStatus = null;

        super.dispose();
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        // Bridge has no channels to handle
    }

    /**
     * Performs authentication with the Diagral API
     *
     * @throws DiagralException if authentication fails
     */
    private void authenticate() throws DiagralException {
        DiagralHttpClient client = diagralHttpClient;
        if (client == null) {
            throw new DiagralException("HTTP client not initialized");
        }

        try {
            logger.info("Authenticating with Diagral API...");
            client.authenticate();
            updateStatus(ThingStatus.ONLINE);
            logger.info("Bridge online - authentication successful");
        } catch (DiagralAuthenticationException e) {
            logger.error("Authentication failed: {}", e.getMessage());
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "Authentication failed: " + e.getMessage());
            throw e;
        }
    }

    /**
     * Starts the polling job
     *
     * @param intervalSeconds the polling interval in seconds
     */
    private void startPolling(int intervalSeconds) {
        stopPolling();

        logger.debug("Starting polling with interval {} seconds", intervalSeconds);
        pollingJob = scheduler.scheduleWithFixedDelay(this::poll, 0, intervalSeconds, TimeUnit.SECONDS);
    }

    /**
     * Stops the polling job
     */
    private void stopPolling() {
        ScheduledFuture<?> job = pollingJob;
        if (job != null && !job.isCancelled()) {
            job.cancel(true);
            pollingJob = null;
            logger.debug("Polling stopped");
        }
    }

    /**
     * Polls the Diagral API for status updates
     */
    private void poll() {
        DiagralHttpClient client = diagralHttpClient;
        if (client == null) {
            logger.debug("Skipping poll - HTTP client not initialized");
            return;
        }

        try {
            // Get system status - always fresh, refreshing the short-lived cache used by getSystemStatus()
            DiagralSystemStatus status = fetchAndCacheSystemStatus(client);
            logger.trace("System status retrieved: {}", status.status);

            // Ensure bridge stays online
            if (getThing().getStatus() != ThingStatus.ONLINE) {
                updateStatus(ThingStatus.ONLINE);
            }

            refreshChildHandlers();
        } catch (DiagralAuthenticationException e) {
            logger.warn("Authentication lost during polling, attempting re-authentication");
            scheduler.execute(() -> {
                try {
                    authenticate();
                    logger.info("Re-authentication successful");
                } catch (DiagralException ex) {
                    logger.error("Re-authentication failed", ex);
                }
            });
        } catch (DiagralException e) {
            String causeMessage = (e.getCause() == null) ? "No cause found" : e.getCause().getMessage();
            logger.warn("Polling failed: {}, Cause: {}", e.getMessage(), causeMessage);
            // Don't immediately go offline on single poll failure
            // TODO Should go offline after consecutive failures handled by error counter logic
        }
    }

    /**
     * Fetches the current system status from the API and refreshes the short-lived cache used by
     * {@link #getSystemStatus()}.
     *
     * @param client the HTTP client to use
     * @return the freshly fetched system status
     * @throws DiagralException if the request fails
     */
    private DiagralSystemStatus fetchAndCacheSystemStatus(DiagralHttpClient client) throws DiagralException {
        DiagralSystemStatus status = client.getSystemStatus();
        cachedSystemStatus = status;
        cachedSystemStatusTimestamp = System.currentTimeMillis();
        return status;
    }

    /**
     * Notifies child thing handlers that implement {@link DiagralRefreshableHandler} so their channels
     * stay up to date on the polling interval, not only on a manual refresh command.
     */
    private void refreshChildHandlers() {
        for (Thing childThing : getThing().getThings()) {
            ThingHandler handler = childThing.getHandler();
            if (handler instanceof DiagralRefreshableHandler refreshableHandler) {
                try {
                    refreshableHandler.refreshStatus();
                } catch (RuntimeException e) {
                    logger.warn("Failed to refresh child thing {}: {}", childThing.getUID(), e.getMessage());
                }
            }
        }
    }

    /**
     * Inspired by Hue add-on.
     * 
     * TODO: complete this function
     * 
     * @param listener
     * @return
     */
    @Override
    public boolean registerDiscoveryListener(DiagralDiscoveryService listener) {
        if (discoveryService == null) {
            discoveryService = listener;
            // getFullLights().forEach(listener::addLightDiscovery);
            // getFullSensors().forEach(listener::addSensorDiscovery);
            // getFullGroups().forEach(listener::addGroupDiscovery);
            return true;
        }

        return false;
    }

    @Override
    public boolean unregisterDiscoveryListener() {
        if (discoveryService != null) {
            discoveryService = null;
            return true;
        }

        return false;
    }

    // Public methods for child thing handlers

    /**
     * Gets the current system status.
     *
     * <p>
     * Returns a short-lived cached value (see {@link #SYSTEM_STATUS_CACHE_TTL_MS}) when available, so
     * that several child handlers refreshing within the same poll tick don't each trigger their own
     * HTTP call.
     * </p>
     *
     * @return the system status, or null if not available
     */
    public @Nullable DiagralSystemStatus getSystemStatus() {
        DiagralSystemStatus cached = cachedSystemStatus;
        if (cached != null && System.currentTimeMillis() - cachedSystemStatusTimestamp < SYSTEM_STATUS_CACHE_TTL_MS) {
            return cached;
        }

        DiagralHttpClient client = diagralHttpClient;
        if (client == null) {
            return null;
        }

        try {
            return fetchAndCacheSystemStatus(client);
        } catch (DiagralException e) {
            logger.warn("Failed to get system status: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Gets the anomalies currently reported for the system.
     *
     * @return the anomalies, or null if not available
     */
    public @Nullable DiagralAnomalies getAnomalies() {
        DiagralHttpClient client = diagralHttpClient;
        if (client == null) {
            return null;
        }

        try {
            return client.getAnomalies();
        } catch (DiagralException e) {
            logger.warn("Failed to get anomalies: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Gets the system configuration (cached)
     *
     * @return the system configuration, or null if not available
     */
    public @Nullable DiagralSystemConfiguration getSystemConfiguration() {
        if (cachedConfiguration != null) {
            return cachedConfiguration;
        }

        DiagralHttpClient client = diagralHttpClient;
        if (client == null) {
            return null;
        }

        try {
            cachedConfiguration = client.getSystemConfiguration();
            return cachedConfiguration;
        } catch (DiagralException e) {
            logger.warn("Failed to get system configuration: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Gets the system details (cached)
     *
     * @return the system details, or null if not available
     */
    public @Nullable DiagralSystemDetails getSystemDetails() {
        if (cachedDetails != null) {
            return cachedDetails;
        }

        DiagralHttpClient client = diagralHttpClient;
        if (client == null) {
            return null;
        }

        try {
            cachedDetails = client.getSystemDetails();
            return cachedDetails;
        } catch (DiagralException e) {
            logger.warn("Failed to get system details: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Sets the alarm system mode
     *
     * @param mode the mode to set (OFF, FULL, PRESENCE, PARTIAL1, PARTIAL2)
     */
    public void setSystemMode(String mode) {
        DiagralHttpClient client = diagralHttpClient;
        if (client == null) {
            logger.warn("Cannot set system mode - HTTP client not initialized");
            return;
        }

        try {
            client.setSystemMode(mode);
            // Trigger immediate poll to update status
            scheduler.execute(this::poll);
        } catch (DiagralException e) {
            logger.error("Failed to set system mode to {}: {}", mode, e.getMessage());
        }
    }

    /**
     * Activates a device group
     *
     * @param groupId the group ID to activate
     */
    public void activateGroup(String groupId) {
        DiagralHttpClient client = diagralHttpClient;
        if (client == null) {
            logger.warn("Cannot activate group - HTTP client not initialized");
            return;
        }

        try {
            client.activateGroup(groupId);
            scheduler.execute(this::poll);
        } catch (DiagralException e) {
            logger.error("Failed to activate group {}: {}", groupId, e.getMessage());
        }
    }

    /**
     * Disables a device group
     *
     * @param groupId the group ID to disable
     */
    public void disableGroup(String groupId) {
        DiagralHttpClient client = diagralHttpClient;
        if (client == null) {
            logger.warn("Cannot disable group - HTTP client not initialized");
            return;
        }

        try {
            client.disableGroup(groupId);
            scheduler.execute(this::poll);
        } catch (DiagralException e) {
            logger.error("Failed to disable group {}: {}", groupId, e.getMessage());
        }
    }

    /**
     * Enables (un-inhibits) a device.
     *
     * @param productType the product type (e.g. SENSOR, ALARM, COMMAND, PLUG)
     * @param productId the per-category numeric device index
     */
    public void enableDevice(String productType, int productId) {
        DiagralHttpClient client = diagralHttpClient;
        if (client == null) {
            logger.warn("Cannot enable device - HTTP client not initialized");
            return;
        }

        try {
            client.enableProduct(productType, productId);
        } catch (DiagralException e) {
            logger.error("Failed to enable device {} ({}): {}", productId, productType, e.getMessage());
        } finally {
            // Refresh regardless of outcome: a reported failure may still have actually changed the
            // device's real state (see the known API quirk in README "Known Limitations" - verification
            // against the /anomalies endpoint can be inconclusive if its data hasn't caught up yet), so
            // always re-sync from the live configuration on the next poll rather than risk showing
            // indefinitely stale cached state.
            cachedConfiguration = null;
            scheduler.execute(this::poll);
        }
    }

    /**
     * Disables (inhibits) a device.
     *
     * @param productType the product type (e.g. SENSOR, ALARM, COMMAND, PLUG)
     * @param productId the per-category numeric device index
     */
    public void disableDevice(String productType, int productId) {
        DiagralHttpClient client = diagralHttpClient;
        if (client == null) {
            logger.warn("Cannot disable device - HTTP client not initialized");
            return;
        }

        try {
            client.disableProduct(productType, productId);
        } catch (DiagralException e) {
            logger.error("Failed to disable device {} ({}): {}", productId, productType, e.getMessage());
        } finally {
            // Refresh regardless of outcome: a reported failure may still have actually changed the
            // device's real state (see the known API quirk in README "Known Limitations" - verification
            // against the /anomalies endpoint can be inconclusive if its data hasn't caught up yet), so
            // always re-sync from the live configuration on the next poll rather than risk showing
            // indefinitely stale cached state.
            cachedConfiguration = null;
            scheduler.execute(this::poll);
        }
    }

    /**
     * Refreshes the cached configuration
     */
    public void refreshConfiguration() {
        cachedConfiguration = null;
        getSystemConfiguration();
    }

    @Override
    public Collection<Class<? extends ThingHandlerService>> getServices() {
        return List.of(DiagralDiscoveryService.class);
    }

    @Override
    public Collection<ConfigStatusMessage> getConfigStatus() {
        diagralBridgeConfig = getConfigAs(DiagralBridgeConfiguration.class);
        Collection<ConfigStatusMessage> messages = List.of();

        String username = diagralBridgeConfig.username;
        if (username == null || username.isEmpty()) {
            messages.add(ConfigStatusMessage.Builder.error(CONFIG_USERNAME).withMessageKeySuffix(USERNAME_MISSING)
                    .withArguments(CONFIG_USERNAME).build());
        }

        String password = diagralBridgeConfig.password;
        if (password == null || password.isEmpty()) {
            messages.add(ConfigStatusMessage.Builder.error(CONFIG_PASSWORD).withMessageKeySuffix(PASSWORD_MISSING)
                    .withArguments(CONFIG_PASSWORD).build());
        }

        String pincode = diagralBridgeConfig.pinCode;
        if (pincode == null || pincode.isEmpty()) {
            messages.add(ConfigStatusMessage.Builder.error(CONFIG_PIN_CODE).withMessageKeySuffix(PINCODE_MISSING)
                    .withArguments(CONFIG_PIN_CODE).build());
        }

        String serialid = diagralBridgeConfig.serialId;
        if (serialid == null || serialid.isEmpty()) {
            messages.add(ConfigStatusMessage.Builder.error(CONFIG_SERIAL_ID).withMessageKeySuffix(SERIALID_MISSING)
                    .withArguments(CONFIG_SERIAL_ID).build());
        }

        return messages;
    }
}
