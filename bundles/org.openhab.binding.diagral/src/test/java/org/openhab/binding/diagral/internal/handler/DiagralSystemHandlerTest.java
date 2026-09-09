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

import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static org.openhab.binding.diagral.internal.DiagralBindingConstants.*;

import java.util.List;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.openhab.binding.diagral.internal.bridge.DiagralBridgeHandler;
import org.openhab.core.config.core.Configuration;
import org.openhab.core.library.types.StringType;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.binding.ThingHandlerCallback;

/**
 * Unit tests for {@link DiagralSystemHandler}, covering the multi-group {@code activate-groups}/
 * {@code disable-groups} channels: CSV command parsing (trimming, empty-entry handling) and correct
 * delegation to {@link DiagralBridgeHandler#activateGroups(List)}/{@link
 * DiagralBridgeHandler#disableGroups(List)}.
 *
 * @author David Martin - Initial contribution
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@NonNullByDefault
public class DiagralSystemHandlerTest {

    /** A handler whose {@code getBridge()} is overridden, since the real one needs a live thing registry. */
    @NonNullByDefault
    private static final class TestableDiagralSystemHandler extends DiagralSystemHandler {

        private volatile @Nullable Bridge bridge;

        TestableDiagralSystemHandler(Thing thing) {
            super(thing);
        }

        @Override
        public @Nullable Bridge getBridge() {
            return bridge;
        }
    }

    private @Mock @NonNullByDefault({}) DiagralBridgeHandler bridgeHandler;

    private @NonNullByDefault({}) TestableDiagralSystemHandler handler;
    private @NonNullByDefault({}) ChannelUID activateGroupsChannel;
    private @NonNullByDefault({}) ChannelUID disableGroupsChannel;

    @BeforeEach
    public void setUp() {
        ThingUID thingUID = new ThingUID("diagral", "alarm-system", "test");
        Thing thing = mock(Thing.class);
        when(thing.getUID()).thenReturn(thingUID);
        when(thing.getConfiguration()).thenReturn(new Configuration(Map.of()));

        Bridge bridge = mock(Bridge.class);
        when(bridge.getHandler()).thenReturn(bridgeHandler);

        handler = new TestableDiagralSystemHandler(thing);
        handler.bridge = bridge;
        handler.setCallback(mock(ThingHandlerCallback.class));

        activateGroupsChannel = new ChannelUID(thingUID, CHANNEL_ACTIVATE_GROUPS);
        disableGroupsChannel = new ChannelUID(thingUID, CHANNEL_DISABLE_GROUPS);
    }

    /** A plain CSV command activates exactly those groups, in order. */
    @Test
    public void activateGroupsCommandActivatesEveryListedGroup() {
        handler.handleCommand(activateGroupsChannel, new StringType("1,3,5"));

        verify(bridgeHandler).activateGroups(List.of("1", "3", "5"));
    }

    /** Same as above, for the disable channel. */
    @Test
    public void disableGroupsCommandDisablesEveryListedGroup() {
        handler.handleCommand(disableGroupsChannel, new StringType("2,4"));

        verify(bridgeHandler).disableGroups(List.of("2", "4"));
    }

    /** Whitespace around entries is trimmed before the list reaches the bridge. */
    @Test
    public void whitespaceAroundGroupIdsIsTrimmed() {
        handler.handleCommand(activateGroupsChannel, new StringType(" 1 , 3 ,5 "));

        verify(bridgeHandler).activateGroups(List.of("1", "3", "5"));
    }

    /** A trailing/leading/doubled comma produces empty entries that must be dropped, not passed through. */
    @Test
    public void emptyEntriesFromStrayCommasAreDropped() {
        handler.handleCommand(activateGroupsChannel, new StringType("1,,3,"));

        verify(bridgeHandler).activateGroups(List.of("1", "3"));
    }

    /** A single group ID (no comma at all) still works - the channel isn't only for batches. */
    @Test
    public void singleGroupIdWithNoCommaStillWorks() {
        handler.handleCommand(activateGroupsChannel, new StringType("7"));

        verify(bridgeHandler).activateGroups(List.of("7"));
    }

    /** A command that parses down to nothing (blank, or only commas) must not reach the bridge at all. */
    @Test
    public void blankCommandActivatesNothing() {
        handler.handleCommand(activateGroupsChannel, new StringType("  , , "));

        verify(bridgeHandler, never()).activateGroups(any());
    }

    /** Same empty-command guard, for the disable channel. */
    @Test
    public void blankCommandDisablesNothing() {
        handler.handleCommand(disableGroupsChannel, new StringType(""));

        verify(bridgeHandler, never()).disableGroups(any());
    }

    /** With no bridge handler available, the command is logged and dropped rather than throwing. */
    @Test
    public void missingBridgeHandlerIsHandledGracefully() {
        handler.bridge = null;

        handler.handleCommand(activateGroupsChannel, new StringType("1,2"));

        verify(bridgeHandler, never()).activateGroups(any());
    }

    /** Commands on unrelated channels are ignored - no accidental cross-wiring. */
    @Test
    public void unrelatedChannelIsIgnored() {
        handler.handleCommand(new ChannelUID(new ThingUID("diagral", "alarm-system", "test"), CHANNEL_ARMED_STATUS),
                new StringType("1,2"));

        verify(bridgeHandler, never()).activateGroups(any());
        verify(bridgeHandler, never()).disableGroups(any());
        verify(bridgeHandler, never()).setSystemMode(any());
    }
}
