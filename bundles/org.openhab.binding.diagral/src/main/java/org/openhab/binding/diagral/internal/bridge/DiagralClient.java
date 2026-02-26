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
package org.openhab.binding.diagral.internal.bridge;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.diagral.internal.discovery.DiagralDiscoveryService;

/**
 * The {@link DiagralClient} defines methods for the {@link DiagralBridgeHandler}.
 *
 * @author David Martin - Initial contribution
 */
@NonNullByDefault
public interface DiagralClient {

    /**
     * Register {@link DiagralDiscoveryService} to bridge handler
     *
     * @param listener the discovery service
     * @return {@code true} if the new discovery service is accepted
     */
    boolean registerDiscoveryListener(DiagralDiscoveryService listener);

    /**
     * Unregister {@link DiagralDiscoveryService} from bridge handler
     *
     * @return {@code true} if the discovery service was removed
     */
    boolean unregisterDiscoveryListener();
}
