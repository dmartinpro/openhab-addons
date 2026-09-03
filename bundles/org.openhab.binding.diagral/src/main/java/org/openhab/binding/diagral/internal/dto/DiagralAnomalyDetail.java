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
 * The {@link DiagralAnomalyDetail} represents the anomalies reported for a single device.
 *
 * @author David Martin - Initial contribution
 */
@NonNullByDefault
public class DiagralAnomalyDetail {

    @SerializedName("serial")
    public @Nullable String serial;

    @SerializedName("index")
    public @Nullable Integer deviceIndex;

    @SerializedName("group")
    public @Nullable Integer groupIndex;

    @SerializedName("label")
    public @Nullable String label;

    @SerializedName("anomaly_names")
    public @Nullable List<DiagralAnomalyName> anomalyNames;
}
