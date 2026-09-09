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

import java.util.List;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

/**
 * The {@link DiagralAnomalyDetail} represents the anomalies reported for a single device, as found in
 * one of the per-category lists of a {@link DiagralAnomalies} response.
 *
 * <p>
 * {@link #deviceIndex} matches {@code DiagralDevice#deviceIndex} for the same physical device, which is
 * how {@code DiagralHttpClient.isDeviceInhibited()} correlates a device being acted on (by product type
 * + index) with its anomaly entry here to check for an {@code "inhibited"} entry in {@link
 * #anomalyNames}.
 * </p>
 *
 * @author David Martin - Initial contribution
 */
@NonNullByDefault
public class DiagralAnomalyDetail {

    /** The affected device's serial number. */
    @SerializedName("serial")
    public @Nullable String serial;

    /** The affected device's per-category numeric index, matched against {@code DiagralDevice#deviceIndex}. */
    @SerializedName("index")
    public @Nullable Integer deviceIndex;

    /** The numeric index of the group this device belongs to. */
    @SerializedName("group")
    public @Nullable Integer groupIndex;

    /** The affected device's human-readable label. */
    @SerializedName("label")
    public @Nullable String label;

    /** The specific anomaly codes reported for this device. */
    @SerializedName("anomaly_names")
    public @Nullable List<DiagralAnomalyName> anomalyNames;
}
