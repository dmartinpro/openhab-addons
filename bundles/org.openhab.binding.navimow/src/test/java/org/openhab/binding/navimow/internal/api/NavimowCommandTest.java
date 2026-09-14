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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link NavimowCommand}'s Google Smart Home-style execution mapping.
 *
 * @author David Martin - Initial contribution
 */
class NavimowCommandTest {

    @Test
    void startMapsToStartStopOn() {
        assertThat(NavimowCommand.START.getExecutionCommand(), is("action.devices.commands.StartStop"));
        assertThat(NavimowCommand.START.getParams(), is(java.util.Map.of("on", true)));
    }

    @Test
    void pauseMapsToPauseUnpauseOff() {
        assertThat(NavimowCommand.PAUSE.getExecutionCommand(), is("action.devices.commands.PauseUnpause"));
        assertThat(NavimowCommand.PAUSE.getParams(), is(java.util.Map.of("on", false)));
    }

    @Test
    void resumeMapsToPauseUnpauseOn() {
        assertThat(NavimowCommand.RESUME.getExecutionCommand(), is("action.devices.commands.PauseUnpause"));
        assertThat(NavimowCommand.RESUME.getParams(), is(java.util.Map.of("on", true)));
    }

    @Test
    void dockMapsToDockWithNoParams() {
        assertThat(NavimowCommand.DOCK.getExecutionCommand(), is("action.devices.commands.Dock"));
        assertThat(NavimowCommand.DOCK.getParams(), nullValue());
    }
}
