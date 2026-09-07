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
import org.openhab.core.thing.Thing;
import org.openhab.core.types.UnDefType;

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
 * <li>{@code contact} - Contact state, always {@code UNDEF} - see
 * {@link #updateSensorSpecificChannels(DiagralDevice)}</li>
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
     * Sets the {@code contact} channel to {@code UNDEF}.
     *
     * <p>
     * The Diagral cloud API this binding uses exposes no real-time open/closed state - only device
     * inventory, inhibit status and anomalies. This channel therefore reports {@code UNDEF} ("unknown")
     * rather than a value.
     * </p>
     *
     * <p>
     * It previously published a hardcoded {@code CLOSED}, which was actively misleading in a security
     * binding: the channel asserted "this door is shut" forever, regardless of reality. {@code UNDEF} is
     * distinguishable from a genuine {@code CLOSED}, so a rule can detect that the value is unavailable
     * instead of silently trusting it. The channel is kept (rather than removed) so existing item links
     * don't break.
     * </p>
     *
     * @param device the device data from the API (unused - it carries no contact state)
     */
    @Override
    protected void updateSensorSpecificChannels(DiagralDevice device) {
        updateState(CHANNEL_CONTACT, UnDefType.UNDEF);
    }
}
