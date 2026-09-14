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
package org.openhab.binding.navimow.internal.api.dto;

import org.eclipse.jdt.annotation.Nullable;

/**
 * {@link CapacityRemainingItem} is one entry of the {@code capacityRemaining} array on a
 * {@code getVehicleStatus} device entry - a Google Smart Home-style "capacity remaining" trait.
 * Battery percentage is the entry whose {@link #unit} is {@code PERCENTAGE}; other units are
 * possible per the underlying trait but not otherwise used by this binding.
 *
 * @author David Martin - Initial contribution
 */
public class CapacityRemainingItem {

    public @Nullable String unit;

    public int rawValue;
}
