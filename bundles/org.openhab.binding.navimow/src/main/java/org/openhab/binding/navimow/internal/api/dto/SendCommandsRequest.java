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

import java.util.List;

/**
 * {@link SendCommandsRequest} is the top-level request body for {@code POST .../sendCommands}:
 * {@code {"commands": [...]}}.
 *
 * @author David Martin - Initial contribution
 */
public class SendCommandsRequest {

    public List<SendCommandsRequestCommand> commands;

    public SendCommandsRequest(List<SendCommandsRequestCommand> commands) {
        this.commands = commands;
    }
}
