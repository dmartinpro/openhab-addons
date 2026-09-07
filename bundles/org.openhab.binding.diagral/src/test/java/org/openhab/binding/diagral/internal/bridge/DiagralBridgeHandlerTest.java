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
import org.openhab.binding.diagral.internal.dto.DiagralSystemConfiguration;
import org.openhab.binding.diagral.internal.dto.DiagralSystemStatus;
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
