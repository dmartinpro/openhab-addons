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
package org.openhab.binding.diagral.internal.exception;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * The {@link DiagralAuthenticationException} is thrown when authentication with the Diagral API fails.
 *
 * <p>
 * Covers both the initial login/API-key-generation flow ({@code DiagralHttpClient.authenticate()}) and a
 * mid-session credential failure detected during a normal API call (an HTTP 400/401/403 response, which
 * {@code DiagralHttpClient} maps to this exception after clearing the stored API keys). Catching this
 * specifically (rather than the base {@link DiagralException}) is how {@code DiagralBridgeHandler}
 * distinguishes "credentials are bad, re-authenticate" from a transient network/server problem.
 * </p>
 *
 * @author David Martin - Initial contribution
 */
@NonNullByDefault
public class DiagralAuthenticationException extends DiagralException {

    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new exception with the given message and no cause.
     *
     * @param message a human-readable description of the authentication failure
     */
    public DiagralAuthenticationException(String message) {
        super(message);
    }

    /**
     * Constructs a new exception with the given message, wrapping an underlying cause.
     *
     * @param message a human-readable description of the authentication failure
     * @param cause the underlying exception that triggered this failure, or null if there is none
     */
    public DiagralAuthenticationException(String message, @Nullable Throwable cause) {
        super(message, cause);
    }
}
