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
 * The {@link DiagralDevice} represents a device in the Diagral system.
 *
 * @author David Martin - Initial contribution
 */
@NonNullByDefault
public class DiagralDevice {

    @SerializedName("uid")
    public @Nullable String id;

    @SerializedName("type")
    public @Nullable String type;

    @SerializedName("gamme")
    public @Nullable String gamme;

    @SerializedName("subtype")
    public @Nullable String subtype;

    @SerializedName("label")
    public @Nullable String name;

    // NOTE: refCode appears in serialId. SerialId format is ....XXXX...... where XXXX is refCode
    @SerializedName("refCode")
    public @Nullable String refCode;

    @SerializedName("inhibited")
    public boolean inhibited;

    @SerializedName("canInhibit")
    public boolean canInhibit;

    @SerializedName("group")
    public @Nullable Integer groupIndex;

    @SerializedName("index")
    public @Nullable Integer deviceIndex;

    @SerializedName("serial")
    public @Nullable String serial;

    @SerializedName("anomalies")
    public @Nullable Map<String, Boolean> anomalies;

    @SerializedName("isPlug")
    public @Nullable Boolean isPlug;

    @SerializedName("isVideo")
    public @Nullable Boolean isVideo;

    @SerializedName("installationDate")
    public @Nullable String installationDate;

    /**
     * Gets a unique identifier for this device.
     *
     * <p>
     * Sensors have a {@code uid}, but sirens, keypads (the API's "commands" category), and transmitters
     * don't - the API only identifies them by their {@code serial} number. Use this instead of {@link #id}
     * directly wherever a device needs to be identified, so it works across every device category.
     * </p>
     *
     * @return {@link #id} if present, otherwise {@link #serial}, or null if neither is available
     */
    public @Nullable String getUniqueId() {
        return id != null ? id : serial;
    }
}
