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
package org.openhab.binding.navimow.internal.api.exceptions;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * {@link NavimowAuthenticationException} indicates that a REST request was rejected as unauthorized
 * (expired or invalid access token), meaning the bridge should attempt to refresh or re-obtain a
 * token rather than treat this as a plain communication failure.
 *
 * @author David Martin - Initial contribution
 */
@NonNullByDefault
public class NavimowAuthenticationException extends NavimowException {

    private static final long serialVersionUID = 1L;

    public NavimowAuthenticationException(String message) {
        super(message);
    }

    public NavimowAuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }
}
