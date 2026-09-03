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
 * The {@link DiagralCentral} represents the alarm central unit's own configuration and status, as
 * returned in the {@code centralInformation} field of the {@code /systems/{serialId}/configurations}
 * response.
 *
 * <p>
 * {@link #anomalies} is a map of central-unit-level anomaly flags, keyed by the same alert names as
 * {@code DiagralDevice#anomalies} (e.g. {@code mainPowerSupplyAlert}); {@code
 * DiagralSystemHandler.updateChannels()} checks {@code mainPowerSupplyAlert}/{@code
 * secondaryPowerSupplyAlert} here to drive the alarm system's {@code central-low-battery} channel.
 * </p>
 *
 * @author David Martin - Initial contribution
 */
@NonNullByDefault
public class DiagralCentral {

    @SerializedName("hasPlug")
    public @Nullable Boolean hasPlug;

    @SerializedName("plugGSM")
    public @Nullable Boolean plugGSM;

    @SerializedName("plugRTC")
    public @Nullable Boolean plugRTC;

    @SerializedName("plugADSL")
    public @Nullable Boolean plugADSL;

    @SerializedName("relayCard")
    public @Nullable Boolean relayCard;

    @SerializedName("canInhibit")
    public @Nullable Boolean canInhibit;

    @SerializedName("parameterGsmSaved")
    public @Nullable Boolean parameterGsmSaved;

    @SerializedName("anomalies")
    public @Nullable Map<String, Boolean> anomalies;
}
