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

import com.google.gson.annotations.SerializedName;

/**
 * The {@link DiagralApiKeyRequest} represents an API key generation request to the Diagral API.
 *
 * <p>
 * Sent as the JSON body of the {@code POST /users/api_key} call in {@code
 * DiagralHttpClient.generateApiKey()}, the second step of the authentication flow (after login), to
 * obtain the {@code apiKey}/{@code secretKey} pair used to sign all subsequent requests.
 * </p>
 *
 * @author David Martin - Initial contribution
 */
@NonNullByDefault
public class DiagralApiKeyRequest {

    @SerializedName("serial_id")
    public String serialId;

    /**
     * Constructs a new API key request for the given Diagral box.
     *
     * @param serialId the serial ID of the Diagral box to generate an API key for
     */
    public DiagralApiKeyRequest(String serialId) {
        this.serialId = serialId;
    }
}
