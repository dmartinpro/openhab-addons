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
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.thing.Thing;

/**
 * The {@link DiagralMotionSensorHandler} handles motion detection sensors.
 *
 * <p>
 * This handler extends {@link DiagralSensorHandler} and adds specific support for
 * motion detection channels.
 * </p>
 *
 * <p>
 * Additional channels beyond base sensor:
 * <ul>
 * <li>{@code motion} - Motion detection state (ON when motion detected)</li>
 * </ul>
 * </p>
 *
 * @author David Martin - Initial contribution
 */
@NonNullByDefault
public class DiagralMotionSensorHandler extends DiagralSensorHandler {

    /**
     * Constructs a new DiagralMotionSensorHandler.
     *
     * @param thing the thing to handle
     */
    public DiagralMotionSensorHandler(Thing thing) {
        super(thing);
    }

    @Override
    protected void updateSensorSpecificChannels(DiagralDevice device) {
        // Motion sensors don't provide real-time motion status from the API
        // The motion state would need to be inferred from system events or a different API
        // For now, we default to OFF (no motion detected)
        updateState(CHANNEL_MOTION, OnOffType.OFF);
    }
}
