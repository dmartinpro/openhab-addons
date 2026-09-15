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
package org.openhab.binding.navimow.internal.mqtt;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.openhab.binding.navimow.internal.api.dto.MqttUserInfo;
import org.openhab.binding.navimow.internal.api.exceptions.NavimowCommunicationException;

/**
 * Tests for {@link NavimowMqttConnection}.
 *
 * <p>
 * Actually opening a connection needs a real WebSocket MQTT broker - out of scope for a unit test, and
 * per this binding's process-binding finding, not something a standalone test could validate anyway
 * (see the class Javadoc). These tests instead cover what is deterministic and broker-independent: the
 * topic format, and the pre-connect validation of {@code mqtt/userInfo}'s fields.
 *
 * @author David Martin - Initial contribution
 */
class NavimowMqttConnectionTest {

    private final NavimowMqttConnection connection = new NavimowMqttConnection(
            (deviceId, state) -> fail("listener should not be invoked in these tests"));

    @Test
    void buildStateTopicFollowsExpectedFormat() {
        assertThat(NavimowMqttConnection.buildStateTopic("22AAD2602Y0911"),
                is("/downlink/vehicle/22AAD2602Y0911/realtimeDate/state"));
    }

    @Test
    void connectRejectsMissingMqttHost() {
        MqttUserInfo info = new MqttUserInfo();
        info.mqttUrl = "/mqtt/5329644";

        assertThrows(NavimowCommunicationException.class,
                () -> connection.connect(info, "token", List.of("22AAD2602Y0911")));
    }

    @Test
    void connectRejectsMissingMqttUrl() {
        MqttUserInfo info = new MqttUserInfo();
        info.mqttHost = "wss://mqtt-fra.navimow.com";

        assertThrows(NavimowCommunicationException.class,
                () -> connection.connect(info, "token", List.of("22AAD2602Y0911")));
    }

    private static void fail(String message) {
        throw new AssertionError(message);
    }
}
