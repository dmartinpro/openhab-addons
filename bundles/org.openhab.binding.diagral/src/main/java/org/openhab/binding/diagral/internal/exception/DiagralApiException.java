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
 * The {@link DiagralApiException} is thrown when a Diagral API call fails.
 *
 * @author David Martin - Initial contribution
 */
@NonNullByDefault
public class DiagralApiException extends DiagralException {

    private static final long serialVersionUID = 1L;

    private final int statusCode;

    public DiagralApiException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public DiagralApiException(String message, int statusCode, @Nullable Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
