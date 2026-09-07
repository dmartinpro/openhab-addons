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

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.diagral.internal.bridge.DiagralPollSnapshot;

/**
 * The {@link DiagralRefreshableHandler} is implemented by thing handlers that need to refresh
 * their channels on the bridge's polling interval, rather than only on a manual refresh command.
 *
 * @author David Martin - Initial contribution
 */
@NonNullByDefault
public interface DiagralRefreshableHandler {

    /**
     * Refreshes this handler's channels from one shared view of the system.
     *
     * <p>
     * The snapshot is supplied by the caller rather than each handler fetching its own data, so every
     * handler refreshed in the same cycle reflects the same moment - see {@link DiagralPollSnapshot}.
     * </p>
     *
     * @param snapshot the system state to reflect
     */
    void refreshStatus(DiagralPollSnapshot snapshot);
}
