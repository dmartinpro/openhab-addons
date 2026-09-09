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
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.openhab.binding.diagral.internal.DiagralBindingConstants.MODE_FULL;
import static org.openhab.binding.diagral.internal.DiagralBindingConstants.PRODUCT_TYPE_SENSOR;
import static org.openhab.binding.diagral.internal.DiagralBindingConstants.SYSTEM_STATUS_GROUP;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openhab.binding.diagral.internal.dto.DiagralAnomalies;
import org.openhab.binding.diagral.internal.dto.DiagralGroup;
import org.openhab.binding.diagral.internal.dto.DiagralSystemConfiguration;
import org.openhab.binding.diagral.internal.testsupport.FakeDiagralApiServer;
import org.openhab.core.config.core.Configuration;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.binding.ThingHandlerCallback;

/**
 * Integration-style tests for {@link DiagralBridgeHandler} driven through the <em>real</em>
 * {@link DiagralHttpClient}/{@link DiagralAuthenticationManager} against {@link FakeDiagralApiServer},
 * rather than the mocked {@code DiagralHttpClient} {@code DiagralBridgeHandlerTest} uses. That file
 * thoroughly covers the bridge's own cache/lock/backoff logic in isolation; this one instead exercises the
 * seam those tests deliberately mock out - real HMAC signing, real JSON parsing, and the bridge's
 * status-derivation logic ({@code isGroupActive()}/{@code getDisplayedMode()}) - all working together the
 * way they do in production, closing gaps this bundle's {@code CLAUDE.md} repeatedly describes as
 * "only manually live-verified" against the real cloud API.
 *
 * <p>
 * Like {@code DiagralBridgeHandlerTest}, this bypasses {@code initialize()} (which needs a live framework
 * to reach {@code scheduler.execute()} safely) and instead injects an already-constructed
 * {@code authManager}/{@code diagralHttpClient} pair via reflection - mirroring exactly what
 * {@code initialize()} itself builds, just without the async dispatch. {@code scheduler} itself is a real,
 * functional {@link java.util.concurrent.ScheduledExecutorService} even without a live framework (it's a
 * plain field assigned in {@code BaseThingHandler}'s constructor), so commands that dispatch a follow-up
 * re-poll via {@code scheduler.execute()} do still run - just asynchronously. Tests that care about the
 * settled outcome of a command use {@link FakeDiagralApiServer#setTransitionReads(int)} with {@code 0} to
 * make that outcome immediate and race-free, since Phase 2's tests already cover the transitional window
 * itself in isolation.
 * </p>
 *
 * @author David Martin - Initial contribution
 */
@NonNullByDefault
public class DiagralBridgeHandlerFakeApiIntegrationTest {

    private static final String USERNAME = "user@example.test";
    private static final String PASSWORD = "correct-password";
    private static final String SERIAL_ID = "SERIAL1";
    private static final String PIN_CODE = "1234";

    /** How long a test waits for scheduler-dispatched work (e.g. dispose's fire-and-forget key deletion). */
    private static final long ASYNC_WAIT_TIMEOUT_MILLIS = 3000;

    private @NonNullByDefault({}) FakeDiagralApiServer fakeServer;
    private @NonNullByDefault({}) DiagralBridgeHandler handler;
    private @NonNullByDefault({}) Bridge bridge;
    private @NonNullByDefault({}) ThingHandlerCallback callback;

    /**
     * Builds a bridge handler wired to a fresh {@link FakeDiagralApiServer} through the real client
     * classes, with a mocked {@link ThingHandlerCallback} so {@code updateStatus()} calls are observable.
     *
     * <p>
     * {@code authManager}/{@code diagralHttpClient} are constructed the same way {@code initialize()}
     * does and injected via reflection, using a second mocked {@code HttpClient} wired to the same
     * {@link FakeDiagralApiServer} instance - safe because all of this fake's state lives on the fake
     * itself, not on the mock, so both mocks observe and mutate the same simulated server.
     * </p>
     */
    @BeforeEach
    public void setUp() throws Exception {
        fakeServer = new FakeDiagralApiServer(USERNAME, PASSWORD, SERIAL_ID, PIN_CODE);

        bridge = mock(Bridge.class);
        when(bridge.getUID()).thenReturn(new ThingUID("diagral", "bridge", "test"));
        when(bridge.getConfiguration()).thenReturn(new Configuration(Map.of()));
        when(bridge.getThings()).thenReturn(List.of());

        handler = new DiagralBridgeHandler(bridge, fakeServer.asMockedHttpClient());
        callback = mock(ThingHandlerCallback.class);
        handler.setCallback(callback);

        DiagralAuthenticationManager authManager = new DiagralAuthenticationManager(USERNAME, PASSWORD, SERIAL_ID,
                PIN_CODE);
        set("authManager", authManager);
        set("diagralHttpClient", new DiagralHttpClient(fakeServer.asMockedHttpClient(), authManager));
    }

    /**
     * Sets one of the handler's private fields, mirroring {@code DiagralBridgeHandlerTest}'s helper.
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
     * Invokes the handler's private {@code poll()}, exactly as the scheduled job or a command's
     * follow-up re-poll would.
     */
    private void invokePoll() {
        try {
            Method poll = DiagralBridgeHandler.class.getDeclaredMethod("poll");
            poll.setAccessible(true);
            poll.invoke(handler);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Invokes the handler's private {@code authenticate()} - the same call {@code
     * attemptInitialAuthentication()} makes, but synchronously and without needing {@code scheduler}.
     */
    private void invokeAuthenticate() {
        try {
            Method authenticate = DiagralBridgeHandler.class.getDeclaredMethod("authenticate");
            authenticate.setAccessible(true);
            authenticate.invoke(handler);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e.getCause() != null ? e.getCause() : e);
        }
    }

    /**
     * Polls a condition until it becomes true or a timeout elapses, for asserting on scheduler-dispatched
     * (fire-and-forget) work without a fixed sleep.
     *
     * @param condition the condition to poll
     * @return {@code true} if the condition became true within {@link #ASYNC_WAIT_TIMEOUT_MILLIS}
     */
    private static boolean waitUntil(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + ASYNC_WAIT_TIMEOUT_MILLIS;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return true;
            }
            Thread.sleep(20);
        }
        return condition.getAsBoolean();
    }

    /**
     * Builds a system configuration carrying one group (index 2), so {@code MODE_FULL} arms something and
     * {@code isGroupActive()} has real membership to derive from.
     *
     * @return a configuration with one group
     */
    private static DiagralSystemConfiguration configurationWithOneGroup() {
        DiagralSystemConfiguration config = new DiagralSystemConfiguration();
        config.installationState = 1;
        config.presenceGroup = List.of();
        config.partialGroup1 = List.of();
        config.partialGroup2 = List.of();
        config.sensors = List.of();
        config.sirens = List.of();
        config.cameras = List.of();
        config.transmitters = List.of();
        config.commands = List.of();
        DiagralGroup group = new DiagralGroup();
        group.index = 2;
        group.name = "Test group";
        config.groups = List.of(group);
        return config;
    }

    /**
     * Authentication through the real client/auth-manager pair against the fake must succeed, flip the
     * bridge {@code ONLINE} via the callback, and a subsequent poll must retrieve the fake's configured
     * status/configuration - the full chain {@code DiagralBridgeHandlerTest} mocks past.
     */
    @Test
    public void authenticationAndPollingWorkThroughTheRealHttpStack() throws Exception {
        fakeServer.setSystemConfiguration(configurationWithOneGroup());

        invokeAuthenticate();
        invokePoll();

        verify(callback, atLeastOnce()).statusUpdated(eq(bridge),
                argThat(info -> info.getStatus() == ThingStatus.ONLINE));
        DiagralSystemConfiguration configuration = Objects.requireNonNull(handler.getSystemConfiguration());
        assertThat("configured group index 2 was not returned",
                configuration.groups.stream().anyMatch(g -> g.index == 2), is(true));
    }

    /**
     * Five consecutive injected timeouts - the exact failure mode this bundle has repeatedly observed
     * live - must flip the bridge {@code OFFLINE} with a {@code COMMUNICATION_ERROR} detail, per
     * {@code MAX_CONSECUTIVE_POLL_FAILURES}. Previously only verifiable by code review, since forcing five
     * genuine consecutive API failures against the real cloud "isn't practical to arrange" (see
     * {@code CLAUDE.md}'s "Known incomplete areas") - the fake makes it a two-line setup.
     */
    @Test
    public void sustainedPollFailuresFlipTheBridgeOffline() throws Exception {
        invokeAuthenticate();

        for (int i = 0; i < 5; i++) {
            fakeServer.injectTimeout();
            invokePoll();
        }

        verify(callback).statusUpdated(eq(bridge), argThat(info -> info.getStatus() == ThingStatus.OFFLINE
                && info.getStatusDetail() == ThingStatusDetail.COMMUNICATION_ERROR));
    }

    /**
     * Once the bridge has gone {@code OFFLINE} after {@code MAX_CONSECUTIVE_POLL_FAILURES}, further
     * consecutive failures during the same outage must not re-trigger the transition - {@code pollOnce()}
     * guards it on the thing's current status specifically so this does not happen. Without that guard,
     * every poll past the fifth failure re-logged "Bridge going OFFLINE" and re-published a status update
     * (with an ever-growing failure count in the message, so each one was a distinct
     * {@code ThingStatusInfo} rather than a no-op), which is exactly the noisy behaviour observed live
     * during a sustained outage.
     */
    @Test
    public void repeatedPollFailuresPastTheThresholdDoNotReTriggerTheOfflineTransition() throws Exception {
        invokeAuthenticate();

        for (int i = 0; i < 7; i++) {
            fakeServer.injectTimeout();
            invokePoll();
        }

        verify(callback, times(1)).statusUpdated(eq(bridge), argThat(info -> info.getStatus() == ThingStatus.OFFLINE
                && info.getStatusDetail() == ThingStatusDetail.COMMUNICATION_ERROR));
    }

    /**
     * A successful poll after the bridge went offline must bring it back {@code ONLINE} and reset the
     * failure counter, so a transient outage doesn't leave the bridge permanently offline once the API
     * recovers.
     */
    @Test
    public void bridgeRecoversOnlineAfterASuccessfulPollFollowingFailures() throws Exception {
        invokeAuthenticate();
        for (int i = 0; i < 5; i++) {
            fakeServer.injectTimeout();
            invokePoll();
        }

        invokePoll();

        verify(callback, atLeastOnce()).statusUpdated(eq(bridge),
                argThat(info -> info.getStatus() == ThingStatus.ONLINE));
        AtomicInteger failures = (AtomicInteger) Objects.requireNonNull(get("consecutivePollFailures"));
        assertThat(failures.get(), equalTo(0));
    }

    /**
     * Arming {@code FULL} end to end - the real command, settled through a real (if instantly-settling)
     * fake status transition, then read back through {@code captureSnapshot()} - must be reflected in both
     * {@code getDisplayedMode()} and {@code isGroupActive()} for the group {@code FULL} arms. This is the
     * named-mode branch of {@code isGroupActive()}'s three-way derivation.
     */
    @Test
    public void armingFullEndToEndReflectsInDisplayedModeAndGroupMembership() throws Exception {
        fakeServer.setSystemConfiguration(configurationWithOneGroup());
        fakeServer.setTransitionReads(0);
        invokeAuthenticate();

        handler.setSystemMode(MODE_FULL);
        invokePoll();

        DiagralPollSnapshot snapshot = handler.captureSnapshot();
        assertThat(handler.getDisplayedMode(snapshot), equalTo(MODE_FULL));
        assertThat(handler.isGroupActive("2", snapshot), is(true));
    }

    /**
     * Directly activating a group (not via a named mode) end to end must settle to {@code GROUP} status
     * and be reflected by {@code isGroupActive()}'s settled-{@code GROUP}-status branch - the one finding
     * {@code b06c1ec1f} fixed after being "confirmed live" only by manually arming a group from the
     * official e-ONE app, per {@code CLAUDE.md}.
     */
    @Test
    public void directGroupActivationEndToEndReflectsInIsGroupActive() throws Exception {
        fakeServer.setTransitionReads(0);
        invokeAuthenticate();

        handler.activateGroup("3");
        invokePoll();

        DiagralPollSnapshot snapshot = handler.captureSnapshot();
        @Nullable
        String status = snapshot.status() != null ? snapshot.status().status : null;
        assertThat(status, equalTo(SYSTEM_STATUS_GROUP));
        assertThat(handler.isGroupActive("3", snapshot), is(true));
    }

    /**
     * Disabling a device end to end must invalidate the cached configuration and be reflected in the
     * bridge's {@code getAnomalies()} passthrough - the enable/disable + anomalies-verification chain
     * {@code DiagralHttpClient.actionProduct()} implements, now exercised through the bridge layer too.
     */
    @Test
    public void disablingADeviceEndToEndInvalidatesConfigurationCacheAndReflectsInAnomalies() throws Exception {
        invokeAuthenticate();
        // Warm the cache first, so the test can show it gets invalidated rather than just never populated.
        handler.getSystemConfiguration();

        handler.disableDevice(PRODUCT_TYPE_SENSOR, 9);
        // deviceCommand() invalidates the cache synchronously, before the async re-poll - safe to read
        // immediately.
        DiagralAnomalies anomalies = Objects.requireNonNull(handler.getAnomalies());

        assertThat(anomalies, is(notNullValue()));
        assertThat("device 9 was not reported inhibited in anomalies", Objects.requireNonNull(anomalies.sensors)
                .stream().anyMatch(d -> Integer.valueOf(9).equals(d.deviceIndex)), is(true));
    }

    /**
     * {@code dispose()}'s fire-and-forget API-key deletion (S2) must actually reach the server - previously
     * only "half-confirmed" live (see {@code CLAUDE.md}: "the superseded-key path still needs a 401 to
     * exercise" and "not live-observable"), since forcing it required deliberately breaking a live
     * account's credentials. Here it's just a normal dispose.
     */
    @Test
    public void disposeDeletesTheApiKeyThroughTheRealFakeServer() throws Exception {
        invokeAuthenticate();
        String apiKey = Objects.requireNonNull(fakeServer.getCurrentApiKey());

        handler.dispose();

        assertThat("dispose's fire-and-forget key deletion did not complete in time",
                waitUntil(() -> fakeServer.getDeletedApiKeys().contains(apiKey)), is(true));
    }

    /**
     * After {@code dispose()}, a poll must be a genuine no-op even against the real client/fake pair -
     * mirrors {@code DiagralBridgeHandlerTest}'s {@code pollIsANoOpOnceDisposed}, but here there is a real
     * (if fake) network underneath to prove nothing reaches it.
     *
     * <p>
     * Waits out {@code dispose()}'s own fire-and-forget key-deletion request first (see {@link
     * #disposeDeletesTheApiKeyThroughTheRealFakeServer}) so that background activity can't race with the
     * before/after request-count comparison below and produce a flaky false failure.
     * </p>
     */
    @Test
    public void pollIsANoOpOnceDisposedEvenWithARealClient() throws Exception {
        invokeAuthenticate();
        String apiKey = Objects.requireNonNull(fakeServer.getCurrentApiKey());

        handler.dispose();
        waitUntil(() -> fakeServer.getDeletedApiKeys().contains(apiKey));
        int requestsBeforePoll = fakeServer.getRequestLog().size();

        invokePoll();

        assertThat(fakeServer.getRequestLog().size(), equalTo(requestsBeforePoll));
    }
}
