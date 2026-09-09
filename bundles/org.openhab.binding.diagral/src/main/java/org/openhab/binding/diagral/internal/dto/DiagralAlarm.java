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

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

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
 * <p>
 * The only DTO in this bundle that was missing {@code @NonNullByDefault}; every field here is optional in
 * the real API response, and {@link #getId()} returning {@code null} for a partial one used to reach
 * {@code new ThingUID(...)} unchecked - see that method.
 * </p>
 *
 * @author David Martin - Initial contribution
 */
@NonNullByDefault
public class DiagralAlarm {

    /** The alarm system's configured name. */
    @SerializedName("name")
    public @Nullable String name;

    /** The box (central controller unit)'s identity, used by {@link #getId()} to derive this system's UID. */
    @SerializedName("box")
    public @Nullable Device box;

    /** The central alarm unit's identity. */
    @SerializedName("central")
    public @Nullable Device central;

    /**
     * Derives a short, stable identifier for this alarm system from the box's serial number.
     *
     * @return the first 6 characters of {@link #box}'s serial number, or {@code null} if the box or its
     *         serial number isn't available, or the serial number is too short to derive an id from
     */
    public @Nullable String getId() {
        Device currentBox = box;
        if (currentBox == null) {
            return null;
        }
        String serial = currentBox.serial;
        if (serial != null && serial.length() > 5) {
            return serial.substring(0, 6);
        }
        return null;
    }

    /**
     * The {@link Device} represents the shared name/serial/firmware shape used by both the {@link #box}
     * and {@link #central} fields of the enclosing {@link DiagralAlarm}.
     */
    @NonNullByDefault
    public static class Device {
        /** The unit's configured name. */
        @SerializedName("name")
        public @Nullable String name;
        /** The unit's serial number; {@link DiagralAlarm#getId()} derives this system's UID from it. */
        @SerializedName("serial")
        public @Nullable String serial;
        /** Firmware versions keyed by component name. */
        @SerializedName("firmwares")
        public @Nullable Map<String, String> firmwares;
    }
}
