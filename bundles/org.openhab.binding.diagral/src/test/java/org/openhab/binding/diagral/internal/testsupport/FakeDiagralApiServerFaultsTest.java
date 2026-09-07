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
package org.openhab.binding.diagral.internal.testsupport;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.openhab.binding.diagral.internal.DiagralBindingConstants.PRODUCT_TYPE_SENSOR;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openhab.binding.diagral.internal.bridge.DiagralAuthenticationManager;
import org.openhab.binding.diagral.internal.bridge.DiagralHttpClient;
import org.openhab.binding.diagral.internal.dto.DiagralAnomalies;
import org.openhab.binding.diagral.internal.exception.DiagralApiException;
import org.openhab.binding.diagral.internal.exception.DiagralException;

/**
 * Proves out {@link FakeDiagralApiServer}'s Phase 3 additions - device enable/disable, the
 * {@code anomalies} endpoint, and general-purpose fault injection - by driving the real
 * {@link DiagralHttpClient} through them.
 *
 * @author David Martin - Initial contribution
 */
@NonNullByDefault
public class FakeDiagralApiServerFaultsTest {

    private static final String USERNAME = "user@example.test";
    private static final String PASSWORD = "correct-password";
    private static final String SERIAL_ID = "SERIAL1";
    private static final String PIN_CODE = "1234";

    private @NonNullByDefault({}) FakeDiagralApiServer fakeServer;
    private @NonNullByDefault({}) DiagralHttpClient client;

    /**
     * Builds a fresh, already-authenticated fake server and client for each test.
     */
    @BeforeEach
    public void setUp() throws Exception {
        fakeServer = new FakeDiagralApiServer(USERNAME, PASSWORD, SERIAL_ID, PIN_CODE);
        DiagralAuthenticationManager authManager = new DiagralAuthenticationManager(USERNAME, PASSWORD, SERIAL_ID,
                PIN_CODE);
        client = new DiagralHttpClient(fakeServer.asMockedHttpClient(), authManager);
        client.authenticate();
    }

    /**
     * With nothing inhibited, {@code anomalies} must come back empty - the fake's {@code 404} translated
     * by production {@code DiagralHttpClient.getAnomalies()} into an empty {@link DiagralAnomalies}, not
     * an error.
     */
    @Test
    public void anomaliesIsEmptyWhenNothingIsInhibited() throws Exception {
        DiagralAnomalies anomalies = client.getAnomalies();

        assertThat(anomalies.getTotalCount(), equalTo(0));
    }

    /**
     * Disabling a sensor must be reflected in {@code anomalies} as an "inhibited" entry for that device's
     * index, and re-enabling it must clear it again.
     */
    @Test
    public void disablingAndEnablingADeviceIsReflectedInAnomalies() throws Exception {
        client.disableProduct(PRODUCT_TYPE_SENSOR, 3);

        DiagralAnomalies afterDisable = client.getAnomalies();
        assertThat(afterDisable.sensors, hasSize(1));
        assertThat(afterDisable.sensors.get(0).deviceIndex, equalTo(3));

        client.enableProduct(PRODUCT_TYPE_SENSOR, 3);

        DiagralAnomalies afterEnable = client.getAnomalies();
        assertThat(afterEnable.getTotalCount(), equalTo(0));
    }

    /**
     * The documented Diagral quirk: an enable/disable call can return an empty-bodied {@code 500} despite
     * having actually applied the change server-side. Production {@code DiagralHttpClient.actionProduct()}
     * detects this by re-checking the device's real state via {@code anomalies} and treats a confirmed
     * match as success rather than propagating the error - this must not throw.
     */
    @Test
    public void enableDisableSurvivesTheServerErrorButAppliedQuirk() throws Exception {
        fakeServer.injectServerErrorButApplyOnEnableDisable();

        client.disableProduct(PRODUCT_TYPE_SENSOR, 5);

        DiagralAnomalies anomalies = client.getAnomalies();
        assertThat(anomalies.sensors, hasSize(1));
        assertThat(anomalies.sensors.get(0).deviceIndex, equalTo(5));
    }

    /**
     * A genuine {@code 500} that did <em>not</em> apply server-side (no quirk flag set) must still
     * propagate as a real failure - the fake's default enable/disable behaviour is either full success or
     * (only when the quirk flag is set) a misleading-but-actually-applied error, never a silent no-op.
     * This test instead checks the more common genuine failure path: an injected generic {@code 500} on
     * the enable/disable call itself, with the device never having been touched.
     */
    @Test
    public void genuineServerErrorOnEnableDisablePropagates() {
        fakeServer.injectHttpStatus(500);

        assertThrows(DiagralApiException.class, () -> client.disableProduct(PRODUCT_TYPE_SENSOR, 7));
    }

    /**
     * {@link FakeDiagralApiServer#injectTimeout()} must surface to a {@code DiagralHttpClient} caller as a
     * plain {@link DiagralException}, exactly like a real Jetty request timeout would - not swallowed, not
     * mis-typed as an authentication or API error.
     */
    @Test
    public void injectedTimeoutSurfacesAsDiagralException() {
        fakeServer.injectTimeout();

        DiagralException exception = assertThrows(DiagralException.class, client::getSystemStatus);
        assertThat(exception.getClass(), equalTo(DiagralException.class));
    }

    /**
     * {@link FakeDiagralApiServer#injectConnectionFailure()} must likewise surface as a plain
     * {@link DiagralException}, mirroring the {@code EOFException}s this bundle has observed live.
     */
    @Test
    public void injectedConnectionFailureSurfacesAsDiagralException() {
        fakeServer.injectConnectionFailure();

        DiagralException exception = assertThrows(DiagralException.class, client::getSystemStatus);
        assertThat(exception.getClass(), equalTo(DiagralException.class));
    }

    /**
     * An injected fault is one-shot: it applies to exactly the next request and then this fake goes back
     * to normal routing, so a retried call succeeds.
     */
    @Test
    public void injectedFaultAppliesOnlyOnce() throws Exception {
        fakeServer.injectTimeout();

        assertThrows(DiagralException.class, client::getSystemStatus);
        // No fault queued for this second call - should succeed normally.
        client.getSystemStatus();
    }

    /**
     * {@link FakeDiagralApiServer#injectHttpStatus(int)} must produce exactly the requested status
     * regardless of which endpoint the next request happens to target.
     */
    @Test
    public void injectedHttpStatusOverridesTheNextResponse() {
        fakeServer.injectHttpStatus(429);

        DiagralApiException exception = assertThrows(DiagralApiException.class, client::getSystemStatus);
        assertThat(exception.getStatusCode(), equalTo(429));
    }
}
