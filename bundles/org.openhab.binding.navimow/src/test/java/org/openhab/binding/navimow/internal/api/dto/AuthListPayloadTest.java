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
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

import java.lang.reflect.Type;

import org.junit.jupiter.api.Test;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

/**
 * Tests for Gson parsing of a realistic {@code authList} response.
 *
 * @author David Martin - Initial contribution
 */
class AuthListPayloadTest {

    private final Gson gson = new Gson();

    @Test
    void parsesRealisticAuthListResponse() {
        String json = """
                {
                  "code": 1,
                  "desc": "success",
                  "data": {
                    "payload": {
                      "devices": [
                        { "id": "device-1", "name": "Front Lawn", "online": true },
                        { "id": "device-2", "name": "Back Lawn", "online": false }
                      ]
                    }
                  }
                }
                """;

        Type type = new TypeToken<NavimowApiEnvelope<AuthListPayload>>() {
        }.getType();
        NavimowApiEnvelope<AuthListPayload> envelope = gson.fromJson(json, type);

        assertThat(envelope.isSuccess(), is(true));
        assertThat(envelope.data.payload.devices, hasSize(2));

        NavimowDevice first = envelope.data.payload.devices.get(0);
        assertThat(first.id, is("device-1"));
        assertThat(first.name, is("Front Lawn"));
        assertThat(first.online, is(true));

        NavimowDevice second = envelope.data.payload.devices.get(1);
        assertThat(second.online, is(false));
    }

    @Test
    void nonSuccessCodeIsReflectedByIsSuccess() {
        String json = """
                {
                  "code": 0,
                  "desc": "invalid token",
                  "data": null
                }
                """;

        Type type = new TypeToken<NavimowApiEnvelope<AuthListPayload>>() {
        }.getType();
        NavimowApiEnvelope<AuthListPayload> envelope = gson.fromJson(json, type);

        assertThat(envelope.isSuccess(), is(false));
        assertThat(envelope.desc, is("invalid token"));
    }
}
