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
 * The {@link DiagralException} is the base exception class for all Diagral binding exceptions.
 *
 * <p>
 * All failures raised by the {@code internal.bridge} layer (HTTP transport, authentication, API errors)
 * extend this type, so calling code can catch a single checked exception type when talking to the
 * Diagral cloud rather than every concrete failure mode individually. See
 * {@link DiagralAuthenticationException} and {@link DiagralApiException} for the more specific subtypes
 * actually thrown in practice.
 * </p>
 *
 * @author David Martin - Initial contribution
 */
@NonNullByDefault
public class DiagralException extends Exception {

    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new exception with the given message and no cause.
     *
     * @param message a human-readable description of the failure
     */
    public DiagralException(String message) {
        super(message);
    }

    /**
     * Constructs a new exception with the given message, wrapping an underlying cause.
     *
     * @param message a human-readable description of the failure
     * @param cause the underlying exception that triggered this failure, or null if there is none
     */
    public DiagralException(String message, @Nullable Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructs a new exception wrapping an underlying cause, with no separate message of its own.
     *
     * @param cause the underlying exception that triggered this failure
     */
    public DiagralException(@Nullable Throwable cause) {
        super(cause);
    }
}
