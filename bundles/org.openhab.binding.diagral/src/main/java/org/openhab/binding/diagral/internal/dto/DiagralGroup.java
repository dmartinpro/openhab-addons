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
 * The {@link DiagralGroup} represents a device group in the Diagral system.
 *
 * @author David Martin - Initial contribution
 */
@NonNullByDefault
public class DiagralGroup {

    @SerializedName("name")
    public @Nullable String name;

    @SerializedName("index")
    public int index;

    @SerializedName("inputDelay")
    public int inputDelay;

    @SerializedName("outputDelay")
    public int outputDelay;
}
