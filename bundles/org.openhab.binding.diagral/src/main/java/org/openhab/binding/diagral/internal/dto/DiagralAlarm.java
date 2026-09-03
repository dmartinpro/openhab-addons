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
package org.openhab.binding.diagral.internal.dto;

import java.util.Map;

import com.google.gson.annotations.SerializedName;

/**
 * The {@link DiagralAlarm} represents the alarm system in the Diagral installation, as returned by the
 * {@code /systems/{serialId}/configurations} endpoint's {@code alarm} field.
 *
 * <p>
 * Used by {@code DiagralDiscoveryService.discoverAlarmSystem()} to build the discovered {@code
 * alarm-system} thing's UID (via {@link #getId()}) and label.
 * </p>
 *
 * @author David Martin - Initial contribution
 */
public class DiagralAlarm {

    @SerializedName("name")
    public String name;

    @SerializedName("box")
    public Device box;

    @SerializedName("central")
    public Device central;

    /**
     * Derives a short, stable identifier for this alarm system from the box's serial number.
     *
     * @return the first 6 characters of {@link #box}'s serial number, or {@code null} if the box or its
     *         serial number isn't available, or the serial number is too short to derive an id from
     */
    public String getId() {
        if (box != null && box.serial != null && box.serial.length() > 5) {
            return box.serial.substring(0, 6);
        }
        return null;
    }

    /**
     * The {@link Device} represents the shared name/serial/firmware shape used by both the {@link #box}
     * and {@link #central} fields of the enclosing {@link DiagralAlarm}.
     */
    public static class Device {
        @SerializedName("name")
        public String name;
        @SerializedName("serial")
        public String serial;
        @SerializedName("firmwares")
        public Map<String, String> firmwares;
    }
}
