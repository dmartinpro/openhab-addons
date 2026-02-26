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
 * The {@link DiagralAlarm} represents the alarm system in the Diagral installation.
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

    public String getId() {
        if (box != null && box.serial != null && box.serial.length() > 5) {
            return box.serial.substring(0, 6);
        }
        return null;
    }

    public static class Device {
        @SerializedName("name")
        public String name;
        @SerializedName("serial")
        public String serial;
        @SerializedName("firmwares")
        public Map<String, String> firmwares;
    }
}
