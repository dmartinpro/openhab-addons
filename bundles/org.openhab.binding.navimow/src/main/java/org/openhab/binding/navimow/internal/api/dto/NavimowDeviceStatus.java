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

import java.util.List;

import org.eclipse.jdt.annotation.Nullable;

/**
 * {@link NavimowDeviceStatus} represents one device entry returned by the {@code getVehicleStatus}
 * endpoint.
 *
 * <p>
 * {@code id}, {@code vehicleState} and {@code capacityRemaining} are confirmed against a real
 * payload - an independent, non-defensive Home Assistant integration reads these three fields
 * directly off this exact endpoint's response. {@code position} and {@code signal_strength} are
 * deliberately not modelled here: the same source only ever populates those two fields from MQTT
 * push messages, never from this REST endpoint, so their presence here is unconfirmed and this
 * binding does not yet use MQTT.
 *
 * @author David Martin - Initial contribution
 */
public class NavimowDeviceStatus {

    public @Nullable String id;

    /** Raw cloud state string, e.g. {@code isDocked}, {@code isMapping}, {@code Error}, ... */
    public @Nullable String vehicleState;

    public @Nullable List<CapacityRemainingItem> capacityRemaining;

    /**
     * Extracts the battery percentage from {@link #capacityRemaining}.
     *
     * @return the battery percentage, or {@code null} if no {@code PERCENTAGE}-unit entry is present
     */
    public @Nullable Integer getBatteryPercentage() {
        List<CapacityRemainingItem> items = capacityRemaining;
        if (items == null) {
            return null;
        }
        for (CapacityRemainingItem item : items) {
            if ("PERCENTAGE".equalsIgnoreCase(item.unit)) {
                return item.rawValue;
            }
        }
        return null;
    }
}
