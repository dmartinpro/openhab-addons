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
 * The {@link DiagralApiException} is thrown when a Diagral API call fails for a reason other than
 * authentication (see {@link DiagralAuthenticationException} for that case).
 *
 * <p>
 * Carries the HTTP status code returned by the API (or {@code 0} for failures that never got a real HTTP
 * response, e.g. an invalid request built locally before it was sent) so callers can branch on it - for
 * example {@code DiagralHttpClient.actionProduct()} checks for {@code HttpStatus.INTERNAL_SERVER_ERROR_500}
 * specifically to work around a known Diagral cloud quirk (see the README's "Known Limitations & Bugs").
 * </p>
 *
 * @author David Martin - Initial contribution
 */
@NonNullByDefault
public class DiagralApiException extends DiagralException {

    private static final long serialVersionUID = 1L;

    private final int statusCode;

    /**
     * Constructs a new exception with the given message and status code, and no cause.
     *
     * @param message a human-readable description of the API failure
     * @param statusCode the HTTP status code returned by the API, or {@code 0} if none is available
     */
    public DiagralApiException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    /**
     * Constructs a new exception with the given message and status code, wrapping an underlying cause.
     *
     * @param message a human-readable description of the API failure
     * @param statusCode the HTTP status code returned by the API, or {@code 0} if none is available
     * @param cause the underlying exception that triggered this failure, or null if there is none
     */
    public DiagralApiException(String message, int statusCode, @Nullable Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    /**
     * Gets the HTTP status code returned by the API for the failed request.
     *
     * @return the HTTP status code, or {@code 0} if the failure never produced a real HTTP response
     */
    public int getStatusCode() {
        return statusCode;
    }
}
