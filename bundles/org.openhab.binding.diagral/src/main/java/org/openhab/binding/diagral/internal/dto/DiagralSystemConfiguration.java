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
 * The {@link DiagralSystemConfiguration} represents the complete configuration of a Diagral alarm
 * system, as returned by the {@code GET /systems/{serialId}/configurations} endpoint (see {@code
 * DiagralHttpClient.getSystemConfiguration()}).
 *
 * <p>
 * This is the single largest and most-used response in the binding: it lists every device category
 * ({@link #sensors}, {@link #sirens}, {@link #commands} (keypads), {@link #transmitters}, {@link
 * #cameras}) that {@code DiagralDiscoveryService} discovers things from, {@link #groups} used for group
 * discovery, and {@link #presenceGroup}/{@link #partialGroup1}/{@link #partialGroup2} - the group-index
 * membership lists that tell {@code DiagralDiscoveryService.getArmModesForGroup()} which arm modes each
 * group belongs to. Fetched once and cached by {@code DiagralBridgeHandler.getSystemConfiguration()};
 * that cache is explicitly invalidated after a successful enable/disable action so device state stays
 * fresh (see {@code DiagralBridgeHandler.enableDevice()}/{@code disableDevice()}).
 * </p>
 *
 * @author David Martin - Initial contribution
 */
@NonNullByDefault
public class DiagralSystemConfiguration {

    @SerializedName("installationState")
    public int installationState = 0;

    @SerializedName("presenceGroup")
    public @Nullable List<Integer> presenceGroup;

    @SerializedName("partialGroup1")
    public @Nullable List<Integer> partialGroup1;

    @SerializedName("partialGroup2")
    public @Nullable List<Integer> partialGroup2;

    @SerializedName("alarm")
    public @Nullable DiagralAlarm alarm;

    @SerializedName("sensors")
    public @Nullable List<DiagralDevice> sensors;

    @SerializedName("sirens")
    public @Nullable List<DiagralDevice> sirens;

    @SerializedName("cameras")
    public @Nullable List<DiagralDevice> cameras;

    @SerializedName("transmitters")
    public @Nullable List<DiagralDevice> transmitters;

    @SerializedName("commands")
    public @Nullable List<DiagralDevice> commands;

    @SerializedName("groups")
    public @Nullable List<DiagralGroup> groups;

    @SerializedName("centralInformation")
    public @Nullable DiagralCentral centralInformation;
}
