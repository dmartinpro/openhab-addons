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
 * The {@link DiagralAnomalies} represents the anomalies currently reported across all device
 * categories of a Diagral system.
 *
 * @author David Martin - Initial contribution
 */
@NonNullByDefault
public class DiagralAnomalies {

    @SerializedName("created_at")
    public @Nullable String createdAt;

    @SerializedName("sensors")
    public @Nullable List<DiagralAnomalyDetail> sensors;

    @SerializedName("badges")
    public @Nullable List<DiagralAnomalyDetail> badges;

    @SerializedName("sirens")
    public @Nullable List<DiagralAnomalyDetail> sirens;

    @SerializedName("cameras")
    public @Nullable List<DiagralAnomalyDetail> cameras;

    @SerializedName("commands")
    public @Nullable List<DiagralAnomalyDetail> commands;

    @SerializedName("transceivers")
    public @Nullable List<DiagralAnomalyDetail> transceivers;

    @SerializedName("transmitters")
    public @Nullable List<DiagralAnomalyDetail> transmitters;

    @SerializedName("central")
    public @Nullable List<DiagralAnomalyDetail> central;

    /**
     * Computes the total number of device anomalies reported across all categories.
     *
     * @return the total anomaly count
     */
    public int getTotalCount() {
        return size(sensors) + size(badges) + size(sirens) + size(cameras) + size(commands) + size(transceivers)
                + size(transmitters) + size(central);
    }

    private static int size(@Nullable List<DiagralAnomalyDetail> list) {
        return list == null ? 0 : list.size();
    }
}
