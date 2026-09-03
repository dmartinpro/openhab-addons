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
 * The {@link DiagralAnomalyName} represents a single named anomaly code reported for a device, as found
 * in a {@link DiagralAnomalyDetail}'s {@code anomaly_names} list.
 *
 * <p>
 * {@link #name} values are free-form strings defined by the Diagral API (e.g. {@code "inhibited"},
 * matched against {@code DiagralBindingConstants#DEVICE_ANOMALY_NAME_INHIBITED} by {@code
 * DiagralHttpClient.hasInhibitedAnomaly()}); {@link #id} is an opaque numeric identifier for the
 * specific anomaly occurrence and isn't otherwise used by this binding.
 * </p>
 *
 * @author David Martin - Initial contribution
 */
@NonNullByDefault
public class DiagralAnomalyName {

    @SerializedName("id")
    public int id;

    @SerializedName("name")
    public @Nullable String name;
}
