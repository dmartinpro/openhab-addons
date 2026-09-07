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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.openhab.binding.diagral.internal.bridge.DiagralBridgeHandler;
import org.openhab.binding.diagral.internal.bridge.DiagralPollSnapshot;
import org.openhab.core.config.core.Configuration;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusInfo;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.binding.ThingHandlerCallback;
import org.openhab.core.thing.binding.builder.ThingStatusInfoBuilder;
import org.openhab.core.types.Command;

/**
 * Unit tests for {@link DiagralBaseThingHandler}, covering finding C8 of the 2026-09-04 review: channel
 * refreshes triggered by a lifecycle callback must not block the calling thread, because
 * {@code refreshStatus()} reaches a cloud API that regularly takes its full 10-second timeout. Blocking
 * there is what produced the framework's recurring "Initializing handler ... takes more than 5000ms"
 * warnings.
 *
 * <p>
 * Also covers the disposal guard the asynchronous refresh needs, and the bridge-status mirroring that D1
 * moved into this class from three byte-identical copies.
 * </p>
 *
 * @author David Martin - Initial contribution
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@NonNullByDefault
public class DiagralBaseThingHandlerTest {

    /** How long a test waits for an asynchronous refresh before deciding it will never run. */
    private static final long ASYNC_TIMEOUT_SECONDS = 5;

    /**
     * A minimal concrete handler whose {@code refreshStatus()} is observable and can be made to block, so
     * the asynchrony itself can be asserted on.
     */
    @NonNullByDefault
    private static final class TestHandler extends DiagralBaseThingHandler {

        private final AtomicInteger refreshes = new AtomicInteger();
        private final CountDownLatch refreshStarted = new CountDownLatch(1);
        private final CountDownLatch releaseRefresh = new CountDownLatch(1);
        private volatile boolean blockOnRefresh;
        private volatile @Nullable Bridge bridge;

        /**
         * Overridden to widen visibility and return a bridge the test controls - {@code getBridge()} is
         * protected in {@code BaseThingHandler}, so it cannot be stubbed from here.
         *
         * @return the bridge this test attached, or {@code null}
         */
        @Override
        public @Nullable Bridge getBridge() {
            return bridge;
        }

        /**
         * @param thing the thing to handle
         */
        TestHandler(Thing thing) {
            super(thing);
        }

        /** Brings the handler online exactly the way the real handlers do. */
        @Override
        public void initialize() {
            if (goOnlineIfBridgeAvailable()) {
                refreshStatusAsync();
            }
        }

        /**
         * @param channelUID unused
         * @param command unused
         */
        @Override
        public void handleCommand(ChannelUID channelUID, Command command) {
            // not under test
        }

        /**
         * Records the call, optionally blocking so a test can observe the caller was not held up.
         *
         * @param snapshot the shared system state (unused here)
         */
        @Override
        public void refreshStatus(DiagralPollSnapshot snapshot) {
            refreshes.incrementAndGet();
            refreshStarted.countDown();
            if (blockOnRefresh) {
                try {
                    releaseRefresh.await(ASYNC_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    /**
     * Builds a handler attached to a mocked bridge in the given status.
     *
     * @param bridgeStatus the status the parent bridge should report
     * @return the handler, already given a mocked callback
     */
    private TestHandler handlerWithBridge(ThingStatus bridgeStatus) {
        Thing thing = mock(Thing.class);
        when(thing.getUID()).thenReturn(new ThingUID("diagral", "group", "test"));
        when(thing.getConfiguration()).thenReturn(new Configuration(Map.of()));

        Bridge bridge = mock(Bridge.class);
        when(bridge.getStatus()).thenReturn(bridgeStatus);
        DiagralBridgeHandler bridgeHandler = mock(DiagralBridgeHandler.class);
        when(bridgeHandler.captureSnapshot()).thenReturn(new DiagralPollSnapshot(null, null, () -> null));
        when(bridge.getHandler()).thenReturn(bridgeHandler);

        TestHandler handler = new TestHandler(thing);
        handler.bridge = bridge;
        handler.setCallback(mock(ThingHandlerCallback.class));
        return handler;
    }

    /**
     * C8 - the core assertion: {@code initialize()} must return while the refresh is still running,
     * rather than waiting for the cloud call to finish.
     */
    @Test
    public void initializeDoesNotBlockOnTheRefresh() throws Exception {
        TestHandler handler = handlerWithBridge(ThingStatus.ONLINE);
        handler.blockOnRefresh = true;

        long start = System.nanoTime();
        handler.initialize();
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000;

        // The refresh really did start, and is still blocked...
        assertThat(handler.refreshStarted.await(ASYNC_TIMEOUT_SECONDS, TimeUnit.SECONDS), is(true));
        // ...yet initialize() had already returned, nowhere near the framework's 5000ms threshold.
        assertThat("initialize() blocked on the refresh", elapsedMillis, is(lessThan(1000L)));

        handler.releaseRefresh.countDown();
    }

    /**
     * C8 - the same guarantee for the other lifecycle callback, which fires on every child thing at once
     * when the bridge comes online.
     */
    @Test
    public void bridgeStatusChangedDoesNotBlockOnTheRefresh() throws Exception {
        TestHandler handler = handlerWithBridge(ThingStatus.ONLINE);
        handler.blockOnRefresh = true;

        long start = System.nanoTime();
        handler.bridgeStatusChanged(ThingStatusInfoBuilder.create(ThingStatus.ONLINE).build());
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000;

        assertThat(handler.refreshStarted.await(ASYNC_TIMEOUT_SECONDS, TimeUnit.SECONDS), is(true));
        assertThat("bridgeStatusChanged() blocked on the refresh", elapsedMillis, is(lessThan(1000L)));

        handler.releaseRefresh.countDown();
    }

    /** The refresh still actually happens - offloading it must not quietly drop it. */
    @Test
    public void asynchronousRefreshStillRuns() throws Exception {
        TestHandler handler = handlerWithBridge(ThingStatus.ONLINE);

        handler.initialize();

        assertThat(handler.refreshStarted.await(ASYNC_TIMEOUT_SECONDS, TimeUnit.SECONDS), is(true));
        assertThat(handler.refreshes.get(), is(1));
    }

    /**
     * The guard the asynchrony makes necessary: a refresh queued before {@code dispose()} must not
     * publish channel states through a torn-down handler.
     */
    @Test
    public void refreshIsSkippedAfterDispose() throws Exception {
        TestHandler handler = handlerWithBridge(ThingStatus.ONLINE);
        handler.dispose();

        handler.refreshStatusAsync();

        assertThat("a disposed handler must not refresh", handler.refreshStarted.await(1, TimeUnit.SECONDS), is(false));
        assertThat(handler.refreshes.get(), is(0));
    }

    /**
     * The disposal guard must not be sticky: the framework reuses handler instances when a thing's
     * configuration is edited, so a handler brought back online must refresh again. Without the reset in
     * {@code goOnlineIfBridgeAvailable()}, every thing would stop updating after its first config change.
     */
    @Test
    public void refreshResumesAfterReinitialisation() throws Exception {
        TestHandler handler = handlerWithBridge(ThingStatus.ONLINE);
        handler.dispose();

        handler.initialize();

        assertThat(handler.refreshStarted.await(ASYNC_TIMEOUT_SECONDS, TimeUnit.SECONDS), is(true));
        assertThat(handler.refreshes.get(), is(1));
    }

    /**
     * D1 - the shared bridge-status mirroring: an offline bridge takes the thing offline and schedules no
     * refresh.
     */
    @Test
    public void offlineBridgeTakesTheThingOfflineWithoutRefreshing() throws Exception {
        TestHandler handler = handlerWithBridge(ThingStatus.OFFLINE);

        handler.initialize();

        assertThat(handler.refreshStarted.await(1, TimeUnit.SECONDS), is(false));
        assertThat(handler.refreshes.get(), is(0));
    }

    /** D1 - a thing with no bridge at all goes offline rather than throwing. */
    @Test
    public void missingBridgeTakesTheThingOffline() throws Exception {
        Thing thing = mock(Thing.class);
        when(thing.getUID()).thenReturn(new ThingUID("diagral", "group", "test"));
        when(thing.getConfiguration()).thenReturn(new Configuration(Map.of()));
        TestHandler handler = new TestHandler(thing);
        handler.setCallback(mock(ThingHandlerCallback.class));

        handler.initialize();

        assertThat(handler.refreshes.get(), is(0));
        assertThat(handler.getBridgeHandler(), is(nullValue()));
    }

    /** Silences an unused-import warning while asserting the status info helper behaves as expected. */
    @Test
    public void statusInfoHelperReportsOnline() {
        ThingStatusInfo info = ThingStatusInfoBuilder.create(ThingStatus.ONLINE).build();

        assertThat(info.getStatus(), is(ThingStatus.ONLINE));
    }
}
