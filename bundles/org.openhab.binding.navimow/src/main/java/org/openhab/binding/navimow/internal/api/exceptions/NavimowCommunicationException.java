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
 * {@link NavimowCommunicationException} indicates a network-level or unexpected-response failure
 * talking to the Navimow cloud API (timeouts, non-2xx status codes other than authentication
 * failures, malformed JSON).
 *
 * @author David Martin - Initial contribution
 */
@NonNullByDefault
public class NavimowCommunicationException extends NavimowException {

    private static final long serialVersionUID = 1L;

    public NavimowCommunicationException(String message) {
        super(message);
    }

    public NavimowCommunicationException(String message, Throwable cause) {
        super(message, cause);
    }

    public NavimowCommunicationException(Throwable cause) {
        super(cause);
    }
}
