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
import static org.hamcrest.Matchers.nullValue;

import java.lang.reflect.Type;

import org.junit.jupiter.api.Test;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

/**
 * Tests for {@link NavimowDeviceStatus}, including Gson parsing of a realistic
 * {@code getVehicleStatus} response and the {@code capacityRemaining} battery extraction.
 *
 * @author David Martin - Initial contribution
 */
class NavimowDeviceStatusTest {

    private final Gson gson = new Gson();

    @Test
    void parsesRealisticGetVehicleStatusResponse() {
        String json = """
                {
                  "code": 1,
                  "desc": "success",
                  "data": {
                    "payload": {
                      "devices": [
                        {
                          "id": "device-1",
                          "vehicleState": "isMapping",
                          "capacityRemaining": [
                            { "unit": "PERCENTAGE", "rawValue": 87 }
                          ]
                        }
                      ]
                    }
                  }
                }
                """;

        Type type = new TypeToken<NavimowApiEnvelope<VehicleStatusPayload>>() {
        }.getType();
        NavimowApiEnvelope<VehicleStatusPayload> envelope = gson.fromJson(json, type);

        assertThat(envelope.isSuccess(), is(true));
        NavimowDeviceStatus status = envelope.data.payload.devices.get(0);
        assertThat(status.id, is("device-1"));
        assertThat(status.vehicleState, is("isMapping"));
        assertThat(status.getBatteryPercentage(), is(87));
    }

    @Test
    void getBatteryPercentageReturnsNullWhenNoPercentageUnitPresent() {
        NavimowDeviceStatus status = new NavimowDeviceStatus();
        CapacityRemainingItem other = new CapacityRemainingItem();
        other.unit = "SECONDS";
        other.rawValue = 42;
        status.capacityRemaining = java.util.List.of(other);

        assertThat(status.getBatteryPercentage(), nullValue());
    }

    @Test
    void getBatteryPercentageReturnsNullWhenListMissing() {
        NavimowDeviceStatus status = new NavimowDeviceStatus();
        status.capacityRemaining = null;

        assertThat(status.getBatteryPercentage(), nullValue());
    }

    @Test
    void getBatteryPercentageIsCaseInsensitiveOnUnit() {
        NavimowDeviceStatus status = new NavimowDeviceStatus();
        CapacityRemainingItem percentage = new CapacityRemainingItem();
        percentage.unit = "percentage";
        percentage.rawValue = 55;
        status.capacityRemaining = java.util.List.of(percentage);

        assertThat(status.getBatteryPercentage(), is(55));
    }
}
