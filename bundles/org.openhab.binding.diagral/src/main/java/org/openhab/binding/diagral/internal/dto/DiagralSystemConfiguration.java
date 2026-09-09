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

    /** The installation's overall setup state. */
    @SerializedName("installationState")
    public int installationState = 0;

    /** Group indices armed by {@code MODE_PRESENCE}. */
    @SerializedName("presenceGroup")
    public @Nullable List<Integer> presenceGroup;

    /** Group indices armed by {@code MODE_PARTIAL1}. */
    @SerializedName("partialGroup1")
    public @Nullable List<Integer> partialGroup1;

    /** Group indices armed by {@code MODE_PARTIAL2}. */
    @SerializedName("partialGroup2")
    public @Nullable List<Integer> partialGroup2;

    /** The alarm system's own identity (box/central serials and name). */
    @SerializedName("alarm")
    public @Nullable DiagralAlarm alarm;

    /** Every motion/contact sensor in the installation. */
    @SerializedName("sensors")
    public @Nullable List<DiagralDevice> sensors;

    /** Every siren in the installation. */
    @SerializedName("sirens")
    public @Nullable List<DiagralDevice> sirens;

    /** Every camera in the installation. */
    @SerializedName("cameras")
    public @Nullable List<DiagralDevice> cameras;

    /** Every transmitter (including plugs) in the installation. */
    @SerializedName("transmitters")
    public @Nullable List<DiagralDevice> transmitters;

    /** Every keypad in the installation (the API's "commands" category). */
    @SerializedName("commands")
    public @Nullable List<DiagralDevice> commands;

    /** Every device group defined in the installation. */
    @SerializedName("groups")
    public @Nullable List<DiagralGroup> groups;

    /** The central alarm unit's own configuration and status. */
    @SerializedName("centralInformation")
    public @Nullable DiagralCentral centralInformation;
}
