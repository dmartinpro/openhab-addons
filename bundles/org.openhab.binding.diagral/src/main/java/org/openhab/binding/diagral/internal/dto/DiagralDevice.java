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
 * The {@link DiagralDevice} represents a single device in the Diagral system - a sensor, siren, keypad
 * ("command"), transmitter, or camera, all of which share this same JSON shape in the {@code
 * /systems/{serialId}/configurations} response (see {@code DiagralSystemConfiguration}'s per-category
 * list fields).
 *
 * <p>
 * Not every field is populated for every category - notably {@link #id} (the API's {@code uid}) is only
 * present for sensors; sirens, keypads, and transmitters are identified only by {@link #serial} (see
 * {@link #getUniqueId()}). {@link #deviceIndex} is the per-category numeric index used by the
 * enable/disable API (see {@code DiagralHttpClient.actionProduct()}), distinct from {@link #groupIndex}
 * (which group the device belongs to) and {@link #id}/{@link #serial} (device identity).
 * </p>
 *
 * @author David Martin - Initial contribution
 */
@NonNullByDefault
public class DiagralDevice {

    /** The device's unique ID; only present for sensors - see {@link #getUniqueId()}. */
    @SerializedName("uid")
    public @Nullable String id;

    /** The device's category-level type string. */
    @SerializedName("type")
    public @Nullable String type;

    /** The device's product range/family. */
    @SerializedName("gamme")
    public @Nullable String gamme;

    /** The device's refCode-derived subtype, used to classify sensors during discovery. */
    @SerializedName("subtype")
    public @Nullable String subtype;

    /** The device's configured label/name. */
    @SerializedName("label")
    public @Nullable String name;

    // NOTE: refCode appears in serialId. SerialId format is ....XXXX...... where XXXX is refCode
    /** The device's hardware reference code, used to classify sensors during discovery. */
    @SerializedName("refCode")
    public @Nullable String refCode;

    /** Whether the device is currently inhibited (disabled). */
    @SerializedName("inhibited")
    public boolean inhibited;

    /** Whether this device supports being inhibited/enabled at all. */
    @SerializedName("canInhibit")
    public boolean canInhibit;

    /** The numeric index of the group this device belongs to. */
    @SerializedName("group")
    public @Nullable Integer groupIndex;

    /** The device's per-category numeric index, used for enable/disable API calls. */
    @SerializedName("index")
    public @Nullable Integer deviceIndex;

    /**
     * The device's serial number; the only identifier for sirens, keypads and transmitters - see
     * {@link #getUniqueId()}.
     */
    @SerializedName("serial")
    public @Nullable String serial;

    /** Per-device anomaly flags, keyed by alert name (e.g. {@code powerSupplyAlert}). */
    @SerializedName("anomalies")
    public @Nullable Map<String, Boolean> anomalies;

    /** Whether this transmitter is a smart plug, distinguishing the {@code plug} thing type from a generic one. */
    @SerializedName("isPlug")
    public @Nullable Boolean isPlug;

    /** Whether this device is a video-capable camera. */
    @SerializedName("isVideo")
    public @Nullable Boolean isVideo;

    /** The date this device was installed. */
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
