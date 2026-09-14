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
package org.openhab.binding.navimow.internal.api.dto;

/**
 * {@link DeviceRef} is the minimal {@code {"id": "..."}} device reference used to address a device
 * in both the {@code getVehicleStatus} request body and each {@code sendCommands} command entry.
 *
 * @author David Martin - Initial contribution
 */
public class DeviceRef {

    public String id;

    public DeviceRef(String id) {
        this.id = id;
    }
}
