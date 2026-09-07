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
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.openhab.binding.diagral.internal.DiagralBindingConstants.MODE_FULL;
import static org.openhab.binding.diagral.internal.DiagralBindingConstants.MODE_OFF;
import static org.openhab.binding.diagral.internal.DiagralBindingConstants.MODE_PARTIAL2;
import static org.openhab.binding.diagral.internal.DiagralBindingConstants.MODE_PRESENCE;
import static org.openhab.binding.diagral.internal.DiagralBindingConstants.MODE_TEMPO_GROUP;
import static org.openhab.binding.diagral.internal.DiagralBindingConstants.SYSTEM_STATUS_GROUP;

import java.util.List;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openhab.binding.diagral.internal.bridge.DiagralAuthenticationManager;
import org.openhab.binding.diagral.internal.bridge.DiagralHttpClient;
import org.openhab.binding.diagral.internal.dto.DiagralSystemStatus;

/**
 * Proves out {@link FakeDiagralApiServer}'s Phase 2 arm/disarm state machine by driving the real
 * {@link DiagralHttpClient} through mode and group commands, exactly the way
 * {@code DiagralBridgeHandler.setSystemMode()}/{@code activateGroup()}/{@code disableGroup()} do, and
 * asserting the transitional-then-settled {@code status} sequence this fake reproduces matches the
 * live-confirmed quirks documented on {@code DiagralBindingConstants}.
 *
 * @author David Martin - Initial contribution
 */
@NonNullByDefault
public class FakeDiagralApiServerModeTransitionsTest {

    private static final String USERNAME = "user@example.test";
    private static final String PASSWORD = "correct-password";
    private static final String SERIAL_ID = "SERIAL1";
    private static final String PIN_CODE = "1234";

    private @NonNullByDefault({}) FakeDiagralApiServer fakeServer;
    private @NonNullByDefault({}) DiagralHttpClient client;

    /**
     * Builds a fresh, already-authenticated fake server and client for each test - every test here cares
     * about post-authentication command/status behaviour, not authentication itself (see
     * {@link FakeDiagralApiServerTest} for that).
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
     * Arming {@code FULL} must go through one {@code TEMPO_GROUP} read with empty {@code activated_groups}
     * before settling to {@code FULL} - also with empty {@code activated_groups}, per the live-confirmed
     * finding that the field is never trustworthy for a named mode.
     */
    @Test
    public void armingFullGoesThroughTempoGroupBeforeSettling() throws Exception {
        client.setSystemMode(MODE_FULL);

        DiagralSystemStatus transitional = client.getSystemStatus();
        assertThat(transitional.status, equalTo(MODE_TEMPO_GROUP));
        assertThat(transitional.activatedGroups, equalTo(List.of()));

        DiagralSystemStatus settled = client.getSystemStatus();
        assertThat(settled.status, equalTo(MODE_FULL));
        assertThat(settled.activatedGroups, empty());
    }

    /**
     * Arming {@code PRESENCE} must go through one {@code TEMPO_1} read before settling to
     * {@code PRESENCE}, per {@code DiagralBindingConstants.MODE_TEMPO_GROUP}'s Javadoc ("heading to
     * PARTIAL1+PRESENCE").
     */
    @Test
    public void armingPresenceGoesThroughTempo1BeforeSettling() throws Exception {
        client.setSystemMode(MODE_PRESENCE);

        assertThat(client.getSystemStatus().status, equalTo("TEMPO_1"));
        assertThat(client.getSystemStatus().status, equalTo(MODE_PRESENCE));
    }

    /**
     * Arming {@code PARTIAL2} must go through one {@code TEMPO_2} read before settling to
     * {@code PARTIAL2}.
     */
    @Test
    public void armingPartial2GoesThroughTempo2BeforeSettling() throws Exception {
        client.setSystemMode(MODE_PARTIAL2);

        assertThat(client.getSystemStatus().status, equalTo("TEMPO_2"));
        assertThat(client.getSystemStatus().status, equalTo(MODE_PARTIAL2));
    }

    /**
     * Disarming ({@code OFF}) settles immediately with no transitional window, even from a fully-armed
     * state - matches production code never treating {@code OFF} as delayed.
     */
    @Test
    public void disarmingSettlesImmediately() throws Exception {
        client.setSystemMode(MODE_FULL);
        client.getSystemStatus();
        client.getSystemStatus();

        client.setSystemMode(MODE_OFF);

        DiagralSystemStatus status = client.getSystemStatus();
        assertThat(status.status, equalTo(MODE_OFF));
        assertThat(status.activatedGroups, empty());
    }

    /**
     * Directly activating one group must go through {@code TEMPO_GROUP} before settling to
     * {@code GROUP} status - and only once settled at {@code GROUP} is {@code activated_groups} populated,
     * per {@code DiagralBindingConstants.SYSTEM_STATUS_GROUP}'s Javadoc.
     */
    @Test
    public void activatingGroupSettlesToGroupStatusWithActivatedGroups() throws Exception {
        client.activateGroup("2");

        DiagralSystemStatus transitional = client.getSystemStatus();
        assertThat(transitional.status, equalTo(MODE_TEMPO_GROUP));
        assertThat(transitional.activatedGroups, equalTo(List.of()));

        DiagralSystemStatus settled = client.getSystemStatus();
        assertThat(settled.status, equalTo(SYSTEM_STATUS_GROUP));
        assertThat(settled.activatedGroups, equalTo(List.of(2)));
    }

    /**
     * Disabling the only directly-activated group settles back to {@code OFF} (this fake's documented
     * simulation choice: disable is instant, not transitional - see {@link FakeDiagralApiServer}'s Javadoc
     * on {@code handleDisableGroup}).
     */
    @Test
    public void disablingTheLastActiveGroupSettlesToOff() throws Exception {
        client.activateGroup("2");
        client.getSystemStatus();
        client.getSystemStatus();

        client.disableGroup("2");

        DiagralSystemStatus status = client.getSystemStatus();
        assertThat(status.status, equalTo(MODE_OFF));
        assertThat(status.activatedGroups, empty());
    }

    /**
     * Disabling one of several directly-activated groups settles back to {@code GROUP} status with the
     * remaining group(s) still listed.
     */
    @Test
    public void disablingOneOfSeveralActiveGroupsLeavesTheOthersActive() throws Exception {
        client.activateGroup("2");
        client.getSystemStatus();
        client.getSystemStatus();
        client.activateGroup("3");
        client.getSystemStatus();
        client.getSystemStatus();

        client.disableGroup("2");

        DiagralSystemStatus status = client.getSystemStatus();
        assertThat(status.status, equalTo(SYSTEM_STATUS_GROUP));
        assertThat(status.activatedGroups, equalTo(List.of(3)));
    }

    /**
     * With transitional reads configured to zero, a command settles on the very first {@code status} read
     * afterward - lets a test skip the transitional window entirely when only the end state matters.
     */
    @Test
    public void zeroTransitionReadsSettlesOnTheFirstRead() throws Exception {
        fakeServer.setTransitionReads(0);

        client.setSystemMode(MODE_FULL);

        assertThat(client.getSystemStatus().status, equalTo(MODE_FULL));
    }

    /**
     * Arming a named mode while a group is directly active must supersede it, not layer onto it - mirrors
     * production {@code DiagralBridgeHandler.setSystemMode()} arming the whole system rather than adding to
     * an in-progress direct group activation.
     */
    @Test
    public void armingNamedModeSupersedesADirectlyActivatedGroup() throws Exception {
        client.activateGroup("2");
        client.getSystemStatus();
        client.getSystemStatus();

        client.setSystemMode(MODE_FULL);
        client.getSystemStatus();

        DiagralSystemStatus settled = client.getSystemStatus();
        assertThat(settled.status, equalTo(MODE_FULL));
        assertThat(settled.activatedGroups, empty());
    }
}
