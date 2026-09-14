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
 * {@code id}, {@code name} and {@code online} are confirmed against a real payload (both
 * independent Home Assistant integrations read these directly, without defensive fallback, from
 * the same endpoint). Other fields commonly seen in reverse-engineered SDKs (model, firmware
 * version, serial number, MAC address) are deliberately not modelled here yet - neither community
 * source accesses them without a defensive multi-key fallback, meaning their real field names
 * are not actually confirmed. Add them once live testing against a real account confirms the
 * exact shape.
 *
 * @author David Martin - Initial contribution
 */
public class NavimowDevice {

    public @Nullable String id;

    public @Nullable String name;

    public boolean online;
}
