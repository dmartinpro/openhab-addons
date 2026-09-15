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
 * {@link NavimowDevice} represents one device entry returned by the {@code authList} endpoint.
 *
 * <p>
 * <b>Live-confirmed 2026-09-15</b> against a real account/device (Navimow X430) - every field here
 * reflects the exact, complete real response, not an inference:
 *
 * <pre>{@code {"id":"22AAD2602Y0911","name":"Navimow X430","model":"X430","firmware":"005D"}}</pre>
 *
 * <p>
 * <b>There is no {@code online} field.</b> An earlier version of this class had one (defaulting to
 * {@code false} and never actually read by any handler), based on the two community Home Assistant
 * integrations defensively reading {@code .get("online", ...)}. The real response above proves that
 * field does not exist on this endpoint for this account - removed rather than kept as a
 * silently-wrong default.
 *
 * @author David Martin - Initial contribution
 */
public class NavimowDevice {

    public @Nullable String id;

    public @Nullable String name;

    /** Surfaced on the mower Thing as {@code Thing.PROPERTY_MODEL_ID}. */
    public @Nullable String model;

    /**
     * Firmware version string, e.g. {@code "005D"} - not confirmed to be human-readable/semver.
     * Surfaced on the mower Thing as {@code Thing.PROPERTY_FIRMWARE_VERSION}.
     */
    public @Nullable String firmware;
}
