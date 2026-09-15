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
package org.openhab.binding.navimow.internal.api;

import java.util.Map;

import org.eclipse.jdt.annotation.Nullable;

/**
 * {@link NavimowCommand} enumerates the mower commands this binding can send, together with the
 * Google Smart Home-style execution name and parameters {@code sendCommands} expects for each -
 * confirmed against the official {@code navimow-sdk} Python package, which is the only source that
 * sends commands over REST (both Home Assistant integrations only ever expose start/pause/resume/
 * dock; there is no confirmed REST equivalent for anything else, e.g. blade height).
 *
 * <p>
 * {@code START}, {@code PAUSE}, {@code RESUME} and {@code DOCK} are live-confirmed end-to-end
 * (2026-09-15, real Navimow X430): sent in sequence, each returned a real non-null {@code cmdNum}
 * and the mower's reported {@code vehicleState} transitioned correctly (isRunning &rarr; isPaused
 * &rarr; isRunning &rarr; isDocking &rarr; isDocked). {@code STOP} mirrors the official SDK's
 * mapping - the same {@code StartStop} execution as {@code START} with the params inverted - but
 * could not itself be live-confirmed: it was only reachable via a standalone call (since it wasn't
 * part of this enum yet at the time), made from the developer's own machine rather than from inside
 * the account bridge's Docker container. Every such standalone call - to this and to other endpoints -
 * was consistently rejected regardless of token freshness or client library, while the real binding's
 * calls (running inside the container) succeeded throughout the same window; the best-supported
 * explanation is a network-origin-bound access token, not anything about {@code STOP} itself - see
 * {@code NavimowBindingConstants.BUSINESS_CODE_OAUTH_INFO_ILLEGAL} for the full investigation. Either
 * way, this mapping is inferred-correct, not yet independently proven the way the other four are.
 *
 * @author David Martin - Initial contribution
 */
public enum NavimowCommand {
    START("action.devices.commands.StartStop", Map.of("on", true)),
    STOP("action.devices.commands.StartStop", Map.of("on", false)),
    PAUSE("action.devices.commands.PauseUnpause", Map.of("on", false)),
    RESUME("action.devices.commands.PauseUnpause", Map.of("on", true)),
    DOCK("action.devices.commands.Dock", null);

    private final String executionCommand;
    private final @Nullable Map<String, Object> params;

    NavimowCommand(String executionCommand, @Nullable Map<String, Object> params) {
        this.executionCommand = executionCommand;
        this.params = params;
    }

    public String getExecutionCommand() {
        return executionCommand;
    }

    public @Nullable Map<String, Object> getParams() {
        return params;
    }
}
