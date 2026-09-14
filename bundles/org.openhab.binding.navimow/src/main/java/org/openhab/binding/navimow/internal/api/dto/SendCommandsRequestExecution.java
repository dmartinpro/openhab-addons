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

import java.util.Map;

import org.eclipse.jdt.annotation.Nullable;

/**
 * {@link SendCommandsRequestExecution} carries a Google Smart Home-style command execution, e.g.
 * {@code {"command": "action.devices.commands.StartStop", "params": {"on": true}}}. This
 * command/params shape (rather than a Navimow-specific verb) is confirmed by the official
 * {@code navimow-sdk} Python package, which is what this binding's REST command path is modelled
 * on.
 *
 * @author David Martin - Initial contribution
 */
public class SendCommandsRequestExecution {

    public String command;

    public @Nullable Map<String, Object> params;

    public SendCommandsRequestExecution(String command, @Nullable Map<String, Object> params) {
        this.command = command;
        this.params = params;
    }
}
