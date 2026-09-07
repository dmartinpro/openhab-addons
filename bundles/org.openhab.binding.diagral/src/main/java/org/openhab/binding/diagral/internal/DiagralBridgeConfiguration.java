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
 * <p>
 * Populated by the openHAB framework from the {@code bridge} thing's configuration parameters (see
 * {@code thing-type:diagral:bridge} in {@code config.xml}) via {@code
 * DiagralBridgeHandler.initialize()}'s call to {@code getConfigAs(DiagralBridgeConfiguration.class)}.
 * Field names must match the config parameter names exactly for that binding to work.
 * </p>
 *
 * @author David Martin - Initial contribution
 */
@NonNullByDefault
public class DiagralBridgeConfiguration {

    /** Lowest accepted poll interval in seconds, matching the {@code min} in {@code config.xml}. */
    public static final int MIN_REFRESH_INTERVAL_SECONDS = 10;

    /** Highest accepted poll interval in seconds, matching the {@code max} in {@code config.xml}. */
    public static final int MAX_REFRESH_INTERVAL_SECONDS = 300;

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
     * Reports whether every required credential field is present.
     *
     * <p>
     * Deliberately separate from {@link #isRefreshIntervalValid()}: folding both into one check made an
     * out-of-range interval report itself as "check username, password, serialId, and pinCode", which
     * sends the user looking in the wrong place. {@code config.xml} constrains the interval in the UI,
     * but a textual {@code .things} file can still set anything.
     * </p>
     *
     * @return true if all four credential fields are non-empty
     */
    public boolean hasCredentials() {
        return !username.isEmpty() && !password.isEmpty() && !serialId.isEmpty() && !pinCode.isEmpty();
    }

    /**
     * Reports whether the poll interval is within the range {@code config.xml} declares.
     *
     * @return true if {@link #refreshInterval} is between {@value #MIN_REFRESH_INTERVAL_SECONDS} and
     *         {@value #MAX_REFRESH_INTERVAL_SECONDS} seconds inclusive
     */
    public boolean isRefreshIntervalValid() {
        return refreshInterval >= MIN_REFRESH_INTERVAL_SECONDS && refreshInterval <= MAX_REFRESH_INTERVAL_SECONDS;
    }
}
