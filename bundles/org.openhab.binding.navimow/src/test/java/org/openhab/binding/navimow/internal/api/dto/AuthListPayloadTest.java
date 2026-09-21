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
        // Exact shape live-confirmed 2026-09-15 against a real Navimow X430.
        String json = """
                {
                  "code": 1,
                  "desc": "Operation successful",
                  "data": {
                    "requestId": "07ae0df4-e700-47f4-81d0-3cfde662f80d",
                    "payload": {
                      "devices": [
                        { "id": "EXAMPLE0DEVICE1", "name": "Navimow X430", "model": "X430", "firmware": "005D" }
                      ]
                    }
                  }
                }
                """;

        Type type = new TypeToken<NavimowApiEnvelope<AuthListPayload>>() {
        }.getType();
        NavimowApiEnvelope<AuthListPayload> envelope = gson.fromJson(json, type);

        assertThat(envelope.isSuccess(), is(true));
        assertThat(envelope.data.payload.devices, hasSize(1));

        NavimowDevice device = envelope.data.payload.devices.get(0);
        assertThat(device.id, is("EXAMPLE0DEVICE1"));
        assertThat(device.name, is("Navimow X430"));
        assertThat(device.model, is("X430"));
        assertThat(device.firmware, is("005D"));
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
