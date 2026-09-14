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
package org.openhab.binding.navimow.internal.api.dto;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Tests for {@link NavimowActivity}'s raw-state normalization.
 *
 * @author David Martin - Initial contribution
 */
class NavimowActivityTest {

    @ParameterizedTest
    @CsvSource({ "isDocked, DOCKED", "isIdel, IDLE", "isIdle, IDLE", "isMapping, MOWING", "isRunning, MOWING",
            "isPaused, PAUSED", "isDocking, RETURNING", "Error, ERROR", "error, ERROR", "isLifted, ERROR",
            "inSoftwareUpdate, PAUSED", "Self-Checking, IDLE", "Self-checking, IDLE", "Offline, UNKNOWN",
            "offline, UNKNOWN" })
    void mapsKnownRawStatesToCanonicalActivity(String rawState, NavimowActivity expected) {
        assertThat(NavimowActivity.fromRawState(rawState), is(expected));
    }

    @Test
    void mapsNullToUnknown() {
        assertThat(NavimowActivity.fromRawState(null), is(NavimowActivity.UNKNOWN));
    }

    @Test
    void mapsUnrecognizedRawStateToUnknown() {
        assertThat(NavimowActivity.fromRawState("SomeFutureStateNotYetSeen"), is(NavimowActivity.UNKNOWN));
    }

    @Test
    void mappingIsCaseSensitive() {
        // "ERROR" (all caps) is not one of the raw spellings actually observed ("Error"/"error"),
        // so it must not be silently accepted - a real behaviour change upstream should surface as
        // UNKNOWN, not be masked by an overly lenient case-insensitive match.
        assertThat(NavimowActivity.fromRawState("ERROR"), is(NavimowActivity.UNKNOWN));
    }
}
