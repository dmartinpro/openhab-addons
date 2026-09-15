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
 * All five commands are now live-confirmed end-to-end through this binding's own client (real
 * Navimow X430): first {@code START}/{@code PAUSE}/{@code RESUME}/{@code DOCK} on 2026-09-15, then
 * {@code STOP} the same day in a second run (sequence: START &rarr; PAUSE &rarr; RESUME &rarr;
 * STOP &rarr; DOCK). Every one returned a real non-null {@code cmdNum} and produced a real
 * {@code vehicleState} transition: isRunning &rarr; isPaused &rarr; isRunning &rarr; <b>isPaused
 * &rarr;</b> isDocking &rarr; isDocked.
 *
 * <p>
 * <b>{@code STOP} and {@code PAUSE} are indistinguishable from the reported state alone</b> - both
 * settle to the exact same {@code vehicleState}, {@code isPaused}, despite using different Google
 * Smart Home execution verbs ({@code StartStop}/{@code on:false} vs. {@code PauseUnpause}/
 * {@code on:false}). Whatever distinction exists between them on the real hardware (if any - e.g. a
 * different re-arm/timeout behavior) is not visible through this API's status endpoint.
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
