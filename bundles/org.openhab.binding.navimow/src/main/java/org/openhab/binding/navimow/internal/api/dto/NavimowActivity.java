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

import java.util.Map;

import org.eclipse.jdt.annotation.Nullable;

/**
 * {@link NavimowActivity} is the canonical, binding-facing mower activity, normalized from the raw
 * {@code vehicleState} strings the cloud API reports (e.g. {@code isDocked}, {@code isMapping},
 * {@code Error}). The raw-to-canonical mapping mirrors the table reverse-engineered from the
 * official {@code navimow-sdk} Python package's {@code _RAW_STATE_TO_CANONICAL}, which is the only
 * place all the raw spellings are enumerated together; individual raw values are not otherwise
 * independently confirmed against a live account.
 *
 * @author David Martin - Initial contribution
 */
public enum NavimowActivity {
    IDLE,
    MOWING,
    PAUSED,
    DOCKED,
    CHARGING,
    RETURNING,
    ERROR,
    UNKNOWN;

    private static final Map<String, NavimowActivity> RAW_STATE_TO_ACTIVITY = Map.ofEntries(
            Map.entry("isDocked", DOCKED), Map.entry("isIdel", IDLE), Map.entry("isIdle", IDLE),
            Map.entry("isMapping", MOWING), Map.entry("isRunning", MOWING), Map.entry("isPaused", PAUSED),
            Map.entry("isDocking", RETURNING), Map.entry("Error", ERROR), Map.entry("error", ERROR),
            Map.entry("isLifted", ERROR), Map.entry("inSoftwareUpdate", PAUSED), Map.entry("Self-Checking", IDLE),
            Map.entry("Self-checking", IDLE), Map.entry("Offline", UNKNOWN), Map.entry("offline", UNKNOWN));

    /**
     * Normalizes a raw {@code vehicleState} value from the cloud API into a canonical activity.
     *
     * @param rawState the raw state string as reported by the API, or {@code null}
     * @return the canonical activity, or {@link #UNKNOWN} if the raw value is null or not recognized
     */
    public static NavimowActivity fromRawState(@Nullable String rawState) {
        if (rawState == null) {
            return UNKNOWN;
        }
        return RAW_STATE_TO_ACTIVITY.getOrDefault(rawState, UNKNOWN);
    }
}
