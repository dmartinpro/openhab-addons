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
 * {@link NavimowException} is a generic exception thrown in case of a communication or parsing
 * failure against the Navimow cloud API. Intended to be extended by more specific exceptions.
 *
 * @author David Martin - Initial contribution
 */
@NonNullByDefault
public class NavimowException extends Exception {

    private static final long serialVersionUID = 1L;

    public NavimowException(String message) {
        super(message);
    }

    public NavimowException(String message, Throwable cause) {
        super(message, cause);
    }

    public NavimowException(Throwable cause) {
        super(cause);
    }
}
