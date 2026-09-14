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
package org.openhab.binding.navimow.internal.api.dto;

import org.eclipse.jdt.annotation.Nullable;

/**
 * {@link SendCommandsResultEntry} is one entry of the {@code sendCommands} response's
 * {@code commands} array, reporting whether that particular command execution succeeded.
 *
 * <p>
 * A {@code status} of {@code "ERROR"} with {@code errorCode == "alreadyInState"} is treated as
 * success by this binding's caller (mirroring the official {@code navimow-sdk}): the device was
 * already in the requested state, which is an idempotent no-op, not a real failure.
 *
 * @author David Martin - Initial contribution
 */
public class SendCommandsResultEntry {

    public @Nullable String status;

    public @Nullable String errorCode;

    /**
     * @return whether this entry represents a business-level error other than the harmless
     *         "already in state" case
     */
    public boolean isRealError() {
        return "ERROR".equals(status) && !"alreadyInState".equals(errorCode);
    }
}
