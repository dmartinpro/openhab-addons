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
package org.openhab.binding.navimow.internal.handler;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * The {@link NavimowBridgeConfiguration} class contains fields mapping the {@code account} bridge
 * Thing's configuration parameters.
 *
 * <p>
 * There is no username/password or client id/secret here: authentication is done through a
 * browser-based OAuth2 flow (see {@link NavimowAccountHandler}), not through configuration
 * parameters.
 *
 * @author David Martin - Initial contribution
 */
@NonNullByDefault
public class NavimowBridgeConfiguration {

    private @Nullable Integer pollingInterval;

    /**
     * @return the configured REST polling interval in seconds, or {@code null} if left at the
     *         thing-type default
     */
    public @Nullable Integer getPollingInterval() {
        return pollingInterval;
    }

    public void setPollingInterval(Integer pollingInterval) {
        this.pollingInterval = pollingInterval;
    }
}
