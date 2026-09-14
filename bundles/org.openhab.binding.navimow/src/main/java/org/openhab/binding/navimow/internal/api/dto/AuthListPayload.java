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

import java.util.List;

import org.eclipse.jdt.annotation.Nullable;

/**
 * {@link AuthListPayload} is the {@code data.payload} shape of the {@code authList} endpoint
 * response: the list of devices linked to the authenticated account.
 *
 * @author David Martin - Initial contribution
 */
public class AuthListPayload {

    public @Nullable List<NavimowDevice> devices;
}
