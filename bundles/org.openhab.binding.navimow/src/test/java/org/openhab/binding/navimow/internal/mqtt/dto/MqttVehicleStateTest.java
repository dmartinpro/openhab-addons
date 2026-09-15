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
package org.openhab.binding.navimow.internal.mqtt.dto;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.Test;

import com.google.gson.Gson;

/**
 * Tests for {@link MqttVehicleState} Gson parsing, using a real payload captured live 2026-09-15 - see
 * the class Javadoc for how it was captured and what it disproved.
 *
 * @author David Martin - Initial contribution
 */
class MqttVehicleStateTest {

    private final Gson gson = new Gson();

    @Test
    void parsesRealisticStateTopicPayload() {
        // Captured live 2026-09-15 from a real Navimow X430's `state` topic while mowing.
        String json = """
                {"battery":85,"device_id":"22AAD2602Y0911","state":"isRunning","timestamp":1789471712448}
                """;

        MqttVehicleState state = gson.fromJson(json, MqttVehicleState.class);

        assertThat(state.vehicleState, is("isRunning"));
        assertThat(state.battery, is(85));
    }
}
