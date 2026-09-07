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
import static org.openhab.binding.diagral.internal.DiagralBindingConstants.SYSTEM_STATUS_GROUP;
import static org.openhab.binding.diagral.internal.DiagralBindingConstants.USERNAME_MISSING;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.http.HttpStatus;
import org.openhab.binding.diagral.internal.DiagralBridgeConfiguration;
import org.openhab.binding.diagral.internal.discovery.DiagralDiscoveryService;
import org.openhab.binding.diagral.internal.dto.DiagralAnomalies;
import org.openhab.binding.diagral.internal.dto.DiagralGroup;
import org.openhab.binding.diagral.internal.dto.DiagralSystemConfiguration;
import org.openhab.binding.diagral.internal.dto.DiagralSystemDetails;
import org.openhab.binding.diagral.internal.dto.DiagralSystemStatus;
import org.openhab.binding.diagral.internal.exception.DiagralApiException;
import org.openhab.binding.diagral.internal.exception.DiagralAuthenticationException;
import org.openhab.binding.diagral.internal.exception.DiagralException;
import org.openhab.binding.diagral.internal.handler.DiagralRefreshableHandler;
import org.openhab.core.config.core.status.ConfigStatusMessage;
import org.openhab.core.config.discovery.ScanListener;
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

    /**
     * How many consecutive plain (non-authentication) poll failures {@link #poll()} tolerates before
     * flipping the bridge {@code OFFLINE}, rather than staying silently {@code ONLINE} through a sustained
     * outage forever. Chosen to comfortably ride out the API's normal transient flakiness (this bundle's
     * live testing has repeatedly seen individual timeouts/{@code EOFException}s self-heal within 1-3
     * poll cycles) while still surfacing a genuinely sustained failure within a reasonable number of
     * cycles, proportional to whatever {@code refreshInterval} the user configured.
     */
    private static final int MAX_CONSECUTIVE_POLL_FAILURES = 5;

    /**
     * How long a fetched system configuration is reused before it is re-fetched.
     *
     * <p>
     * The configuration endpoint carries every device's live {@code inhibited} flag and per-device
     * {@code anomalies} map - which is what drives each device thing's {@code enabled} and {@code
     * low-battery} channels, and the alarm system's {@code central-low-battery}. It used to be cached
     * indefinitely (invalidated only by this binding's own enable/disable calls), so a device inhibited
     * from the official e-ONE app, or a battery that went flat, was never reflected until openHAB
     * restarted. For an alarm system a permanently stale low-battery indicator is the wrong failure mode,
     * hence a bounded lifetime.
     * </p>
     *
     * <p>
     * Deliberately much longer than the poll interval: this response is by far the largest the API
     * returns (every sensor, siren, keypad, transmitter, camera and group), so re-fetching it on every
     * poll would be wasteful, while device inhibit/battery state does not need second-level freshness.
     * </p>
     */
    private static final long CONFIGURATION_CACHE_TTL_MS = 300_000;

    /**
     * Upper bound on how long {@link #poll()} backs off after the API asks it to slow down.
     *
     * <p>
     * Ten minutes: long enough to actually relieve a rate limit or a server-side outage, short enough
     * that recovery is still noticed promptly once the API returns.
     * </p>
     */
    private static final long MAX_BACKOFF_MS = 600_000;

    private final Logger logger = LoggerFactory.getLogger(DiagralBridgeHandler.class);

    private final HttpClient httpClient;

    /**
     * A value fetched from the cloud API together with the moment it was fetched, so a cache entry and
     * its age can only ever be read as one consistent pair.
     *
     * <p>
     * Exists for thread-safety as much as for tidiness: the value and its timestamp used to be two
     * separate non-volatile fields written by the poll thread and read by command threads, which could
     * observe a new value with an old timestamp (or vice versa) and mis-judge freshness. Holding both in
     * one immutable record behind a single {@code volatile} reference makes that impossible.
     * </p>
     *
     * @param <T> the cached value's type
     * @param value the cached value
     * @param timestampMillis when it was fetched, from {@link System#currentTimeMillis()}
     */
    private record Cached<T> (T value, long timestampMillis) {

        /**
         * Reports whether this entry is still within its time-to-live.
         *
         * @param now the current time in milliseconds
         * @param ttlMillis how long an entry stays usable
         * @return {@code true} if the entry may still be served without re-fetching
         */
        boolean isFreshAt(long now, long ttlMillis) {
            return now - timestampMillis < ttlMillis;
        }
    }

    // Every mutable field below is written by whichever thread runs the poll/authentication and read by
    // the framework's command threads, so each is volatile (or an atomic/concurrent type). Nothing here
    // is guarded by a monitor - reads are individually consistent, which is all any of these need.
    private volatile @Nullable DiagralDiscoveryService discoveryService;
    private volatile @Nullable DiagralAuthenticationManager authManager;
    private volatile @Nullable DiagralHttpClient diagralHttpClient;
    private volatile @Nullable ScheduledFuture<?> pollingJob;
    private volatile @Nullable ScheduledFuture<?> authRetryJob;
    private volatile @Nullable Cached<DiagralSystemConfiguration> cachedConfiguration;
    private volatile @Nullable DiagralSystemDetails cachedDetails;
    private volatile @Nullable Cached<DiagralSystemStatus> cachedSystemStatus;
    private final AtomicInteger consecutivePollFailures = new AtomicInteger();

    /**
     * Serialises {@link #poll()}.
     *
     * <p>
     * Polling runs both on the scheduled interval and off-cycle, submitted by every command method so the
     * UI re-syncs promptly - so two polls really can overlap. Unguarded, they raced on the caches above
     * and duplicated every HTTP call. A lock rather than a "skip if busy" flag on purpose: a
     * command-triggered re-poll exists precisely to reflect a state change that just happened, so
     * dropping it would reintroduce exactly the stale-UI problem it was added to fix. Waiting briefly on
     * a scheduler thread is the cheaper trade.
     * </p>
     */
    private final ReentrantLock pollLock = new ReentrantLock();

    /**
     * Makes {@link #getSystemConfiguration()} single-flight: concurrent callers that all find the cache
     * empty or expired issue one HTTP request between them, instead of one each.
     *
     * <p>
     * The method is a check-then-fetch with no atomicity, so every thread that misses the cache used to
     * start its own fetch of the largest response this API returns. Live-observed repeatedly at startup,
     * where all child handlers initialise at once: 4 to 11 duplicate fetches of the same configuration in
     * a single burst, varying only with thread timing. Serialising the fetch means the first caller does
     * the work and the rest return its result.
     * </p>
     *
     * <p>
     * Deliberately separate from {@link #pollLock} - these guard unrelated things, and sharing one lock
     * would make a child handler's cache miss block an entire poll cycle for no reason.
     * </p>
     */
    private final ReentrantLock configurationFetchLock = new ReentrantLock();

    /**
     * Makes {@link #getSystemStatus()} single-flight, for the same reason as
     * {@link #configurationFetchLock}.
     *
     * <p>
     * The short-lived status cache collapses repeat reads only once a fetch has <em>succeeded</em>: a
     * failed fetch caches nothing, so every caller that missed the cache goes to the network on its own.
     * Live-observed right after the bridge came online with the API misbehaving - ten handlers each
     * issued their own status request within 30ms and each waited out its own 10-second timeout. Adding
     * this lock means the first caller makes the request and the rest reuse its outcome, whether that is
     * a value or a failure.
     * </p>
     */
    private final ReentrantLock statusFetchLock = new ReentrantLock();

    /**
     * Set once {@link #dispose()} has run, so work already scheduled - in particular the
     * self-rescheduling {@link #attemptInitialAuthentication(DiagralBridgeConfiguration)} retry - stops
     * instead of running on, and possibly restarting polling, after this handler is gone.
     */
    private volatile boolean disposed;

    /**
     * How many rate-limit/server-error responses have arrived in a row, driving the exponential delay in
     * {@link #applyBackoff(int)}. Reset by any successful poll.
     */
    private final AtomicInteger consecutiveBackoffFailures = new AtomicInteger();

    /**
     * Wall-clock time before which scheduled polling stays quiet - see {@link #scheduledPoll()}.
     *
     * <p>
     * Zero when not backing off. Deliberately a deadline rather than a rescheduled job, so the existing
     * fixed-delay schedule is left intact and only individual ticks are skipped.
     * </p>
     */
    private volatile long backoffUntilMillis;

    /** The configured poll interval, kept so the backoff can be expressed as a multiple of it. */
    private volatile int refreshIntervalSeconds = 60;

    /**
     * Best-effort, locally-tracked set of group IDs believed active while the real API's {@code /status}
     * reports the transitional {@code TEMPO_GROUP} status (or any other status this binding can't
     * otherwise interpret) - see {@link #isGroupActive(String)}.
     *
     * <p>
     * Exists to work around a real API gap: {@code activated_groups} in the {@code /status} response is
     * empty for every named mode and for the transitional {@code TEMPO_GROUP} status (confirmed live
     * 2026-09-03/04), so it cannot be trusted during those. {@link #isGroupActive(String)} instead derives
     * group membership from this bridge's own cached {@link DiagralSystemConfiguration} while {@code
     * status} is one of the five named modes, and directly from {@code activated_groups} while {@code
     * status} is the settled {@code GROUP} status (confirmed live 2026-09-04 to be reliably populated
     * there, unlike everywhere else) - both self-correcting every poll, regardless of how the state was
     * set. This set is only consulted as a fallback for what's left: the transitional {@code TEMPO_GROUP}
     * status (or any other unrecognized one), where the real, final group membership isn't yet knowable
     * from either source. It's kept up to date by {@link #setSystemMode(String)} (optimistically, to the
     * target mode's membership, immediately on command success - covers the exit-delay window before the
     * poll sees the final named mode), by {@link #activateGroup(String)}/{@link #disableGroup(String)} (a
     * single group at a time, for direct activation outside any mode), and opportunistically by {@link
     * #isGroupActive(String)} itself whenever it gets an authoritative {@code GROUP}-status answer - so
     * this fallback stays honest for a later {@code TEMPO_GROUP} read even when the settling was observed
     * rather than commanded. Still, being local/optimistic state, it resets on bridge restart and won't
     * see a change made outside this binding until the next poll that lands on a named mode or {@code
     * GROUP}.
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
    private volatile @Nullable String lastKnownMode;

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

        // Reset the per-lifecycle state. This matters because the framework reuses the handler instance:
        // BaseThingHandler.thingUpdated() calls dispose() then initialize() on this same object whenever
        // the thing's configuration is edited, so leaving `disposed` set from the previous cycle would
        // make attemptInitialAuthentication() and poll() return immediately forever, and the bridge would
        // never come back online after a config change.
        disposed = false;
        consecutivePollFailures.set(0);
        consecutiveBackoffFailures.set(0);
        backoffUntilMillis = 0;

        DiagralBridgeConfiguration config = getConfigAs(DiagralBridgeConfiguration.class);

        // Validate configuration. Reported separately so the message points at the actual problem.
        if (!config.hasCredentials()) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "Invalid configuration: Check username, password, serialId, and pinCode");
            return;
        }

        if (!config.isRefreshIntervalValid()) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "Invalid configuration: refreshInterval must be between "
                            + DiagralBridgeConfiguration.MIN_REFRESH_INTERVAL_SECONDS + " and "
                            + DiagralBridgeConfiguration.MAX_REFRESH_INTERVAL_SECONDS + " seconds");
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
        if (disposed) {
            return;
        }

        try {
            authenticate();
            if (disposed) {
                // dispose() ran while authentication was in flight; don't start polling a dead handler.
                return;
            }
            startPolling(config.refreshInterval);
        } catch (DiagralException e) {
            if (disposed) {
                return;
            }
            logger.warn("Initial authentication failed, will retry in {}s: {}", config.refreshInterval, e.getMessage());
            ScheduledFuture<?> retry = scheduler.schedule(() -> attemptInitialAuthentication(config),
                    config.refreshInterval, TimeUnit.SECONDS);
            authRetryJob = retry;
            // Re-check after publishing the job: had dispose() run between the check above and here, it
            // would have cancelled the previous job and missed this one, leaving it to retry forever on a
            // disposed handler.
            if (disposed) {
                retry.cancel(false);
            }
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

        // Set before cancelling anything: this is what stops the self-rescheduling initial-authentication
        // retry from queueing another attempt after the cancellation below has already run.
        disposed = true;

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
            // warn, not error: the usual cause is a wrong or expired credential, which is a user
            // configuration problem rather than a fault in the binding. The ThingStatus detail set below
            // is what actually surfaces it to the user.
            logger.warn("Authentication failed: {}", e.getMessage());
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
        refreshIntervalSeconds = intervalSeconds;
        pollingJob = scheduler.scheduleWithFixedDelay(this::scheduledPoll, 0, intervalSeconds, TimeUnit.SECONDS);
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
     * reflects the change promptly rather than waiting for the next scheduled tick. On success, resets
     * {@link #consecutivePollFailures}, keeps the bridge {@code ONLINE}, and calls {@link
     * #refreshChildHandlers}. On an authentication failure, schedules a re-authentication attempt
     * rather than going offline immediately (this doesn't touch {@link #consecutivePollFailures}, which
     * only tracks plain failures - see that field's Javadoc). On any other failure, logs a warning and
     * increments {@link #consecutivePollFailures}; a single failed poll still doesn't flip the bridge
     * offline (this API's transient flakiness usually self-heals within 1-3 cycles), but reaching {@link
     * #MAX_CONSECUTIVE_POLL_FAILURES} in a row does, so a genuinely sustained outage doesn't leave the
     * bridge silently {@code ONLINE} forever.
     * </p>
     */
    /**
     * The scheduled polling tick: honours any active backoff, then polls.
     *
     * <p>
     * Only the scheduled path checks the backoff. A poll submitted by a command runs regardless, because
     * that one exists to reflect a change the user just made - suppressing it would leave the UI stale
     * for exactly the reason the immediate re-poll was introduced to prevent, and it is a single request
     * rather than a repeating load.
     * </p>
     */
    private void scheduledPoll() {
        long backoffUntil = backoffUntilMillis;
        if (backoffUntil > 0) {
            long remainingMillis = backoffUntil - System.currentTimeMillis();
            if (remainingMillis > 0) {
                logger.debug("Skipping scheduled poll - backing off for another {}s", remainingMillis / 1000);
                return;
            }
        }
        poll();
    }

    private void poll() {
        if (disposed) {
            return;
        }

        // Serialise against any other in-flight poll - see pollLock's Javadoc.
        pollLock.lock();
        try {
            pollOnce();
        } finally {
            pollLock.unlock();
        }
    }

    /**
     * Performs one poll cycle. Only ever called by {@link #poll()}, which holds {@link #pollLock} for the
     * duration, so this method can assume it is the only poll running.
     */
    private void pollOnce() {
        DiagralHttpClient client = diagralHttpClient;
        if (client == null) {
            logger.debug("Skipping poll - HTTP client not initialized");
            return;
        }

        try {
            // Get system status - always fresh, refreshing the short-lived cache used by getSystemStatus()
            DiagralSystemStatus status = fetchAndCacheSystemStatus(client);
            logger.trace("System status retrieved: {}", status.status);
            consecutivePollFailures.set(0);
            clearBackoff();

            // Ensure bridge stays online
            if (getThing().getStatus() != ThingStatus.ONLINE) {
                updateStatus(ThingStatus.ONLINE);
            }

            refreshChildHandlers(captureSnapshot(status));
        } catch (DiagralAuthenticationException e) {
            logger.warn("Authentication lost during polling, attempting re-authentication");
            scheduler.execute(() -> {
                try {
                    authenticate();
                    logger.info("Re-authentication successful");
                } catch (DiagralException ex) {
                    // No stack trace: an expected user/config/network failure, per the logging guideline.
                    logger.warn("Re-authentication failed: {}", ex.getMessage());
                }
            });
        } catch (DiagralException e) {
            String causeMessage = (e.getCause() == null) ? "No cause found" : e.getCause().getMessage();
            logger.warn("Polling failed: {}, Cause: {}", e.getMessage(), causeMessage);
            // Don't go offline on an isolated failure - this API's transient flakiness usually self-heals
            // within 1-3 cycles - but a sustained outage shouldn't leave the bridge silently ONLINE
            // forever either, so flip OFFLINE once too many consecutive failures pile up.
            if (e instanceof DiagralApiException apiException && isBackoffWorthy(apiException.getStatusCode())) {
                applyBackoff(apiException.getStatusCode());
            }
            int failures = consecutivePollFailures.incrementAndGet();
            if (failures >= MAX_CONSECUTIVE_POLL_FAILURES) {
                logger.warn("Bridge going OFFLINE after {} consecutive poll failures", failures);
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                        failures + " consecutive poll failures - last error: " + e.getMessage());
            }
        }
    }

    /**
     * Reports whether an HTTP status means "stop hammering me" rather than "this one request failed".
     *
     * @param statusCode the status code the API returned
     * @return {@code true} for 429 and any 5xx, the two cases where retrying at the normal cadence makes
     *         things worse rather than better
     */
    private static boolean isBackoffWorthy(int statusCode) {
        return statusCode == HttpStatus.TOO_MANY_REQUESTS_429 || statusCode >= 500;
    }

    /**
     * Backs scheduled polling off exponentially after a rate-limit or server-error response.
     *
     * <p>
     * The delay doubles per consecutive occurrence, starting from the configured poll interval and capped
     * at {@link #MAX_BACKOFF_MS}. Only affects the scheduled path - see {@link #scheduledPoll()}.
     * </p>
     *
     * @param statusCode the status code that triggered the backoff, for the log line
     */
    private void applyBackoff(int statusCode) {
        int occurrences = consecutiveBackoffFailures.incrementAndGet();
        // Shift rather than pow, and clamp the exponent so it cannot overflow on a long outage.
        long base = TimeUnit.SECONDS.toMillis(refreshIntervalSeconds);
        long delayMillis = Math.min(MAX_BACKOFF_MS, base << Math.min(occurrences - 1, 20));
        backoffUntilMillis = System.currentTimeMillis() + delayMillis;
        logger.warn("API returned {} - backing off scheduled polling for {}s (occurrence {})", statusCode,
                delayMillis / 1000, occurrences);
    }

    /**
     * Clears any active backoff after a successful poll, so normal cadence resumes immediately rather
     * than waiting out a delay that is no longer warranted.
     */
    private void clearBackoff() {
        if (backoffUntilMillis != 0) {
            logger.info("API is responding again - resuming normal polling cadence");
            backoffUntilMillis = 0;
        }
        consecutiveBackoffFailures.set(0);
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
        cachedSystemStatus = new Cached<>(status, System.currentTimeMillis());
        return status;
    }

    /**
     * Builds the single {@link DiagralPollSnapshot} that one refresh cycle works from.
     *
     * <p>
     * Anomalies are deliberately left unresolved here - see that class's Javadoc for why they are fetched
     * lazily rather than with the rest.
     * </p>
     *
     * @param status the status this cycle should use, or {@code null} if it could not be fetched
     * @return a snapshot every handler in this cycle can share
     */
    private DiagralPollSnapshot captureSnapshot(@Nullable DiagralSystemStatus status) {
        return new DiagralPollSnapshot(status, getSystemConfiguration(), this::getAnomalies);
    }

    /**
     * Builds a snapshot for a caller outside the poll cycle - a thing handler refreshing itself after it
     * or its bridge came online.
     *
     * <p>
     * Reads the status through {@link #getSystemStatus()}, whose short-lived cache is what stops the
     * fourteen-odd handlers that come online together from each issuing their own request.
     * </p>
     *
     * @return a snapshot of the system as currently known
     */
    public DiagralPollSnapshot captureSnapshot() {
        return captureSnapshot(getSystemStatus());
    }

    /**
     * Refreshes every child thing handler that implements {@link DiagralRefreshableHandler} from one
     * shared snapshot, so their channels stay up to date on the polling interval rather than only on a
     * manual refresh command.
     *
     * <p>
     * Handing each handler the same {@link DiagralPollSnapshot} is what replaced the previous approach of
     * re-stamping a cache timestamp before every call to keep a shared value looking fresh. That worked
     * by making the cache lie about its age; passing the value explicitly means there is nothing to lie
     * about, and no way for a slow handler to leave the ones after it reading different data.
     * </p>
     *
     * <p>
     * Calls {@link DiagralRefreshableHandler#refreshStatus(DiagralPollSnapshot)} directly and
     * synchronously, on purpose - see {@code DiagralBaseThingHandler.refreshStatusAsync()} for why only
     * lifecycle callbacks are offloaded and this loop is not.
     * </p>
     *
     * @param snapshot the system state every handler in this cycle should reflect
     */
    private void refreshChildHandlers(DiagralPollSnapshot snapshot) {
        for (Thing childThing : getThing().getThings()) {
            ThingHandler handler = childThing.getHandler();
            if (handler instanceof DiagralRefreshableHandler refreshableHandler) {
                try {
                    refreshableHandler.refreshStatus(snapshot);
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
     * <b>[Fixed 2026-09-04]</b> Previously only stored the listener reference without pushing
     * already-known devices to it (see {@code CLAUDE.md} "Known incomplete areas" for the original gap
     * this closes). Now triggers a catch-up scan on the newly registered listener via its inherited
     * {@code startScan(ScanListener)} - the same public entry point the openHAB framework itself uses -
     * so a listener registering after the bridge already has a populated configuration immediately sees
     * every currently-known device, rather than only ever seeing devices discovered by a future scan.
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
            listener.startScan(new ScanListener() {
                @Override
                public void onFinished() {
                    // No-op: nothing extra needed once this catch-up scan completes - startScan() has
                    // already emitted a discovery result for everything currently known.
                }

                @Override
                public void onErrorOccurred(@Nullable Exception exception) {
                    String message = exception == null ? "unknown error" : exception.getMessage();
                    logger.debug("Catch-up scan for newly registered discovery listener failed: {}", message);
                }
            });
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
     * HTTP call. Since the poll cycle now hands every handler one shared {@link DiagralPollSnapshot},
     * this cache mainly serves callers outside that cycle - notably the handlers that all come online
     * together when the bridge does, each of which captures its own snapshot.
     * </p>
     *
     * @return the current system status, or null if it isn't available
     */
    public @Nullable DiagralSystemStatus getSystemStatus() {
        Cached<DiagralSystemStatus> cached = cachedSystemStatus;
        if (cached != null && cached.isFreshAt(System.currentTimeMillis(), SYSTEM_STATUS_CACHE_TTL_MS)) {
            return cached.value();
        }

        statusFetchLock.lock();
        try {
            // Re-check after acquiring: whoever held the lock has very likely just refreshed the cache.
            cached = cachedSystemStatus;
            if (cached != null && cached.isFreshAt(System.currentTimeMillis(), SYSTEM_STATUS_CACHE_TTL_MS)) {
                return cached.value();
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
        } finally {
            statusFetchLock.unlock();
        }
    }

    /**
     * A call into {@link DiagralHttpClient} that returns a value.
     *
     * @param <T> the value type
     */
    @FunctionalInterface
    private interface ClientQuery<T> {
        /**
         * Performs the call.
         *
         * @param client the HTTP client, guaranteed non-null
         * @return the fetched value
         * @throws DiagralException if the call fails
         */
        T run(DiagralHttpClient client) throws DiagralException;
    }

    /**
     * A call into {@link DiagralHttpClient} that changes system state and returns nothing.
     */
    @FunctionalInterface
    private interface ClientCommand {
        /**
         * Performs the call.
         *
         * @param client the HTTP client, guaranteed non-null
         * @throws DiagralException if the call fails
         */
        void run(DiagralHttpClient client) throws DiagralException;
    }

    /**
     * Runs a read against the cloud API, translating "no client" and any failure into {@code null}.
     *
     * <p>
     * Thing handlers deal in nullable values rather than checked exceptions - this is where that
     * translation happens, once, instead of in each accessor.
     * </p>
     *
     * @param <T> the value type
     * @param description what is being fetched, used in the warning if it fails
     * @param query the call to make
     * @return the fetched value, or {@code null} if the client isn't initialized or the call failed
     */
    private <T> @Nullable T query(String description, ClientQuery<T> query) {
        DiagralHttpClient client = diagralHttpClient;
        if (client == null) {
            return null;
        }

        try {
            return query.run(client);
        } catch (DiagralException e) {
            logger.warn("Failed to get {}: {}", description, e.getMessage());
            return null;
        }
    }

    /**
     * Runs a state-changing command against the cloud API and re-polls afterwards.
     *
     * <p>
     * The re-poll happens in a {@code finally}, regardless of outcome, and that is deliberate:
     * live-verified (2026-09-03) that a command can time out client-side while having been applied
     * server-side, since this API is prone to slow responses. Re-polling either way means the UI
     * re-syncs to reality within one cycle instead of showing a stale armed/disarmed state until the next
     * scheduled interval.
     * </p>
     *
     * @param description what is being done, used in the log messages
     * @param command the call to make, plus any bookkeeping that should only happen on success
     */
    private void command(String description, ClientCommand command) {
        DiagralHttpClient client = diagralHttpClient;
        if (client == null) {
            logger.warn("Cannot {} - HTTP client not initialized", description);
            return;
        }

        try {
            command.run(client);
        } catch (DiagralException e) {
            // warn, not error: a timed-out command is routine on this API and often applied anyway - the
            // re-poll below is what resolves the real state.
            logger.warn("Failed to {}: {}", description, e.getMessage());
        } finally {
            scheduler.execute(this::poll);
        }
    }

    /**
     * Runs a device enable/disable command, additionally discarding the cached system configuration so
     * the follow-up poll re-reads the device's real state.
     *
     * @param description what is being done, used in the log messages
     * @param action the call to make
     */
    private void deviceCommand(String description, ClientCommand action) {
        command(description, client -> {
            try {
                action.run(client);
            } finally {
                // After the call, not before: invalidating first leaves a window in which a concurrent
                // reader can fetch the still-unchanged configuration and cache it as fresh, which the
                // re-poll would then trust. A reported failure may still have changed the device's real
                // state (see the HTTP-500 quirk in README "Known Limitations"), so the cache is discarded
                // either way.
                cachedConfiguration = null;
            }
        });
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
        return query("anomalies", DiagralHttpClient::getAnomalies);
    }

    /**
     * Gets the system configuration (cached).
     *
     * <p>
     * This is what every thing handler's {@code refreshStatus()} reads its device data from. Cached for
     * {@link #CONFIGURATION_CACHE_TTL_MS} - see that constant for why the lifetime is bounded rather than
     * indefinite, and why it is nonetheless much longer than the poll interval. The cache is also
     * invalidated outright after a (possibly-successful) {@link #enableDevice}/{@link #disableDevice}
     * call.
     * </p>
     *
     * <p>
     * If a refresh fails, the expired entry is served rather than {@code null}: slightly stale device
     * data keeps every thing's channels populated, whereas {@code null} makes each handler skip its
     * update entirely. A transient API failure - which this API produces routinely - should not blank the
     * UI.
     * </p>
     *
     * @return the system configuration, or null if none has ever been fetched successfully
     */
    public @Nullable DiagralSystemConfiguration getSystemConfiguration() {
        Cached<DiagralSystemConfiguration> cached = cachedConfiguration;
        if (cached != null && cached.isFreshAt(System.currentTimeMillis(), CONFIGURATION_CACHE_TTL_MS)) {
            return cached.value();
        }

        // Only one caller fetches; the rest wait and then take the result from the cache below.
        configurationFetchLock.lock();
        try {
            // Re-check after acquiring: whoever held the lock has very likely just refreshed it, which is
            // exactly the duplicate fetch this lock exists to avoid.
            cached = cachedConfiguration;
            if (cached != null && cached.isFreshAt(System.currentTimeMillis(), CONFIGURATION_CACHE_TTL_MS)) {
                return cached.value();
            }

            DiagralHttpClient client = diagralHttpClient;
            if (client == null) {
                return cached == null ? null : cached.value();
            }

            try {
                DiagralSystemConfiguration configuration = client.getSystemConfiguration();
                cachedConfiguration = new Cached<>(configuration, System.currentTimeMillis());
                return configuration;
            } catch (DiagralException e) {
                if (cached != null) {
                    logger.debug("Failed to refresh system configuration, serving the previous one: {}",
                            e.getMessage());
                    return cached.value();
                }
                logger.warn("Failed to get system configuration: {}", e.getMessage());
                return null;
            }
        } finally {
            configurationFetchLock.unlock();
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
        DiagralSystemDetails cached = cachedDetails;
        if (cached != null) {
            return cached;
        }

        DiagralSystemDetails fetched = query("system details", DiagralHttpClient::getSystemDetails);
        if (fetched != null) {
            cachedDetails = fetched;
        }
        return fetched;
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
        command("set system mode to " + mode, client -> {
            client.setSystemMode(mode);
            // Optimistically set the tracked active-group set to this mode's target membership right away
            // - covers the transitional status (e.g. TEMPO_2) the real system reports for up to a group's
            // outputDelay seconds before /status settles on the final named mode, at which point
            // isGroupActive() switches back to deriving straight from configuration anyway. Only done on
            // confirmed success - see the finally block below for the ambiguous (timeout/error) case.
            Set<String> targetMembers = groupsForMode(mode, captureSnapshot());
            if (targetMembers != null) {
                activeGroupIds.clear();
                activeGroupIds.addAll(targetMembers);
            }
            // Same reasoning, for the mode-control channel's own displayed value (see getDisplayedMode()):
            // show the just-selected mode immediately rather than leaving it stuck on whatever was shown
            // before this command, for the same transitional window described above.
            lastKnownMode = mode;
        });
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
        command("activate group " + groupId, client -> {
            client.activateGroup(groupId);
            activeGroupIds.add(groupId);
        });
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
        command("disable group " + groupId, client -> {
            client.disableGroup(groupId);
            activeGroupIds.remove(groupId);
        });
    }

    /**
     * Reports whether {@code groupId} is currently active.
     *
     * <p>
     * Called by {@code DiagralGroupHandler} to drive a group thing's {@code active} channel. The real
     * API's {@code activated_groups} field is untrustworthy under most statuses (confirmed live
     * 2026-09-03/04 - empty for every named mode and for the transitional {@link
     * DiagralBindingConstants#MODE_TEMPO_GROUP}), so this derives the answer from whichever source is
     * actually authoritative for the current status, in order:
     * </p>
     * <ol>
     * <li>One of the five named modes ({@link DiagralBindingConstants#NAMED_SYSTEM_MODES}): membership is
     * computed fresh from that mode's static group configuration in the cached {@link
     * DiagralSystemConfiguration} - self-correcting every poll, regardless of how the mode was set.</li>
     * <li>{@link DiagralBindingConstants#SYSTEM_STATUS_GROUP} (added 2026-09-04): unlike every other
     * non-named status, {@code activated_groups} <em>is</em> reliably populated once a directly-activated
     * group has settled here - proven live across three independent, isolated tests (one per group,
     * 2026-09-04), each returning the exact expected list (e.g. {@code activated_groups:[2]} for group 2).
     * Trusted directly, the same way named-mode membership is trusted, and used to opportunistically
     * resync {@link #activeGroupIds} so the fallback below stays honest for a later transitional read.</li>
     * <li>Anything else (the transitional {@link DiagralBindingConstants#MODE_TEMPO_GROUP}, or any other
     * unrecognized status): falls back to {@link #activeGroupIds}, this bridge's own best-effort record of
     * the last group action it issued - see that field's Javadoc. There is currently no way to do better
     * here: {@code activated_groups} gives no per-group detail while still transitional (confirmed live
     * 2026-09-04), so a group armed outside openHAB is unavoidably invisible until it settles to {@code
     * GROUP} or a poll lands on a named mode.</li>
     * </ol>
     *
     * <p>
     * Logs a {@code TRACE}-level line on every call (added 2026-09-04) showing which branch was taken and
     * its inputs - added while troubleshooting group state not reliably reflecting activity triggered
     * outside openHAB (e.g. the official e-ONE app), to make this derivation directly observable rather
     * than only its output. That same troubleshooting is what proved branch 2 above was reachable and
     * correct, but unused, before this method trusted it.
     * </p>
     *
     * @param groupId the group ID to check
     * @param snapshot the shared view of the system this refresh cycle is working from
     * @return {@code true} if this group is currently believed active
     */
    public boolean isGroupActive(String groupId, DiagralPollSnapshot snapshot) {
        DiagralSystemStatus status = snapshot.status();
        String mode = status == null ? null : status.status;
        boolean result;
        if (mode != null && NAMED_SYSTEM_MODES.contains(mode)) {
            Set<String> members = groupsForMode(mode, snapshot);
            result = members != null && members.contains(groupId);
            logger.trace("isGroupActive({}): status={} (named mode), configMembers={}, result={}", groupId, mode,
                    members, result);
        } else if (SYSTEM_STATUS_GROUP.equals(mode) && status != null && status.activatedGroups != null) {
            result = status.activatedGroups.stream().map(String::valueOf).anyMatch(groupId::equals);
            // Opportunistically resync activeGroupIds to this authoritative answer, mirroring how
            // getDisplayedMode() already refreshes lastKnownMode - keeps the transitional-status fallback
            // below honest for the next TEMPO_GROUP-only read, where activated_groups gives no detail.
            if (result) {
                activeGroupIds.add(groupId);
            } else {
                activeGroupIds.remove(groupId);
            }
            logger.trace("isGroupActive({}): status={} (settled group status), activatedGroups={}, result={}", groupId,
                    mode, status.activatedGroups, result);
        } else {
            result = activeGroupIds.contains(groupId);
            logger.trace("isGroupActive({}): status={} (not a named mode), activeGroupIds={}, result={}", groupId, mode,
                    activeGroupIds, result);
        }
        return result;
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
     * <p>
     * Logs a {@code TRACE}-level line on every call (added 2026-09-04) for the same diagnostic reason as
     * {@link #isGroupActive(String)}'s matching trace line.
     * </p>
     *
     * @param snapshot the shared view of the system this refresh cycle is working from
     * @return the mode to display, or {@code null} if none is known yet (e.g. before the first successful
     *         poll or command)
     */
    public @Nullable String getDisplayedMode(DiagralPollSnapshot snapshot) {
        DiagralSystemStatus status = snapshot.status();
        String mode = status == null ? null : status.status;
        if (mode != null && NAMED_SYSTEM_MODES.contains(mode)) {
            lastKnownMode = mode;
            // Diagnostic-only (added 2026-09-04, see isGroupActive()'s matching trace line for why).
            logger.trace("getDisplayedMode(): status={} (named mode), caching and returning as lastKnownMode", mode);
            return mode;
        }
        logger.trace("getDisplayedMode(): status={} (not a named mode), falling back to lastKnownMode={}", mode,
                lastKnownMode);
        return lastKnownMode;
    }

    /**
     * Computes the set of group IDs a given whole-system mode arms, from the cached system configuration's
     * static per-mode membership lists.
     *
     * @param mode one of the five named modes ({@link
     *            org.openhab.binding.diagral.internal.DiagralBindingConstants#NAMED_SYSTEM_MODES})
     * @param snapshot the shared view of the system this refresh cycle is working from
     * @return the member group IDs for {@code mode}, or {@code null} if the system configuration isn't
     *         cached yet (too early to tell) or {@code mode} isn't a recognized named mode
     */
    private @Nullable Set<String> groupsForMode(String mode, DiagralPollSnapshot snapshot) {
        DiagralSystemConfiguration config = snapshot.configuration();
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
        deviceCommand("enable device " + productId + " (" + productType + ")",
                client -> client.enableProduct(productType, productId));
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
        deviceCommand("disable device " + productId + " (" + productType + ")",
                client -> client.disableProduct(productType, productId));
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
        // A local, not a field: this is read-only, computed fresh on every call, and used nowhere else -
        // holding it in shared mutable state would just be one more thing for another thread to observe
        // half-written.
        DiagralBridgeConfiguration config = getConfigAs(DiagralBridgeConfiguration.class);
        // Must be mutable - List.of() would throw UnsupportedOperationException on the very first add()
        // below, which is exactly the case (a genuinely missing field) this method exists to report.
        Collection<ConfigStatusMessage> messages = new ArrayList<>();

        String username = config.username;
        if (username == null || username.isEmpty()) {
            messages.add(ConfigStatusMessage.Builder.error(CONFIG_USERNAME).withMessageKeySuffix(USERNAME_MISSING)
                    .withArguments(CONFIG_USERNAME).build());
        }

        String password = config.password;
        if (password == null || password.isEmpty()) {
            messages.add(ConfigStatusMessage.Builder.error(CONFIG_PASSWORD).withMessageKeySuffix(PASSWORD_MISSING)
                    .withArguments(CONFIG_PASSWORD).build());
        }

        String pincode = config.pinCode;
        if (pincode == null || pincode.isEmpty()) {
            messages.add(ConfigStatusMessage.Builder.error(CONFIG_PIN_CODE).withMessageKeySuffix(PINCODE_MISSING)
                    .withArguments(CONFIG_PIN_CODE).build());
        }

        String serialid = config.serialId;
        if (serialid == null || serialid.isEmpty()) {
            messages.add(ConfigStatusMessage.Builder.error(CONFIG_SERIAL_ID).withMessageKeySuffix(SERIALID_MISSING)
                    .withArguments(CONFIG_SERIAL_ID).build());
        }

        return messages;
    }
}
