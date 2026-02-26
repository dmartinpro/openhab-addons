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
 * The {@link DiagralAnomaly} represents an anomaly or alert in the Diagral system.
 *
 * @author David Martin - Initial contribution
 */
@NonNullByDefault
public class DiagralAnomaly {

    @SerializedName("type")
    public @Nullable String type;

    @SerializedName("index")
    public @Nullable Integer deviceIndex;

    @SerializedName("label")
    public @Nullable String deviceLabel;

    @SerializedName("group")
    public @Nullable Integer groupIndex;

    @SerializedName("serial")
    public @Nullable String serial;
}
