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
package org.openhab.binding.diagral.internal;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * The {@link DiagralBridgeConfiguration} class contains configuration fields for the Diagral bridge.
 *
 * @author David Martin - Initial contribution
 */
@NonNullByDefault
public class DiagralBridgeConfiguration {

    /**
     * Username (email) for Diagral account
     */
    public String username = "";

    /**
     * Password for Diagral account
     */
    public String password = "";

    /**
     * Serial ID of the Diagral box (DIAG56AAX identifier)
     */
    public String serialId = "";

    /**
     * PIN code for system control
     */
    public String pinCode = "";

    /**
     * Polling interval in seconds for status updates
     */
    public int refreshInterval = 60;

    /**
     * Validates the configuration
     *
     * @return true if configuration is valid, false otherwise
     */
    public boolean isValid() {
        return !username.isEmpty() && !password.isEmpty() && !serialId.isEmpty() && !pinCode.isEmpty()
                && refreshInterval >= 10 && refreshInterval <= 300;
    }
}
