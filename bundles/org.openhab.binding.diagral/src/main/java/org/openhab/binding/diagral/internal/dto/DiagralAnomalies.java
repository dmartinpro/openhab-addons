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
 * categories of a Diagral system, as returned by the {@code GET /systems/{serialId}/anomalies} endpoint
 * (see {@code DiagralHttpClient.getAnomalies()}).
 *
 * <p>
 * A {@code null} category list (e.g. {@link #sensors}) means nothing in that category currently has an
 * anomaly - it is not the same as "unknown"; see {@code DiagralHttpClient.isDeviceInhibited()}, which
 * relies on this distinction. The endpoint itself behaves as a periodically-refreshed snapshot rather
 * than a fully live query, and returns HTTP 404 when there are no anomalies at all - {@code
 * DiagralHttpClient.getAnomalies()} translates that specific 404 into an empty {@link DiagralAnomalies}
 * rather than propagating it as an error.
 * </p>
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
     * <p>
     * Used by {@code DiagralSystemHandler.updateChannels()} to drive the alarm system's {@code
     * anomaly-count} and {@code anomalies-present} channels.
     * </p>
     *
     * @return the total anomaly count
     */
    public int getTotalCount() {
        return size(sensors) + size(badges) + size(sirens) + size(cameras) + size(commands) + size(transceivers)
                + size(transmitters) + size(central);
    }

    /**
     * Null-safely gets the size of a category's anomaly list.
     *
     * @param list the category's anomaly list, possibly null
     * @return the list's size, or {@code 0} if the list is null (i.e. nothing in that category has an
     *         anomaly)
     */
    private static int size(@Nullable List<DiagralAnomalyDetail> list) {
        return list == null ? 0 : list.size();
    }
}
