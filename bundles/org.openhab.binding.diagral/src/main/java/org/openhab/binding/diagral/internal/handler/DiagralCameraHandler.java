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
 * The {@link DiagralCameraHandler} handles cameras.
 *
 * <p>
 * The Diagral cloud API used by this binding exposes no image/video access and no enable/disable action
 * for cameras - this thing is inventory/anomaly-visibility only, so its {@code enabled} channel is
 * read-only (see {@code thing-types.xml}) and {@link #getProductType()} returns {@code null}.
 * </p>
 *
 * @author David Martin - Initial contribution
 */
@NonNullByDefault
public class DiagralCameraHandler extends DiagralSensorHandler {

    /**
     * Constructs a new DiagralCameraHandler.
     *
     * @param thing the thing to handle
     */
    public DiagralCameraHandler(Thing thing) {
        super(thing);
    }

    /**
     * @return always {@code null} - the Diagral API has no enable/disable action for cameras
     */
    @Override
    protected @Nullable String getProductType() {
        return null;
    }

    /**
     * @param config the system configuration
     * @return the {@code cameras} list from the configuration
     */
    @Override
    protected @Nullable List<DiagralDevice> getDeviceList(DiagralSystemConfiguration config) {
        return config.cameras;
    }

    /**
     * No-op - cameras have no channels beyond the base {@code enabled}/{@code low-battery} ones.
     *
     * @param device the device data from the API (unused)
     */
    @Override
    protected void updateSensorSpecificChannels(DiagralDevice device) {
        // Cameras have no channels beyond the base enabled/low-battery ones
    }
}
