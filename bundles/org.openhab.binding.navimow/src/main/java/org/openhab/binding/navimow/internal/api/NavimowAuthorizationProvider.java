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
package org.openhab.binding.navimow.internal.api;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.navimow.internal.api.exceptions.NavimowAuthenticationException;
import org.openhab.binding.navimow.internal.api.exceptions.NavimowCommunicationException;

/**
 * {@link NavimowAuthorizationProvider} supplies a currently-valid bearer access token to
 * {@link NavimowApiClient} for each request, without the client needing to know anything about
 * OAuth2 token acquisition or refresh. Implemented by {@code NavimowAccountHandler}, backed by
 * openHAB core's {@code OAuthClientService}.
 *
 * @author David Martin - Initial contribution
 */
@NonNullByDefault
public interface NavimowAuthorizationProvider {

    /**
     * @return a currently-valid bearer access token
     * @throws NavimowAuthenticationException if no valid token is available and the user needs to
     *             (re)authorize
     * @throws NavimowCommunicationException if the token could not be obtained/refreshed due to a
     *             communication failure
     */
    String getAccessToken() throws NavimowAuthenticationException, NavimowCommunicationException;
}
