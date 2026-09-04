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
import static org.openhab.binding.diagral.internal.DiagralBindingConstants.MODE_FULL;
import static org.openhab.binding.diagral.internal.DiagralBindingConstants.MODE_OFF;
import static org.openhab.binding.diagral.internal.DiagralBindingConstants.MODE_PARTIAL1;
import static org.openhab.binding.diagral.internal.DiagralBindingConstants.MODE_PARTIAL2;
import static org.openhab.binding.diagral.internal.DiagralBindingConstants.MODE_PRESENCE;
import static org.openhab.binding.diagral.internal.DiagralBindingConstants.NAMED_SYSTEM_MODES;
import static org.openhab.binding.diagral.internal.DiagralBindingConstants.PASSWORD_MISSING;
import static org.openhab.binding.diagral.internal.DiagralBindingConstants.PINCODE_MISSING;
import static org.openhab.binding.diagral.internal.DiagralBindingConstants.SERIALID_MISSING;
import static org.openhab.binding.diagral.internal.DiagralBindingConstants.USERNAME_MISSING;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.openhab.binding.diagral.internal.DiagralBridgeConfiguration;
import org.openhab.binding.diagral.internal.discovery.DiagralDiscoveryService;
import org.openhab.binding.diagral.internal.dto.DiagralAnomalies;
import org.openhab.binding.diagral.internal.dto.DiagralGroup;
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
 * <p>
 * Owns the {@link DiagralHttpClient}/{@link DiagralAuthenticationManager} pair for one Diagral box, runs
 * the periodic polling loop that keeps the bridge and every child thing up to date (see {@link #poll()}
 * and {@link DiagralRefreshableHandler}), and is the single point child thing handlers go through to
 * talk to the cloud - they never call {@link DiagralHttpClient} directly, only the {@code getXxx()}/
 * {@code setXxx()}/{@code enableDevice()}/etc. methods here, each of which null-tolerantly wraps the
 * underlying HTTP client and logs+swallows failures rather than propagating checked exceptions to
 * handler code. Also implements {@link DiagralClient} to let {@code DiagralDiscoveryService} register
 * itself, and {@link org.openhab.core.thing.binding.ConfigStatusBridgeHandler} to report missing bridge
 * configuration fields in the UI.
 * </p>
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
    private @Nullable ScheduledFuture<?> authRetryJob;
    private @Nullable DiagralSystemConfiguration cachedConfiguration;
    private @Nullable DiagralSystemDetails cachedDetails;
    private @Nullable DiagralSystemStatus cachedSystemStatus;
    private long cachedSystemStatusTimestamp;

    /**
     * Best-effort, locally-tracked set of group IDs believed active while the real API's {@code /status}
     * reports a transitional status this binding can't otherwise interpret (e.g. {@code TEMPO_GROUP},
     * {@code TEMPO_2} - see {@link #isGroupActive(String)}).
     *
     * <p>
     * Exists to work around a real API gap confirmed live (2026-09-03): the {@code activated_groups} field
     * in the {@code /status} response was observed to stay empty across every status this binding has ever
     * seen - including a fully-settled, named mode like {@code PRESENCE} - so it cannot be trusted at all,
     * for any status. {@link #isGroupActive(String)} instead derives group membership from this bridge's
     * own cached {@link DiagralSystemConfiguration} (each mode's static {@code presenceGroup}/{@code
     * partialGroup1}/{@code partialGroup2}/all-groups membership) whenever {@code status} is one of the
     * five named modes - re-derived fresh on every poll, so it self-corrects after a restart or after a
     * mode change made outside openHAB (e.g. the official e-ONE app). This set is only consulted as a
     * fallback for the remaining case: a transitional status ({@code TEMPO_*}) where the real, final group
     * membership isn't yet knowable from configuration alone. It's kept up to date by {@link
     * #setSystemMode(String)} (optimistically, to the target mode's membership, immediately on command
     * success - covers the exit-delay window before the poll sees the final named mode) and by {@link
     * #activateGroup(String)}/{@link #disableGroup(String)} (a single group at a time, for direct
     * activation outside any mode). Being local/optimistic state, it resets on bridge restart and won't see
     * a change made outside this binding until the next named-mode poll re-derives from configuration.
     * </p>
     */
    private final Set<String> activeGroupIds = ConcurrentHashMap.newKeySet();

    /**
     * Best-effort record of the whole-system mode to display on {@code DiagralSystemHandler}'s {@code
     * mode-control} channel (see {@link #getDisplayedMode()}), so that channel reflects the current mode
     * instead of staying permanently {@code NULL} the way a pure command channel otherwise would.
     *
     * <p>
     * Deliberately shows only one of the five named modes, never a transitional {@code TEMPO_*} value - by
     * design (2026-09-03): during a transition, {@code mode-control} holds whichever named mode was last
     * selected/observed rather than a raw undocumented status string. Kept current the same way as {@link
     * #activeGroupIds}: {@link #setSystemMode(String)} sets it optimistically, immediately on command
     * success (never on a failed/timed-out call, so a command that didn't actually apply never claims to);
     * {@link #getDisplayedMode()} also refreshes it opportunistically whenever the real status happens to be
     * a named mode. Being local/optimistic state, it resets on bridge restart and won't see a mode change
     * made outside this binding (e.g. the official e-ONE app) until the next poll lands on a named mode.
     * </p>
     */
    private @Nullable String lastKnownMode;

    /**
     * Constructs a new bridge handler.
     *
     * @param bridge the bridge thing to handle
     * @param httpClient the shared Jetty HTTP client to use for all requests (obtained from openHAB's
     *            {@code HttpClientFactory} by {@code DiagralHandlerFactory})
     */
    public DiagralBridgeHandler(Bridge bridge, HttpClient httpClient) {
        super(bridge);
        this.httpClient = httpClient;
    }

    /**
     * Validates the bridge configuration and, if valid, kicks off authentication and polling.
     *
     * <p>
     * Per the openHAB threading guideline, this returns immediately - the actual network calls
     * (authentication, first poll) are pushed onto {@code scheduler} rather than run inline, since
     * {@code initialize()} must not block.
     * </p>
     */
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
        scheduler.execute(() -> attemptInitialAuthentication(config));
    }

    /**
     * Attempts the first authentication and, on success, starts polling; on failure, schedules a retry of
     * this same method after {@code config.refreshInterval} seconds rather than giving up.
     *
     * <p>
     * Live-verified (2026-09-03) that a transient network failure during startup - the same kind of
     * flakiness {@link #poll()} already tolerates via its re-authentication path - previously left the
     * bridge permanently {@code OFFLINE} until manually reinitialized, since {@link #startPolling(int)}
     * (and with it, {@code poll()}'s own resilience) was never reached. This method closes that gap by
     * retrying itself on the same cadence as regular polling, instead of requiring manual intervention for
     * what's usually just a temporary connectivity blip.
     * </p>
     *
     * @param config the validated bridge configuration (carries the retry/poll interval)
     */
    private void attemptInitialAuthentication(DiagralBridgeConfiguration config) {
        try {
            authenticate();
            startPolling(config.refreshInterval);
        } catch (DiagralException e) {
            logger.warn("Initial authentication failed, will retry in {}s: {}", config.refreshInterval, e.getMessage());
            authRetryJob = scheduler.schedule(() -> attemptInitialAuthentication(config), config.refreshInterval,
                    TimeUnit.SECONDS);
        }
    }

    /**
     * Stops polling, cancels any pending initial-authentication retry (see {@link
     * #attemptInitialAuthentication(DiagralBridgeConfiguration)}), best-effort deletes the current API key
     * from the Diagral account, and releases all held state.
     *
     * <p>
     * The API-key deletion is fire-and-forget on {@code scheduler} (see the inline comment below) so
     * this method itself stays non-blocking, per the openHAB threading guideline.
     * </p>
     */
    @Override
    public void dispose() {
        logger.debug("Disposing Diagral bridge handler");

        stopPolling();

        ScheduledFuture<?> retryJob = authRetryJob;
        if (retryJob != null && !retryJob.isCancelled()) {
            retryJob.cancel(true);
            authRetryJob = null;
        }

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

    /**
     * Handles a command sent to one of the bridge's own channels.
     *
     * <p>
     * A no-op: the {@code bridge} thing type declares no channels of its own (see {@code
     * thing-types.xml}) - all commands are handled by child things instead.
     * </p>
     *
     * @param channelUID the channel the command was sent to
     * @param command the command
     */
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
     * Polls the Diagral API for status updates.
     *
     * <p>
     * Runs on {@code scheduler} at the configured {@code refreshInterval} (see {@link
     * #startPolling(int)}), and is also triggered immediately, off-cycle, after any command that
     * changes device/system state (e.g. {@link #setSystemMode}, {@link #enableDevice}) so the UI
     * reflects the change promptly rather than waiting for the next scheduled tick. On success, keeps
     * the bridge {@code ONLINE} and calls {@link #refreshChildHandlers()}. On an authentication failure,
     * schedules a re-authentication attempt rather than going offline immediately. On any other failure,
     * logs a warning and leaves the bridge status untouched - a single failed poll doesn't flip the
     * bridge offline (see the {@code TODO} below for the known gap: there's currently no
     * consecutive-failure counter to eventually do so).
     * </p>
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
     * Registers the discovery service so it can be notified of already-known devices.
     *
     * <p>
     * Pattern inspired by the Hue binding's bridge/discovery-service registration. Only one discovery
     * service can be registered at a time (a new one is rejected while another is already registered).
     * </p>
     *
     * <p>
     * <b>Known gap (see {@code CLAUDE.md} "Known incomplete areas"):</b> this currently only stores the
     * listener reference - it doesn't push already-discovered devices to it immediately (the commented-
     * out calls below are a placeholder for that). In practice this isn't a functional problem because
     * {@code DiagralDiscoveryService.startScan()} always re-reads the full configuration itself rather
     * than relying on a push from here.
     * </p>
     *
     * @param listener the discovery service to register
     * @return {@code true} if the listener was registered, {@code false} if another listener was already
     *         registered
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

    /**
     * Unregisters the currently-registered discovery service, if any.
     *
     * @return {@code true} if a listener was registered and has now been removed, {@code false} if none
     *         was registered
     */
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
     * <p>
     * Unlike {@link #getSystemConfiguration()}/{@link #getSystemDetails()}, this is never cached - it's
     * fetched fresh from {@link DiagralHttpClient#getAnomalies()} on every call, since anomaly state is
     * live rather than static configuration. Used by {@code DiagralSystemHandler} to drive the
     * {@code anomaly-count}/{@code anomalies-present} channels, and internally by {@link
     * DiagralHttpClient#actionProduct} to verify whether an enable/disable action that returned an
     * error actually took effect.
     * </p>
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
     * Gets the system configuration (cached).
     *
     * <p>
     * The configuration (device lists, groups, etc.) rarely changes, so it's fetched once and cached
     * indefinitely - call {@link #refreshConfiguration()} to force a fresh fetch, which happens
     * automatically after a successful (or possibly-successful) {@link #enableDevice}/{@link
     * #disableDevice} call. This is what every thing handler's {@code refreshStatus()} reads its device
     * data from.
     * </p>
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
     * Gets the system details (cached).
     *
     * <p>
     * Cached indefinitely once fetched (no explicit refresh method exists for this one, unlike {@link
     * #getSystemConfiguration()}, since these fields - firmware version, IP address, etc. - change even
     * less often). Used only by {@code DiagralDiscoveryService.discoverAlarmSystem()} to populate
     * discovery-time thing properties.
     * </p>
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
     * Sets the alarm system mode.
     *
     * <p>
     * Called by {@code DiagralSystemHandler} in response to a command on the {@code mode-control}
     * channel. Failures are logged and swallowed rather than propagated - there's no channel to report
     * a command failure back through, so a warning/error in the log is the only feedback.
     * </p>
     *
     * @param mode one of the five named modes (OFF, FULL, PRESENCE, PARTIAL1, PARTIAL2) - {@link
     *            DiagralHttpClient#setSystemMode(String)} rejects anything else before this method's
     *            optimistic updates below are reached, so by the time they run {@code mode} is known valid
     */
    public void setSystemMode(String mode) {
        DiagralHttpClient client = diagralHttpClient;
        if (client == null) {
            logger.warn("Cannot set system mode - HTTP client not initialized");
            return;
        }

        try {
            client.setSystemMode(mode);
            // Optimistically set the tracked active-group set to this mode's target membership right away
            // - covers the transitional status (e.g. TEMPO_2) the real system reports for up to a group's
            // outputDelay seconds before /status settles on the final named mode, at which point
            // isGroupActive() switches back to deriving straight from configuration anyway. Only done on
            // confirmed success - see the finally block below for the ambiguous (timeout/error) case.
            Set<String> targetMembers = groupsForMode(mode);
            if (targetMembers != null) {
                activeGroupIds.clear();
                activeGroupIds.addAll(targetMembers);
            }
            // Same reasoning, for the mode-control channel's own displayed value (see getDisplayedMode()):
            // show the just-selected mode immediately rather than leaving it stuck on whatever was shown
            // before this command, for the same transitional window described above.
            lastKnownMode = mode;
        } catch (DiagralException e) {
            logger.error("Failed to set system mode to {}: {}", mode, e.getMessage());
        } finally {
            // Live-verified (2026-09-03): a mode command can time out client-side while still having been
            // applied server-side (the Diagral cloud API is prone to slow/dropped responses under load).
            // Always re-poll regardless of outcome so the UI re-syncs to the real state within one poll
            // cycle instead of staying stale - possibly showing a stale ARMED status - until the next
            // scheduled interval. Mirrors the same fix already applied to enableDevice()/disableDevice().
            scheduler.execute(this::poll);
        }
    }

    /**
     * Activates a device group.
     *
     * <p>
     * Called by {@code DiagralGroupHandler} in response to an {@code ON} command on the group's
     * {@code active} channel. On success, also records {@code groupId} in {@link #activeGroupIds} - see
     * that field's Javadoc for why.
     * </p>
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
            activeGroupIds.add(groupId);
        } catch (DiagralException e) {
            logger.error("Failed to activate group {}: {}", groupId, e.getMessage());
        } finally {
            // Always re-poll regardless of outcome - see setSystemMode()'s finally block for why.
            scheduler.execute(this::poll);
        }
    }

    /**
     * Disables a device group.
     *
     * <p>
     * Called by {@code DiagralGroupHandler} in response to an {@code OFF} command on the group's
     * {@code active} channel. On success, also removes {@code groupId} from {@link #activeGroupIds} - see
     * that field's Javadoc for why.
     * </p>
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
            activeGroupIds.remove(groupId);
        } catch (DiagralException e) {
            logger.error("Failed to disable group {}: {}", groupId, e.getMessage());
        } finally {
            // Always re-poll regardless of outcome - see setSystemMode()'s finally block for why.
            scheduler.execute(this::poll);
        }
    }

    /**
     * Reports whether {@code groupId} is currently active.
     *
     * <p>
     * Called by {@code DiagralGroupHandler} to drive a group thing's {@code active} channel. Confirmed live
     * (2026-09-03) that the real API's {@code activated_groups} field cannot be trusted for this under any
     * status, so this derives the answer itself: while {@code status} is one of the five named modes, it's
     * computed fresh from this mode's static group membership in the cached {@link
     * DiagralSystemConfiguration} (self-correcting every poll, regardless of how the mode was set);
     * otherwise (a transitional {@code TEMPO_*} status) it falls back to {@link #activeGroupIds}, this
     * bridge's own best-effort record of the last group action it issued - see that field's Javadoc.
     * </p>
     *
     * @param groupId the group ID to check
     * @return {@code true} if this group is currently believed active
     */
    public boolean isGroupActive(String groupId) {
        DiagralSystemStatus status = getSystemStatus();
        String mode = status == null ? null : status.status;
        if (mode != null && NAMED_SYSTEM_MODES.contains(mode)) {
            Set<String> members = groupsForMode(mode);
            return members != null && members.contains(groupId);
        }
        return activeGroupIds.contains(groupId);
    }

    /**
     * Reports which of the five named modes should currently be displayed on {@code
     * DiagralSystemHandler}'s {@code mode-control} channel.
     *
     * <p>
     * Called by {@code DiagralSystemHandler} to drive that channel's state. By design (2026-09-03), this
     * only ever returns one of the five named modes, never a raw transitional {@code TEMPO_*} status
     * string - mirrors {@link #isGroupActive(String)}'s derivation shape: while the real status is one of
     * the five named modes, that's authoritative (and this opportunistically refreshes {@link
     * #lastKnownMode} to match, so the fallback below stays honest); otherwise (a transitional status) it
     * falls back to {@link #lastKnownMode} - the last named mode this bridge selected or observed, per that
     * field's Javadoc.
     * </p>
     *
     * @return the mode to display, or {@code null} if none is known yet (e.g. before the first successful
     *         poll or command)
     */
    public @Nullable String getDisplayedMode() {
        DiagralSystemStatus status = getSystemStatus();
        String mode = status == null ? null : status.status;
        if (mode != null && NAMED_SYSTEM_MODES.contains(mode)) {
            lastKnownMode = mode;
            return mode;
        }
        return lastKnownMode;
    }

    /**
     * Computes the set of group IDs a given whole-system mode arms, from the cached system configuration's
     * static per-mode membership lists.
     *
     * @param mode one of the five named modes ({@link
     *            org.openhab.binding.diagral.internal.DiagralBindingConstants#NAMED_SYSTEM_MODES})
     * @return the member group IDs for {@code mode}, or {@code null} if the system configuration isn't
     *         cached yet (too early to tell) or {@code mode} isn't a recognized named mode
     */
    private @Nullable Set<String> groupsForMode(String mode) {
        DiagralSystemConfiguration config = getSystemConfiguration();
        if (config == null) {
            return null;
        }
        switch (mode) {
            case MODE_OFF:
                return Set.of();
            case MODE_FULL:
                List<DiagralGroup> groups = config.groups;
                return groups == null ? Set.of()
                        : groups.stream().map(g -> String.valueOf(g.index)).collect(Collectors.toSet());
            case MODE_PRESENCE:
                return toGroupIdSet(config.presenceGroup);
            case MODE_PARTIAL1:
                return toGroupIdSet(config.partialGroup1);
            case MODE_PARTIAL2:
                return toGroupIdSet(config.partialGroup2);
            default:
                return null;
        }
    }

    /**
     * Converts a list of numeric group indices (as parsed from the system configuration) to the string
     * group-ID form used everywhere else in this binding (thing config, {@link #activeGroupIds}, etc.).
     *
     * @param indices the group indices, or {@code null} if that mode has no configured members
     * @return the group IDs as strings, or an empty set if {@code indices} is {@code null}
     */
    private static Set<String> toGroupIdSet(@Nullable List<Integer> indices) {
        return indices == null ? Set.of() : indices.stream().map(String::valueOf).collect(Collectors.toSet());
    }

    /**
     * Enables (un-inhibits) a device.
     *
     * <p>
     * Called by {@code DiagralSensorHandler} (and its siren/keypad/plug subclasses) in response to an
     * {@code ON} command on the device's {@code enabled} channel.
     * </p>
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
     * <p>
     * Called by {@code DiagralSensorHandler} (and its siren/keypad/plug subclasses) in response to an
     * {@code OFF} command on the device's {@code enabled} channel.
     * </p>
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
     * Forces a fresh fetch of the system configuration on the next call to {@link
     * #getSystemConfiguration()}, discarding whatever is currently cached.
     *
     * <p>
     * Invalidates the cache and immediately re-fetches (synchronously, on the calling thread) so the
     * cache is warm again by the time this method returns.
     * </p>
     */
    public void refreshConfiguration() {
        cachedConfiguration = null;
        getSystemConfiguration();
    }

    /**
     * Advertises the OSGi services this bridge handler contributes.
     *
     * @return a collection containing just {@link DiagralDiscoveryService}, so the openHAB framework
     *         registers it as this bridge's discovery service
     */
    @Override
    public Collection<Class<? extends ThingHandlerService>> getServices() {
        return List.of(DiagralDiscoveryService.class);
    }

    /**
     * Reports configuration problems with the bridge's own thing configuration, so the openHAB UI can
     * surface them (via {@code i18n} message keys such as {@link
     * org.openhab.binding.diagral.internal.DiagralBindingConstants#USERNAME_MISSING}) even before the
     * bridge has attempted to connect.
     *
     * @return one {@link ConfigStatusMessage} per missing required field (username, password, PIN code,
     *         serial ID), or an empty collection if all required fields are present
     */
    @Override
    public Collection<ConfigStatusMessage> getConfigStatus() {
        diagralBridgeConfig = getConfigAs(DiagralBridgeConfiguration.class);
        // Must be mutable - List.of() would throw UnsupportedOperationException on the very first add()
        // below, which is exactly the case (a genuinely missing field) this method exists to report.
        Collection<ConfigStatusMessage> messages = new ArrayList<>();

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
