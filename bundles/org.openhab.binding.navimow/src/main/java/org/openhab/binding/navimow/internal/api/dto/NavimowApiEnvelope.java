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
 * {@link NavimowApiEnvelope} is the common response wrapper used by every Navimow "smarthome" REST
 * endpoint observed so far ({@code authList}, {@code getVehicleStatus}, {@code sendCommands}):
 * {@code {"code":1,"desc":"...","data":{"payload": ...}}}. {@code code == 1} indicates success;
 * any other value indicates a business-level failure described by {@link #desc}. See
 * {@link NavimowApiResponseBase} for the {@code code}/{@code desc}/{@code isSuccess()} part shared
 * with {@link MqttUserInfoResponse}.
 *
 * @param <T> the endpoint-specific shape of {@code data.payload}
 * @author David Martin - Initial contribution
 */
public class NavimowApiEnvelope<T> extends NavimowApiResponseBase {

    public @Nullable NavimowApiData<T> data;
}
