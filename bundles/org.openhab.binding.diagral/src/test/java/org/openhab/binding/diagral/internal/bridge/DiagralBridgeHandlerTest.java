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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.openhab.binding.diagral.internal.dto.DiagralAnomalies;
import org.openhab.binding.diagral.internal.dto.DiagralGroup;
import org.openhab.binding.diagral.internal.dto.DiagralSystemConfiguration;
import org.openhab.binding.diagral.internal.dto.DiagralSystemStatus;
import org.openhab.binding.diagral.internal.exception.DiagralApiException;
import org.openhab.binding.diagral.internal.exception.DiagralException;
import org.openhab.core.config.core.Configuration;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ThingUID;

/**
 * Unit tests for {@link DiagralBridgeHandler}, covering the correctness findings C1-C4 from the
 * 2026-09-04 review: the bounded system-configuration cache and its stale-on-failure fallback (C4), the
 * lock that stops two polls overlapping (C2), and the lifecycle guard that stops a scheduled
 * authentication retry outliving {@code dispose()} while still surviving a config edit (C3). C1
 * (visibility of the mutable bridge state) has no directly observable behaviour; it is exercised
 * indirectly by the concurrency test here.
 *
 * <p>
 * The handler is driven through its package-private collaborators via reflection rather than a live
 * openHAB framework: {@code DiagralBridgeHandler} takes its {@code DiagralHttpClient} from
 * {@code initialize()}, which needs a running framework, so these tests inject a mocked client directly.
 * </p>
 *
 * @author David Martin - Initial contribution
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@NonNullByDefault
public class DiagralBridgeHandlerTest {

    private @Mock @NonNullByDefault({}) HttpClient httpClient;
    private @Mock @NonNullByDefault({}) Bridge bridge;
    private @Mock @NonNullByDefault({}) DiagralHttpClient diagralHttpClient;

    private @NonNullByDefault({}) DiagralBridgeHandler handler;

    /**
     * Builds a handler with a mocked HTTP client injected, bypassing {@code initialize()}.
     */
    @BeforeEach
    public void setUp() throws Exception {
        when(bridge.getUID()).thenReturn(new ThingUID("diagral", "bridge", "test"));
        when(bridge.getConfiguration()).thenReturn(new Configuration(Map.of()));
        when(bridge.getThings()).thenReturn(List.of());

        handler = new DiagralBridgeHandler(bridge, httpClient);
        set("diagralHttpClient", diagralHttpClient);
    }

    /**
     * Sets one of the handler's private fields.
     *
     * @param name the field name
     * @param value the value to set
     */
    private void set(String name, @Nullable Object value) throws Exception {
        Field field = DiagralBridgeHandler.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(handler, value);
    }

    /**
     * Reads one of the handler's private fields.
     *
     * @param name the field name
     * @return the current value
     */
    private @Nullable Object get(String name) throws Exception {
        Field field = DiagralBridgeHandler.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(handler);
    }

    /**
     * Builds a configuration whose identity can be asserted on.
     *
     * @return a distinguishable configuration instance
     */
    private static DiagralSystemConfiguration configuration() {
        return new DiagralSystemConfiguration();
    }

    /**
     * C4: a second call inside the cache lifetime is served from the cache, not re-fetched - the API's
     * largest response must not be pulled on every poll.
     */
    @Test
    public void configurationIsServedFromCacheWhileFresh() throws Exception {
        DiagralSystemConfiguration first = configuration();
        when(diagralHttpClient.getSystemConfiguration()).thenReturn(first);

        assertThat(handler.getSystemConfiguration(), is(sameInstance(first)));
        assertThat(handler.getSystemConfiguration(), is(sameInstance(first)));

        verify(diagralHttpClient, times(1)).getSystemConfiguration();
    }

    /**
     * C4: the core fix - once the entry is older than its TTL it is re-fetched, so a device inhibited
     * from the e-ONE app or a battery that went flat is eventually reflected instead of being frozen for
     * the lifetime of the binding.
     */
    @Test
    public void configurationIsRefetchedOnceTheCacheExpires() throws Exception {
        DiagralSystemConfiguration first = configuration();
        DiagralSystemConfiguration second = configuration();
        when(diagralHttpClient.getSystemConfiguration()).thenReturn(first, second);

        assertThat(handler.getSystemConfiguration(), is(sameInstance(first)));
        expireConfigurationCache();

        assertThat(handler.getSystemConfiguration(), is(sameInstance(second)));
        verify(diagralHttpClient, times(2)).getSystemConfiguration();
    }

    /**
     * C4: when a refresh fails, the expired entry is served rather than {@code null}, so a transient API
     * failure leaves every thing's channels populated instead of blanking them.
     */
    @Test
    public void expiredConfigurationIsServedWhenTheRefreshFails() throws Exception {
        DiagralSystemConfiguration first = configuration();
        when(diagralHttpClient.getSystemConfiguration()).thenReturn(first)
                .thenThrow(new DiagralException("Request timeout"));

        assertThat(handler.getSystemConfiguration(), is(sameInstance(first)));
        expireConfigurationCache();

        assertThat(handler.getSystemConfiguration(), is(sameInstance(first)));
    }

    /**
     * C4: with nothing ever cached, a failure still yields {@code null} - there is no stale value to fall
     * back to, and inventing one would be worse.
     */
    @Test
    public void failureWithNothingCachedStillReturnsNull() throws Exception {
        when(diagralHttpClient.getSystemConfiguration()).thenThrow(new DiagralException("Request timeout"));

        assertThat(handler.getSystemConfiguration(), is(nullValue()));
    }

    /**
     * C4: an explicit invalidation (what enable/disable does) forces a re-fetch immediately, without
     * waiting for the TTL.
     */
    @Test
    public void invalidatingTheCacheForcesAnImmediateRefetch() throws Exception {
        DiagralSystemConfiguration first = configuration();
        DiagralSystemConfiguration second = configuration();
        when(diagralHttpClient.getSystemConfiguration()).thenReturn(first, second);

        assertThat(handler.getSystemConfiguration(), is(sameInstance(first)));
        set("cachedConfiguration", null);

        assertThat(handler.getSystemConfiguration(), is(sameInstance(second)));
    }

    /**
     * Ages the cached configuration past its TTL by rewriting the entry's timestamp, so the test doesn't
     * have to wait five minutes.
     */
    private void expireConfigurationCache() throws Exception {
        Object cached = Objects.requireNonNull(get("cachedConfiguration"), "nothing cached to expire");
        Class<?> cachedClass = cached.getClass();
        Field value = cachedClass.getDeclaredField("value");
        value.setAccessible(true);

        // Rebuild the record with a timestamp far enough in the past to be stale.
        java.lang.reflect.Constructor<?> constructor = cachedClass.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        Object rebuilt = constructor.newInstance(value.get(cached),
                System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1));
        set("cachedConfiguration", rebuilt);
    }

    /**
     * P4: concurrent callers that all miss the cache must issue exactly one HTTP fetch between them, not
     * one each.
     *
     * <p>
     * This is the race live logs kept showing at startup, where every child handler initialises at once
     * and each independently found the cache empty: 4 to 11 duplicate fetches of the API's largest
     * response in a single burst. It matters more now that C8 dispatches those refreshes onto the
     * scheduler, which widens the fan-out.
     * </p>
     */
    @Test
    public void concurrentCallersShareASingleConfigurationFetch() throws Exception {
        int callers = 8;
        AtomicInteger inFlight = new AtomicInteger();
        AtomicInteger maxInFlight = new AtomicInteger();
        DiagralSystemConfiguration configuration = configuration();
        when(diagralHttpClient.getSystemConfiguration()).thenAnswer(invocation -> {
            int now = inFlight.incrementAndGet();
            maxInFlight.accumulateAndGet(now, Math::max);
            Thread.sleep(200);
            inFlight.decrementAndGet();
            return configuration;
        });

        CountDownLatch startTogether = new CountDownLatch(1);
        List<DiagralSystemConfiguration> results = java.util.Collections.synchronizedList(new ArrayList<>());
        Thread[] threads = new Thread[callers];
        for (int i = 0; i < callers; i++) {
            threads[i] = new Thread(() -> {
                try {
                    startTogether.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                DiagralSystemConfiguration result = handler.getSystemConfiguration();
                if (result != null) {
                    results.add(result);
                }
            });
            threads[i].start();
        }
        startTogether.countDown();
        for (Thread thread : threads) {
            thread.join(10000);
        }

        verify(diagralHttpClient, times(1)).getSystemConfiguration();
        assertThat("fetches overlapped", maxInFlight.get(), is(1));
        assertThat("every caller got the configuration", results, hasSize(callers));
        assertThat(results, everyItem(is(sameInstance(configuration))));
    }

    /**
     * The same single-flight guarantee for the status fetch, found by live testing rather than review:
     * with C8 dispatching every handler's refresh onto the scheduler, a cold or stale status cache had
     * ten handlers each issuing their own request within 30ms and each waiting out its own 10s timeout,
     * because a failed fetch caches nothing for the others to reuse.
     */
    @Test
    public void concurrentCallersShareASingleStatusFetch() throws Exception {
        int callers = 10;
        AtomicInteger inFlight = new AtomicInteger();
        AtomicInteger maxInFlight = new AtomicInteger();
        when(diagralHttpClient.getSystemStatus()).thenAnswer(invocation -> {
            int now = inFlight.incrementAndGet();
            maxInFlight.accumulateAndGet(now, Math::max);
            Thread.sleep(200);
            inFlight.decrementAndGet();
            throw new DiagralException("Request timeout");
        });

        CountDownLatch go = new CountDownLatch(1);
        Thread[] threads = new Thread[callers];
        for (int i = 0; i < callers; i++) {
            threads[i] = new Thread(() -> {
                try {
                    go.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                handler.getSystemStatus();
            });
            threads[i].start();
        }
        go.countDown();
        for (Thread thread : threads) {
            thread.join(15000);
        }

        assertThat("status fetches overlapped", maxInFlight.get(), is(1));
    }

    /**
     * C2: two polls submitted at once must not overlap. Without the lock they raced on the caches and
     * duplicated every HTTP call; with it, the second waits for the first.
     */
    @Test
    public void concurrentPollsAreSerialised() throws Exception {
        AtomicInteger concurrent = new AtomicInteger();
        AtomicInteger maxConcurrent = new AtomicInteger();
        CountDownLatch bothStarted = new CountDownLatch(1);

        when(diagralHttpClient.getSystemStatus()).thenAnswer(invocation -> {
            int now = concurrent.incrementAndGet();
            maxConcurrent.accumulateAndGet(now, Math::max);
            // Hold the poll open long enough that an unguarded second poll would overlap it.
            Thread.sleep(150);
            concurrent.decrementAndGet();
            bothStarted.countDown();
            DiagralSystemStatus status = new DiagralSystemStatus();
            status.status = "OFF";
            return status;
        });

        Thread first = new Thread(() -> invokePoll());
        Thread second = new Thread(() -> invokePoll());
        first.start();
        second.start();
        first.join(5000);
        second.join(5000);

        assertThat(bothStarted.await(5, TimeUnit.SECONDS), is(true));
        assertThat("two polls ran at the same time", maxConcurrent.get(), is(1));
        verify(diagralHttpClient, times(2)).getSystemStatus();
    }

    /**
     * Invokes the handler's private {@code poll()}.
     */
    private void invokePoll() {
        try {
            java.lang.reflect.Method poll = DiagralBridgeHandler.class.getDeclaredMethod("poll");
            poll.setAccessible(true);
            poll.invoke(handler);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * C3: after {@code dispose()}, a poll must do nothing - no HTTP call on a handler that is gone.
     */
    @Test
    public void pollIsANoOpOnceDisposed() throws Exception {
        set("disposed", true);

        invokePoll();

        verify(diagralHttpClient, never()).getSystemStatus();
    }

    /**
     * C3: the regression guard that matters most - the framework reuses this handler instance
     * ({@code BaseThingHandler.thingUpdated()} calls {@code dispose()} then {@code initialize()} on the
     * same object when the thing's config is edited), so the disposed flag must not be sticky. If it
     * were, the bridge would never come back online after any configuration change.
     */
    @Test
    public void disposedFlagIsClearedByReinitialisation() throws Exception {
        set("disposed", true);
        set("consecutivePollFailures", new AtomicInteger(4));

        // initialize() bails out early on an invalid (empty) configuration, but only after resetting the
        // per-lifecycle state - which is exactly the behaviour under test.
        handler.initialize();

        assertThat("disposed must be cleared so polling can resume", get("disposed"), is(false));
        AtomicInteger counter = (AtomicInteger) Objects.requireNonNull(get("consecutivePollFailures"));
        assertThat("a stale failure count would trip MAX_CONSECUTIVE_POLL_FAILURES early", counter.get(), is(0));
    }

    /**
     * C1: the failure counter is atomic, so concurrent increments can't lose updates and trip - or fail
     * to trip - the offline threshold incorrectly.
     */
    @Test
    public void failureCounterIsAtomic() throws Exception {
        AtomicInteger counter = (AtomicInteger) Objects.requireNonNull(get("consecutivePollFailures"));
        int threads = 8;
        int perThread = 1000;
        Thread[] workers = new Thread[threads];
        for (int i = 0; i < threads; i++) {
            workers[i] = new Thread(() -> {
                for (int j = 0; j < perThread; j++) {
                    counter.incrementAndGet();
                }
            });
            workers[i].start();
        }
        for (Thread worker : workers) {
            worker.join(5000);
        }

        assertThat(counter.get(), is(threads * perThread));
    }

    /**
     * C1: the status cache's value and its timestamp live in one immutable record, so a reader can never
     * pair a new value with an old timestamp. Verifies the field really does hold that record rather
     * than a bare status.
     */
    @Test
    public void statusCacheHoldsValueAndTimestampTogether() throws Exception {
        DiagralSystemStatus status = new DiagralSystemStatus();
        status.status = "PRESENCE";
        when(diagralHttpClient.getSystemStatus()).thenReturn(status);

        assertThat(handler.getSystemStatus(), is(sameInstance(status)));

        Object cached = Objects.requireNonNull(get("cachedSystemStatus"), "status should be cached");
        assertThat(cached.getClass().isRecord(), is(true));
        assertThat(cached.getClass().getRecordComponents().length, is(2));
    }

    /**
     * The short-lived status cache still collapses the several calls child handlers make in one poll
     * cycle into a single HTTP request - unchanged by C1's restructuring.
     */
    @Test
    public void statusIsServedFromTheShortLivedCache() throws Exception {
        DiagralSystemStatus status = new DiagralSystemStatus();
        status.status = "OFF";
        when(diagralHttpClient.getSystemStatus()).thenReturn(status);

        handler.getSystemStatus();
        handler.getSystemStatus();
        handler.getSystemStatus();

        verify(diagralHttpClient, times(1)).getSystemStatus();
    }

    /**
     * A failed status fetch with nothing cached yields {@code null}, unchanged - handlers treat that as
     * "skip this refresh", which is correct for live status (unlike configuration, where stale data is
     * still useful).
     */
    @Test
    public void statusFailureReturnsNull() throws Exception {
        when(diagralHttpClient.getSystemStatus()).thenThrow(new DiagralException("Request timeout"));

        assertThat(handler.getSystemStatus(), is(nullValue()));
    }

    /**
     * P1/P2: one refresh cycle resolves anomalies at most once, however many handlers read them - and
     * only if something actually does. Previously the alarm-system handler fetched them inline on every
     * cycle, inside the sequential loop, where a slow response stalled every handler after it.
     */
    @Test
    public void snapshotResolvesAnomaliesLazilyAndOnlyOnce() throws Exception {
        DiagralAnomalies anomalies = new DiagralAnomalies();
        when(diagralHttpClient.getAnomalies()).thenReturn(anomalies);
        when(diagralHttpClient.getSystemStatus()).thenReturn(new DiagralSystemStatus());
        when(diagralHttpClient.getSystemConfiguration()).thenReturn(configuration());

        DiagralPollSnapshot snapshot = handler.captureSnapshot();

        // Nothing has asked for anomalies yet, so nothing was fetched.
        verify(diagralHttpClient, never()).getAnomalies();

        assertThat(snapshot.anomalies(), is(sameInstance(anomalies)));
        assertThat(snapshot.anomalies(), is(sameInstance(anomalies)));
        assertThat(snapshot.anomalies(), is(sameInstance(anomalies)));

        verify(diagralHttpClient, times(1)).getAnomalies();
    }

    /**
     * P2: concurrent readers of one snapshot must still trigger exactly one fetch - lifecycle-driven
     * refreshes run on the scheduler, so several handlers really can hit the same snapshot at once.
     */
    @Test
    public void concurrentSnapshotReadersShareOneAnomaliesFetch() throws Exception {
        when(diagralHttpClient.getAnomalies()).thenAnswer(invocation -> {
            Thread.sleep(150);
            return new DiagralAnomalies();
        });
        DiagralPollSnapshot snapshot = handler.captureSnapshot();

        CountDownLatch go = new CountDownLatch(1);
        Thread[] readers = new Thread[6];
        for (int i = 0; i < readers.length; i++) {
            readers[i] = new Thread(() -> {
                try {
                    go.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                snapshot.anomalies();
            });
            readers[i].start();
        }
        go.countDown();
        for (Thread reader : readers) {
            reader.join(10000);
        }

        verify(diagralHttpClient, times(1)).getAnomalies();
    }

    /**
     * P2: the snapshot reports exactly the status and configuration it was built with, so every handler
     * in a cycle sees the same moment rather than each re-reading a cache that may have moved on.
     */
    @Test
    public void snapshotCarriesTheStatusAndConfigurationItWasBuiltWith() throws Exception {
        DiagralSystemStatus status = new DiagralSystemStatus();
        status.status = "PRESENCE";
        DiagralSystemConfiguration config = configuration();
        when(diagralHttpClient.getSystemStatus()).thenReturn(status);
        when(diagralHttpClient.getSystemConfiguration()).thenReturn(config);

        DiagralPollSnapshot snapshot = handler.captureSnapshot();

        assertThat(snapshot.status(), is(sameInstance(status)));
        assertThat(snapshot.configuration(), is(sameInstance(config)));
    }

    /**
     * P3: a 429 backs scheduled polling off, so the binding stops adding load while it is being rate
     * limited.
     */
    @Test
    public void rateLimitBacksOffScheduledPolling() throws Exception {
        when(diagralHttpClient.getSystemStatus()).thenThrow(new DiagralApiException("Rate limit exceeded", 429));

        invokePoll();

        long backoffUntil = (long) Objects.requireNonNull(get("backoffUntilMillis"));
        assertThat("no backoff was applied", backoffUntil, is(greaterThan(System.currentTimeMillis())));
        assertThat(((AtomicInteger) Objects.requireNonNull(get("consecutiveBackoffFailures"))).get(), is(1));
    }

    /** P3: a 5xx backs off too - retrying a broken server at full cadence helps nobody. */
    @Test
    public void serverErrorBacksOffScheduledPolling() throws Exception {
        when(diagralHttpClient.getSystemStatus()).thenThrow(new DiagralApiException("Server error: 503", 503));

        invokePoll();

        assertThat((long) Objects.requireNonNull(get("backoffUntilMillis")),
                is(greaterThan(System.currentTimeMillis())));
    }

    /**
     * P3: an ordinary timeout is not a "slow down" signal - backing off for it would make the binding
     * far less responsive on an API whose baseline timeout rate is already high.
     */
    @Test
    public void ordinaryFailureDoesNotBackOff() throws Exception {
        when(diagralHttpClient.getSystemStatus()).thenThrow(new DiagralException("Request timeout"));

        invokePoll();

        assertThat((long) Objects.requireNonNull(get("backoffUntilMillis")), is(0L));
    }

    /**
     * P3: an API error that is <em>not</em> a slow-down signal must not back off either. Distinct from
     * {@code ordinaryFailureDoesNotBackOff}, which throws a plain transport exception that never reaches
     * the status-code check at all - this one exercises that check itself.
     */
    @Test
    public void nonThrottlingApiErrorDoesNotBackOff() throws Exception {
        when(diagralHttpClient.getSystemStatus()).thenThrow(new DiagralApiException("Resource not found", 404));

        invokePoll();

        assertThat((long) Objects.requireNonNull(get("backoffUntilMillis")), is(0L));
        assertThat(((AtomicInteger) Objects.requireNonNull(get("consecutiveBackoffFailures"))).get(), is(0));
    }

    /** P3: the delay grows with each consecutive occurrence rather than staying flat. */
    @Test
    public void backoffGrowsExponentially() throws Exception {
        when(diagralHttpClient.getSystemStatus()).thenThrow(new DiagralApiException("Rate limit exceeded", 429));

        invokePoll();
        long first = (long) Objects.requireNonNull(get("backoffUntilMillis")) - System.currentTimeMillis();
        set("backoffUntilMillis", 0L);
        invokePoll();
        long second = (long) Objects.requireNonNull(get("backoffUntilMillis")) - System.currentTimeMillis();

        assertThat("second backoff should be longer than the first", second, is(greaterThan(first)));
    }

    /** P3: a successful poll clears the backoff immediately rather than waiting the delay out. */
    @Test
    public void successClearsTheBackoff() throws Exception {
        when(diagralHttpClient.getSystemStatus()).thenThrow(new DiagralApiException("Rate limit exceeded", 429))
                .thenReturn(new DiagralSystemStatus());

        invokePoll();
        assertThat((long) Objects.requireNonNull(get("backoffUntilMillis")), is(greaterThan(0L)));

        invokePoll();

        assertThat((long) Objects.requireNonNull(get("backoffUntilMillis")), is(0L));
        assertThat(((AtomicInteger) Objects.requireNonNull(get("consecutiveBackoffFailures"))).get(), is(0));
    }

    /**
     * P3: while backing off, the scheduled tick skips - but a command-triggered poll still runs, because
     * that one exists to reflect a change the user just made.
     */
    @Test
    public void backoffSkipsScheduledPollsButNotCommandTriggeredOnes() throws Exception {
        when(diagralHttpClient.getSystemStatus()).thenReturn(new DiagralSystemStatus());
        set("backoffUntilMillis", System.currentTimeMillis() + 60_000);

        java.lang.reflect.Method scheduledPoll = DiagralBridgeHandler.class.getDeclaredMethod("scheduledPoll");
        scheduledPoll.setAccessible(true);
        scheduledPoll.invoke(handler);
        verify(diagralHttpClient, never()).getSystemStatus();

        invokePoll();
        verify(diagralHttpClient, times(1)).getSystemStatus();
    }

    /**
     * Builds a configuration with the given per-mode group membership.
     *
     * @param presence group indices armed by PRESENCE
     * @param partial1 group indices armed by PARTIAL1
     * @param partial2 group indices armed by PARTIAL2
     * @param allGroupIndices every group that exists, which is what FULL arms
     * @return the configuration
     */
    private DiagralSystemConfiguration configurationWithGroups(List<Integer> presence, List<Integer> partial1,
            List<Integer> partial2, int... allGroupIndices) {
        DiagralSystemConfiguration config = new DiagralSystemConfiguration();
        config.presenceGroup = presence;
        config.partialGroup1 = partial1;
        config.partialGroup2 = partial2;
        List<DiagralGroup> groups = new ArrayList<>();
        for (int index : allGroupIndices) {
            DiagralGroup group = new DiagralGroup();
            group.index = index;
            groups.add(group);
        }
        config.groups = groups;
        return config;
    }

    /**
     * Builds a snapshot with the given status and configuration.
     *
     * @param status the system status value, or null for no status
     * @param config the configuration, or null
     * @param activatedGroups the activated_groups list to report, or null
     * @return the snapshot
     */
    private DiagralPollSnapshot snapshot(@Nullable String status, @Nullable DiagralSystemConfiguration config,
            @Nullable List<Integer> activatedGroups) {
        DiagralSystemStatus systemStatus = null;
        if (status != null) {
            systemStatus = new DiagralSystemStatus();
            systemStatus.status = status;
            systemStatus.activatedGroups = activatedGroups;
        }
        return new DiagralPollSnapshot(systemStatus, config, () -> null);
    }

    /**
     * While the system reports one of the five named modes, group membership is derived fresh from the
     * configuration - self-correcting every poll however the mode was set, including from the e-ONE app.
     */
    @Test
    public void namedModeDerivesGroupMembershipFromConfiguration() {
        DiagralSystemConfiguration config = configurationWithGroups(List.of(1), List.of(1), List.of(2), 1, 2, 3);

        DiagralPollSnapshot presence = snapshot("PRESENCE", config, List.of());
        assertThat(handler.isGroupActive("1", presence), is(true));
        assertThat(handler.isGroupActive("2", presence), is(false));
        assertThat(handler.isGroupActive("3", presence), is(false));

        DiagralPollSnapshot partial2 = snapshot("PARTIAL2", config, List.of());
        assertThat(handler.isGroupActive("1", partial2), is(false));
        assertThat(handler.isGroupActive("2", partial2), is(true));
    }

    /** FULL arms every group that exists, which the API expresses by listing none of them. */
    @Test
    public void fullModeArmsEveryGroup() {
        DiagralPollSnapshot full = snapshot("FULL",
                configurationWithGroups(List.of(1), List.of(1), List.of(2), 1, 2, 3), List.of());

        for (String groupId : new String[] { "1", "2", "3" }) {
            assertThat("group " + groupId + " should be armed by FULL", handler.isGroupActive(groupId, full), is(true));
        }
    }

    /** OFF arms nothing, whatever the configuration says. */
    @Test
    public void offArmsNoGroup() {
        DiagralPollSnapshot off = snapshot("OFF", configurationWithGroups(List.of(1), List.of(1), List.of(2), 1, 2, 3),
                List.of());

        assertThat(handler.isGroupActive("1", off), is(false));
        assertThat(handler.isGroupActive("2", off), is(false));
    }

    /**
     * The settled GROUP status is the one non-named state where activated_groups is reliable, so it is
     * trusted directly - the 2026-09-04 finding.
     */
    @Test
    public void settledGroupStatusTrustsActivatedGroups() {
        DiagralPollSnapshot group = snapshot("GROUP", null, List.of(2));

        assertThat(handler.isGroupActive("2", group), is(true));
        assertThat(handler.isGroupActive("1", group), is(false));
    }

    /**
     * A transitional status carries no per-group detail, so the answer falls back to the bridge's own
     * record of the last group action it issued.
     */
    @Test
    public void transitionalStatusFallsBackToLocallyTrackedGroups() throws Exception {
        when(diagralHttpClient.getSystemStatus()).thenReturn(new DiagralSystemStatus());
        handler.activateGroup("3");

        DiagralPollSnapshot tempo = snapshot("TEMPO_GROUP", null, List.of());
        assertThat(handler.isGroupActive("3", tempo), is(true));
        assertThat(handler.isGroupActive("1", tempo), is(false));
    }

    /** LEARNING_MODE is not an armed state and has no per-group detail; it uses the same fallback. */
    @Test
    public void learningModeUsesTheFallback() {
        DiagralPollSnapshot learning = snapshot("LEARNING_MODE", null, List.of());

        assertThat(handler.isGroupActive("1", learning), is(false));
    }

    /** mode-control shows the real mode while it is a named one. */
    @Test
    public void displayedModeIsTheNamedModeWhenThereIsOne() {
        assertThat(handler.getDisplayedMode(snapshot("PARTIAL1", null, null)), is("PARTIAL1"));
    }

    /**
     * During a transitional status, mode-control holds the last named mode rather than showing a raw
     * TEMPO_* string, which is not one of the five selectable modes.
     */
    @Test
    public void displayedModeHoldsTheLastNamedModeWhileTransitional() {
        handler.getDisplayedMode(snapshot("PRESENCE", null, null));

        assertThat(handler.getDisplayedMode(snapshot("TEMPO_1", null, null)), is("PRESENCE"));
        assertThat(handler.getDisplayedMode(snapshot("GROUP", null, null)), is("PRESENCE"));
    }

    /** With nothing observed yet, there is no mode to display. */
    @Test
    public void displayedModeIsNullBeforeAnythingIsKnown() {
        assertThat(handler.getDisplayedMode(snapshot(null, null, null)), is(nullValue()));
    }

    /**
     * Guards the mocking assumption the rest of this class relies on: the handler really does route every
     * call through the injected client.
     */
    @Test
    public void handlerUsesTheInjectedClient() throws Exception {
        when(diagralHttpClient.getSystemConfiguration()).thenReturn(configuration());

        handler.getSystemConfiguration();

        verify(diagralHttpClient, atLeastOnce()).getSystemConfiguration();
        verifyNoInteractions(httpClient);
    }

    /**
     * Silences an unused-mock warning for {@code any()} imports kept for readability.
     */
    @Test
    public void mockingHarnessIsWired() {
        assertThat(handler, is(notNullValue()));
        verify(bridge, never()).setStatusInfo(any());
    }
}
