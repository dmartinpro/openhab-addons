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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link SendCommandsResultEntry#isRealError()}, in particular the "already in state"
 * idempotency carve-out mirrored from the official {@code navimow-sdk}.
 *
 * @author David Martin - Initial contribution
 */
class SendCommandsResultEntryTest {

    @Test
    void successStatusIsNotAnError() {
        SendCommandsResultEntry entry = new SendCommandsResultEntry();
        entry.status = "SUCCESS";

        assertThat(entry.isRealError(), is(false));
    }

    @Test
    void alreadyInStateErrorIsTreatedAsSuccess() {
        SendCommandsResultEntry entry = new SendCommandsResultEntry();
        entry.status = "ERROR";
        entry.errorCode = "alreadyInState";

        assertThat(entry.isRealError(), is(false));
    }

    @Test
    void otherErrorCodesAreRealErrors() {
        SendCommandsResultEntry entry = new SendCommandsResultEntry();
        entry.status = "ERROR";
        entry.errorCode = "deviceOffline";

        assertThat(entry.isRealError(), is(true));
    }

    @Test
    void errorStatusWithNoErrorCodeIsARealError() {
        SendCommandsResultEntry entry = new SendCommandsResultEntry();
        entry.status = "ERROR";
        entry.errorCode = null;

        assertThat(entry.isRealError(), is(true));
    }
}
