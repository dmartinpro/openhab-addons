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
 * The {@link DiagralSystemStatus} represents the current status of the Diagral alarm system.
 *
 * @author David Martin - Initial contribution
 */
@NonNullByDefault
public class DiagralSystemStatus {

    @SerializedName("status")
    public @Nullable String status;

    @SerializedName("activated_groups")
    public @Nullable List<Integer> activatedGroups;
}
