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
 * The {@link DiagralLoginResponse} represents the response from a Diagral API login request.
 *
 * <p>
 * Returned by {@code POST /users/authenticate/login} and parsed in {@code DiagralHttpClient.login()},
 * which uses {@link #accessToken} as the {@code Authorization: Bearer} credential for the subsequent
 * API-key-generation call - it is not used for any other request.
 * </p>
 *
 * @author David Martin - Initial contribution
 */
@NonNullByDefault
public class DiagralLoginResponse {

    @SerializedName("access_token")
    public @Nullable String accessToken;
}
