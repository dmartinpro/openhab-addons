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
import org.openhab.binding.diagral.internal.dto.DiagralSystemConfiguration;
import org.openhab.binding.diagral.internal.dto.DiagralSystemDetails;
import org.openhab.binding.diagral.internal.dto.DiagralSystemStatus;
import org.openhab.binding.diagral.internal.exception.DiagralAuthenticationException;
import org.openhab.binding.diagral.internal.exception.DiagralException;
import org.openhab.core.config.core.status.ConfigStatusMessage;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.ConfigStatusBridgeHandler;
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

    private final Logger logger = LoggerFactory.getLogger(DiagralBridgeHandler.class);

    private final HttpClient httpClient;
    private @Nullable DiagralDiscoveryService discoveryService;
    private @NonNullByDefault({}) DiagralBridgeConfiguration diagralBridgeConfig = null;
    private @Nullable DiagralAuthenticationManager authManager;
    private @Nullable DiagralHttpClient diagralHttpClient;
    private @Nullable ScheduledFuture<?> pollingJob;
    private @Nullable DiagralSystemConfiguration cachedConfiguration;
    private @Nullable DiagralSystemDetails cachedDetails;

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

        diagralHttpClient = null;
        authManager = null;
        cachedConfiguration = null;

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
            // Get system status
            DiagralSystemStatus status = client.getSystemStatus();
            logger.trace("System status retrieved: {}", status.status);

            // Notify child things about status update
            // Child thing handlers will call getSystemStatus() to get the latest status

            // Ensure bridge stays online
            if (getThing().getStatus() != ThingStatus.ONLINE) {
                updateStatus(ThingStatus.ONLINE);
            }
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
     * Gets the current system status
     *
     * @return the system status, or null if not available
     */
    public @Nullable DiagralSystemStatus getSystemStatus() {
        DiagralHttpClient client = diagralHttpClient;
        if (client == null) {
            return null;
        }

        try {
            return client.getSystemStatus();
        } catch (DiagralException e) {
            logger.warn("Failed to get system status: {}", e.getMessage());
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
