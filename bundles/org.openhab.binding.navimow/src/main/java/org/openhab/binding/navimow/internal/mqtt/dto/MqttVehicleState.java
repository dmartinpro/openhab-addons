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

import org.eclipse.jdt.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

/**
 * {@link MqttVehicleState} is the payload of the {@code .../realtimeDate/state} MQTT topic pushed by
 * the Navimow cloud for one mower.
 *
 * <p>
 * <b>Live-confirmed 2026-09-15</b> against a real Navimow X430, across docked, running, and docking
 * states: {@code {"battery":85,"device_id":"...","state":"isRunning","timestamp":...}}. This
 * disproves the shape originally assumed from reading the {@code navimow-sdk} source (a nested
 * {@code position} object with {@code lat}/{@code lng}) - six live messages spanning idle, mowing, and
 * return-to-dock never once included a position field, and the sibling {@code event}/{@code attributes}
 * topics produced zero traffic during the same window. Position does not appear to be delivered over
 * this MQTT channel at all; only {@code deviceId} is left unmapped here since routing already happens
 * by topic, not by this field.
 *
 * <p>
 * {@code state} mirrors the same raw values REST's {@code vehicleState} field does (see
 * {@link org.openhab.binding.navimow.internal.api.dto.NavimowActivity#fromRawState}), which is why this
 * connection's actual value is push latency, not new data: a real state transition arrived here within
 * ~130ms of occurring, against a 60-second REST poll interval.
 *
 * @author David Martin - Initial contribution
 */
public class MqttVehicleState {

    @SerializedName("state")
    public @Nullable String vehicleState;

    public @Nullable Integer battery;
}
