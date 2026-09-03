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
 * The {@link DiagralLoginRequest} represents a login request to the Diagral API.
 *
 * <p>
 * Sent as the JSON body of the {@code POST /users/authenticate/login} call in {@code
 * DiagralHttpClient.login()}, the first step of the authentication flow, to obtain a bearer access
 * token.
 * </p>
 *
 * @author David Martin - Initial contribution
 */
@NonNullByDefault
public class DiagralLoginRequest {

    @SerializedName("username")
    public String username;

    @SerializedName("password")
    public String password;

    /**
     * Constructs a new login request for the given Diagral account credentials.
     *
     * @param username the Diagral account's email address
     * @param password the Diagral account's password
     */
    public DiagralLoginRequest(String username, String password) {
        this.username = username;
        this.password = password;
    }
}
