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

/**
 * The {@link DiagralRefreshableHandler} is implemented by thing handlers that need to refresh
 * their channels on the bridge's polling interval, rather than only on a manual refresh command.
 *
 * @author David Martin - Initial contribution
 */
@NonNullByDefault
public interface DiagralRefreshableHandler {

    /**
     * Refreshes this handler's channels from the latest data available on the bridge.
     */
    void refreshStatus();
}
