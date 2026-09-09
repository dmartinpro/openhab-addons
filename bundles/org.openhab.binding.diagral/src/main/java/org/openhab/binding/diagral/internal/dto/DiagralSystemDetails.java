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

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

/**
 * The {@link DiagralSystemDetails} represents the details of a Diagral alarm system, as returned by the
 * {@code GET /systems/{serialId}} endpoint (see {@code DiagralHttpClient.getSystemDetails()}).
 *
 * <p>
 * Note the PascalCase {@code @SerializedName} values - unlike most other Diagral API responses used by
 * this bundle, this endpoint's JSON keys are capitalized. Fetched once and cached by {@code
 * DiagralBridgeHandler.getSystemDetails()}, and used only to populate discovery-time properties on the
 * {@code alarm-system} thing in {@code DiagralDiscoveryService.discoverAlarmSystem()} - none of these
 * fields back a live channel.
 * </p>
 *
 * @author David Martin - Initial contribution
 */
@NonNullByDefault
public class DiagralSystemDetails {

    /** The central unit's device type. */
    @SerializedName("DeviceType")
    public @Nullable String deviceType;

    /** The central unit's installed firmware version. */
    @SerializedName("FirmwareVersion")
    public @Nullable String firmwareVersion;

    /** The central unit's IP address. */
    @SerializedName("IpAddress")
    public @Nullable String ipAddress;

    /** The installed iPoda protocol version. */
    @SerializedName("IpodaVersion")
    public @Nullable String ipodaVersion;

    /** The central unit's operating mode string. */
    @SerializedName("Mode")
    public @Nullable String mode;

    /** The first vocal contact configured for the system. */
    @SerializedName("FirstVocalContact")
    public @Nullable String firstVocalContact;

    /** Whether an alarm recording file is present, as a string flag. */
    @SerializedName("IsAlarmFilePresent")
    public @Nullable String isAlarmFilePresent;

    /** Whether MJPEG archive video playback is supported, as a string flag. */
    @SerializedName("IsMJPEGArchiveVideoSupported")
    public @Nullable String isMJPEGArchiveVideoSupported;

    /** Whether the central unit has mass storage present, as a string flag. */
    @SerializedName("IsMassStoragePresent")
    public @Nullable String isMassStoragePresent;

    /** Whether remote startup/shutdown is allowed, as a string flag. */
    @SerializedName("IsRemoteStartupShutdownAllowed")
    public @Nullable String isRemoteStartupShutdownAllowed;

    /** Whether video access is password-protected, as a string flag. */
    @SerializedName("IsVideoPasswordProtected")
    public @Nullable String isVideoPasswordProtected;
}
