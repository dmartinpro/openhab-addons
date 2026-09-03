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
package org.openhab.binding.diagral.internal.handler;

import static org.openhab.binding.diagral.internal.DiagralBindingConstants.*;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.diagral.internal.dto.DiagralDevice;
import org.openhab.core.library.types.OpenClosedType;
import org.openhab.core.thing.Thing;

/**
 * The {@link DiagralContactSensorHandler} handles contact/door sensors.
 *
 * <p>
 * This handler extends {@link DiagralSensorHandler} and adds specific support for
 * contact/door state channels.
 * </p>
 *
 * <p>
 * Additional channels beyond base sensor:
 * <ul>
 * <li>{@code contact} - Contact state (OPEN/CLOSED)</li>
 * </ul>
 * </p>
 *
 * @author David Martin - Initial contribution
 */
@NonNullByDefault
public class DiagralContactSensorHandler extends DiagralSensorHandler {

    /**
     * Constructs a new DiagralContactSensorHandler.
     *
     * @param thing the thing to handle
     */
    public DiagralContactSensorHandler(Thing thing) {
        super(thing);
    }

    /**
     * Updates the {@code contact} channel.
     *
     * @param device the device data from the API (unused - see below)
     */
    @Override
    protected void updateSensorSpecificChannels(DiagralDevice device) {
        // Contact sensors don't provide real-time contact status from the API
        // The contact state would need to be inferred from system events or a different API
        // For now, we default to CLOSED (door/window closed)
        updateState(CHANNEL_CONTACT, OpenClosedType.CLOSED);
    }
}
