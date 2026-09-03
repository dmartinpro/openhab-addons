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

import java.util.List;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.diagral.internal.dto.DiagralDevice;
import org.openhab.binding.diagral.internal.dto.DiagralSystemConfiguration;
import org.openhab.core.thing.Thing;

/**
 * The {@link DiagralTransmitterHandler} handles generic (non-plug) transmitters.
 *
 * <p>
 * Unlike sensors, sirens, keypads, and plugs, the Diagral API has no enable/disable action for the
 * generic transmitter category - so this thing's {@code enabled} channel is read-only (see
 * {@code thing-types.xml}) and {@link #getProductType()} returns {@code null}.
 * </p>
 *
 * @author David Martin - Initial contribution
 */
@NonNullByDefault
public class DiagralTransmitterHandler extends DiagralSensorHandler {

    /**
     * Constructs a new DiagralTransmitterHandler.
     *
     * @param thing the thing to handle
     */
    public DiagralTransmitterHandler(Thing thing) {
        super(thing);
    }

    @Override
    protected @Nullable String getProductType() {
        return null;
    }

    @Override
    protected @Nullable List<DiagralDevice> getDeviceList(DiagralSystemConfiguration config) {
        return config.transmitters;
    }

    @Override
    protected void updateSensorSpecificChannels(DiagralDevice device) {
        // Transmitters have no channels beyond the base enabled/low-battery ones
    }
}
