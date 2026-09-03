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
 * The {@link DiagralConfiguration} class contains fields mapping device configuration parameters.
 *
 * @author David Martin - Initial contribution
 */
@NonNullByDefault
public class DiagralConfiguration {

    /**
     * Unique device ID from Diagral system
     */
    public String deviceId = "";

    /**
     * Per-category numeric device index, used for enable/disable API calls
     */
    public int deviceIndex = -1;

    /**
     * Group ID for group things
     */
    public String groupId = "";

    /**
     * Validates the device configuration
     *
     * @return true if device configuration is valid, false otherwise
     */
    public boolean isValidDevice() {
        return !deviceId.isEmpty();
    }

    /**
     * Validates the group configuration
     *
     * @return true if group configuration is valid, false otherwise
     */
    public boolean isValidGroup() {
        return !groupId.isEmpty();
    }
}
