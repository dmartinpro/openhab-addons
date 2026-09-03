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

import static org.openhab.binding.diagral.internal.DiagralBindingConstants.PRODUCT_TYPE_PLUG;

import java.util.List;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.diagral.internal.dto.DiagralDevice;
import org.openhab.binding.diagral.internal.dto.DiagralSystemConfiguration;
import org.openhab.core.thing.Thing;

/**
 * The {@link DiagralPlugHandler} handles smart plugs (transmitters with {@code isPlug} set).
 *
 * <p>
 * Plugs only expose the base {@code enabled}/{@code low-battery} channels from
 * {@link DiagralSensorHandler} - there's no additional plug-specific state available from the API.
 * </p>
 *
 * @author David Martin - Initial contribution
 */
@NonNullByDefault
public class DiagralPlugHandler extends DiagralSensorHandler {

    /**
     * Constructs a new DiagralPlugHandler.
     *
     * @param thing the thing to handle
     */
    public DiagralPlugHandler(Thing thing) {
        super(thing);
    }

    /**
     * @return {@link org.openhab.binding.diagral.internal.DiagralBindingConstants#PRODUCT_TYPE_PLUG}
     */
    @Override
    protected @Nullable String getProductType() {
        return PRODUCT_TYPE_PLUG;
    }

    /**
     * @param config the system configuration
     * @return the {@code transmitters} list from the configuration - plugs are discovered from the same
     *         API category as generic transmitters, distinguished only by the {@code isPlug} flag on each
     *         device (see {@link org.openhab.binding.diagral.internal.discovery.DiagralDiscoveryService})
     */
    @Override
    protected @Nullable List<DiagralDevice> getDeviceList(DiagralSystemConfiguration config) {
        return config.transmitters;
    }

    /**
     * No-op - plugs have no channels beyond the base {@code enabled}/{@code low-battery} ones.
     *
     * @param device the device data from the API (unused)
     */
    @Override
    protected void updateSensorSpecificChannels(DiagralDevice device) {
        // Plugs have no channels beyond the base enabled/low-battery ones
    }
}
