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
 * The {@link DiagralApiKeyResponse} represents the response from a Diagral API key generation request.
 *
 * <p>
 * Returned by {@code POST /users/api_key} and parsed in {@code DiagralHttpClient.generateApiKey()},
 * which stores {@link #apiKey}/{@link #secretKey} in {@code DiagralAuthenticationManager} for use as the
 * {@code X-APIKEY} header and HMAC signing secret on all later requests.
 * </p>
 *
 * @author David Martin - Initial contribution
 */
@NonNullByDefault
public class DiagralApiKeyResponse {

    @SerializedName("api_key")
    public @Nullable String apiKey;

    @SerializedName("secret_key")
    public @Nullable String secretKey;
}
