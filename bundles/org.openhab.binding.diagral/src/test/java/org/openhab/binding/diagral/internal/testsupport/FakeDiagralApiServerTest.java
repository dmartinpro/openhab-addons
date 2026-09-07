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
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.openhab.binding.diagral.internal.DiagralBindingConstants.MODE_FULL;

import java.util.List;
import java.util.Objects;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jetty.client.HttpClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openhab.binding.diagral.internal.bridge.DiagralAuthenticationManager;
import org.openhab.binding.diagral.internal.bridge.DiagralHttpClient;
import org.openhab.binding.diagral.internal.dto.DiagralSystemConfiguration;
import org.openhab.binding.diagral.internal.dto.DiagralSystemDetails;
import org.openhab.binding.diagral.internal.dto.DiagralSystemStatus;
import org.openhab.binding.diagral.internal.exception.DiagralApiException;
import org.openhab.binding.diagral.internal.exception.DiagralAuthenticationException;

/**
 * Proves out {@link FakeDiagralApiServer} (Phase 1 of the Diagral mock-API plan: authentication and the
 * three read-only "fetch state" endpoints) by driving the real {@link DiagralHttpClient}/
 * {@link DiagralAuthenticationManager} against it - no network, no mocked business logic beyond the
 * Jetty transport seam itself.
 *
 * <p>
 * Doubles as the template for later phases: every test below talks to the production client classes
 * exactly the way {@code DiagralBridgeHandler} does, so once mode/group state machinery is added to the
 * fake, bridge-level integration tests can be written the same way.
 * </p>
 *
 * @author David Martin - Initial contribution
 */
@NonNullByDefault
public class FakeDiagralApiServerTest {

    private static final String USERNAME = "user@example.test";
    private static final String PASSWORD = "correct-password";
    private static final String SERIAL_ID = "SERIAL1";
    private static final String PIN_CODE = "1234";

    private @NonNullByDefault({}) FakeDiagralApiServer fakeServer;
    private @NonNullByDefault({}) DiagralHttpClient client;

    /**
     * Builds a fresh fake server and a real {@link DiagralHttpClient} wired to it for each test, so tests
     * don't share authentication state.
     */
    @BeforeEach
    public void setUp() {
        fakeServer = new FakeDiagralApiServer(USERNAME, PASSWORD, SERIAL_ID, PIN_CODE);
        HttpClient mockedHttpClient = fakeServer.asMockedHttpClient();
        DiagralAuthenticationManager authManager = new DiagralAuthenticationManager(USERNAME, PASSWORD, SERIAL_ID,
                PIN_CODE);
        client = new DiagralHttpClient(mockedHttpClient, authManager);
    }

    /**
     * The full login -> api_key flow should succeed against matching credentials and leave the fake
     * holding a current API key.
     */
    @Test
    public void authenticateSucceedsWithCorrectCredentials() throws Exception {
        client.authenticate();

        assertThat(fakeServer.getCurrentApiKey(), not(equalTo(null)));
    }

    /**
     * Wrong credentials must surface as an authentication failure, not silently succeed or throw some
     * other exception type.
     */
    @Test
    public void authenticateFailsWithWrongPassword() {
        DiagralAuthenticationManager wrongPassword = new DiagralAuthenticationManager(USERNAME, "not-the-password",
                SERIAL_ID, PIN_CODE);
        DiagralHttpClient clientWithWrongPassword = new DiagralHttpClient(fakeServer.asMockedHttpClient(),
                wrongPassword);

        assertThrows(DiagralAuthenticationException.class, clientWithWrongPassword::authenticate);
    }

    /**
     * Once authenticated, {@code getSystemStatus()} should return exactly what the fake was configured
     * with, round-tripped through real HMAC signing and JSON parsing.
     */
    @Test
    public void getSystemStatusReturnsConfiguredFixture() throws Exception {
        DiagralSystemStatus configured = new DiagralSystemStatus();
        configured.status = MODE_FULL;
        configured.activatedGroups = List.of(1, 2);
        fakeServer.setSystemStatus(configured);

        client.authenticate();
        DiagralSystemStatus status = client.getSystemStatus();

        assertThat(status.status, equalTo(MODE_FULL));
        assertThat(status.activatedGroups, equalTo(List.of(1, 2)));
    }

    /**
     * {@code getSystemConfiguration()} must round-trip the configured fixture too, on the endpoint that
     * (unlike status/details) does not require a PIN code.
     */
    @Test
    public void getSystemConfigurationReturnsConfiguredFixture() throws Exception {
        DiagralSystemConfiguration configured = new DiagralSystemConfiguration();
        configured.installationState = 1;
        configured.presenceGroup = List.of(3);
        fakeServer.setSystemConfiguration(configured);

        client.authenticate();
        DiagralSystemConfiguration configuration = client.getSystemConfiguration();

        assertThat(configuration.presenceGroup, equalTo(List.of(3)));
    }

    /**
     * {@code getSystemDetails()} must round-trip the configured fixture, on the PascalCase-keyed details
     * endpoint.
     */
    @Test
    public void getSystemDetailsReturnsConfiguredFixture() throws Exception {
        DiagralSystemDetails configured = new DiagralSystemDetails();
        configured.deviceType = "DIAG56AAX";
        fakeServer.setSystemDetails(configured);

        client.authenticate();
        DiagralSystemDetails details = client.getSystemDetails();

        assertThat(details.deviceType, equalTo("DIAG56AAX"));
    }

    /**
     * A signed request with the wrong PIN code must fail, via the fake's {@code 403} response for a PIN
     * mismatch (see {@link FakeDiagralApiServer}'s class Javadoc caveat on that choice). Note this
     * surfaces as {@link DiagralAuthenticationException}, not {@link DiagralApiException}: production
     * {@code DiagralHttpClient.executeHttpRequest()} treats every 401/403 as an authentication failure
     * regardless of cause, so a wrong PIN code and an expired API key are indistinguishable to callers -
     * this test exists specifically to pin down that (possibly surprising) real interaction.
     */
    @Test
    public void getSystemStatusFailsWithWrongPinCode() throws Exception {
        client.authenticate();
        DiagralAuthenticationManager wrongPin = new DiagralAuthenticationManager(USERNAME, PASSWORD, SERIAL_ID, "9999");
        // Deliberately reuse this test's already-authenticated fake server state but sign with a
        // different manager's (wrong) PIN code, by driving a second client against the same fake.
        DiagralHttpClient clientWithWrongPin = new DiagralHttpClient(fakeServer.asMockedHttpClient(), wrongPin);
        clientWithWrongPin.authenticate();

        assertThrows(DiagralAuthenticationException.class, clientWithWrongPin::getSystemStatus);
    }

    /**
     * Re-authenticating (e.g. after a 401 clears the stored keys) must delete the superseded API key
     * server-side, exactly the S2 behaviour this fake was built to let integration tests verify.
     */
    @Test
    public void reAuthenticationDeletesSupersededApiKey() throws Exception {
        client.authenticate();
        String firstApiKey = Objects.requireNonNull(fakeServer.getCurrentApiKey());

        client.authenticate();

        assertThat(fakeServer.getDeletedApiKeys(), hasItem(firstApiKey));
        assertThat(fakeServer.getCurrentApiKey(), not(equalTo(firstApiKey)));
    }
}
