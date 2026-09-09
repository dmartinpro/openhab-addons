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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.openhab.binding.diagral.internal.DiagralBindingConstants.*;

import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.openhab.binding.diagral.internal.bridge.DiagralBridgeHandler;
import org.openhab.binding.diagral.internal.bridge.DiagralPollSnapshot;
import org.openhab.core.config.core.Configuration;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.binding.ThingHandlerCallback;
import org.openhab.core.types.RefreshType;

/**
 * Unit tests for {@link DiagralGroupHandler}, covering the {@code group-id} channel: a numeric,
 * read-only reflection of the {@code groupId} configuration parameter, published once the thing goes
 * {@code ONLINE}, and the configuration-error handling for a non-numeric or missing value.
 *
 * @author David Martin - Initial contribution
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@NonNullByDefault
public class DiagralGroupHandlerTest {

    /** A handler whose {@code getBridge()} is overridden, since the real one needs a live thing registry. */
    @NonNullByDefault
    private static final class TestableDiagralGroupHandler extends DiagralGroupHandler {

        private volatile @Nullable Bridge bridge;

        TestableDiagralGroupHandler(Thing thing) {
            super(thing);
        }

        @Override
        public @Nullable Bridge getBridge() {
            return bridge;
        }
    }

    private @Mock @NonNullByDefault({}) DiagralBridgeHandler bridgeHandler;
    private @Mock @NonNullByDefault({}) ThingHandlerCallback callback;

    /**
     * Builds a handler configured with the given {@code groupId} value and a bridge already {@code
     * ONLINE}, so {@code initialize()} can be driven all the way to publishing channel states.
     *
     * @param groupId the {@code groupId} configuration value (empty string simulates it being unset)
     * @return the handler, not yet initialized
     */
    private TestableDiagralGroupHandler handlerWithGroupId(String groupId) {
        ThingUID thingUID = new ThingUID("diagral", "group", "test");
        Thing thing = mock(Thing.class);
        when(thing.getUID()).thenReturn(thingUID);
        when(thing.getConfiguration()).thenReturn(new Configuration(Map.of("groupId", groupId)));

        Bridge bridge = mock(Bridge.class);
        when(bridge.getStatus()).thenReturn(ThingStatus.ONLINE);
        when(bridge.getHandler()).thenReturn(bridgeHandler);
        when(bridgeHandler.captureSnapshot()).thenReturn(new DiagralPollSnapshot(null, null, () -> null));
        when(bridgeHandler.isGroupActive(any(), any())).thenReturn(false);

        TestableDiagralGroupHandler handler = new TestableDiagralGroupHandler(thing);
        handler.bridge = bridge;
        handler.setCallback(callback);
        return handler;
    }

    /** A valid numeric {@code groupId} is published on {@code group-id} as a {@link DecimalType}. */
    @Test
    public void validNumericGroupIdIsPublishedAsDecimalType() {
        TestableDiagralGroupHandler handler = handlerWithGroupId("3");

        handler.initialize();

        ChannelUID groupIdChannel = new ChannelUID(handler.getThing().getUID(), CHANNEL_GROUP_ID);
        verify(callback).stateUpdated(eq(groupIdChannel), eq(new DecimalType(3)));
    }

    /**
     * {@code group-id} is pure configuration, not bridge-derived data, so it must publish even when the
     * bridge isn't available yet - unlike {@code active}/{@code status}. Regression test for a bug caught
     * live (2026-09-09): this API's own well-documented flakiness routinely delays the bridge coming
     * online past this handler's {@code initialize()}, and {@code bridgeStatusChanged()} never re-publishes
     * {@code group-id}, so gating the publish on {@code goOnlineIfBridgeAvailable()} left it permanently
     * {@code NULL} in exactly that (common) case.
     */
    @Test
    public void groupIdIsPublishedEvenWithoutABridge() {
        ThingUID thingUID = new ThingUID("diagral", "group", "test");
        Thing thing = mock(Thing.class);
        when(thing.getUID()).thenReturn(thingUID);
        when(thing.getConfiguration()).thenReturn(new Configuration(Map.of("groupId", "3")));
        TestableDiagralGroupHandler handler = new TestableDiagralGroupHandler(thing);
        handler.setCallback(callback);
        // handler.bridge deliberately left null - simulates the bridge not being available yet.

        handler.initialize();

        ChannelUID groupIdChannel = new ChannelUID(thingUID, CHANNEL_GROUP_ID);
        verify(callback).stateUpdated(eq(groupIdChannel), eq(new DecimalType(3)));
        verify(callback).statusUpdated(eq(thing), argThat(info -> info.getStatus() == ThingStatus.OFFLINE));
    }

    /** A non-numeric {@code groupId} must not throw - the thing goes offline with a configuration error. */
    @Test
    public void nonNumericGroupIdGoesOfflineWithConfigurationError() {
        TestableDiagralGroupHandler handler = handlerWithGroupId("not-a-number");

        handler.initialize();

        verify(callback).statusUpdated(eq(handler.getThing()), argThat(info -> info.getStatus() == ThingStatus.OFFLINE
                && info.getStatusDetail() == ThingStatusDetail.CONFIGURATION_ERROR));
        verify(callback, never()).stateUpdated(any(), any());
    }

    /** A missing/empty {@code groupId} fails the existing {@code isValidGroup()} check first. */
    @Test
    public void emptyGroupIdGoesOfflineWithConfigurationError() {
        TestableDiagralGroupHandler handler = handlerWithGroupId("");

        handler.initialize();

        verify(callback).statusUpdated(eq(handler.getThing()), argThat(info -> info.getStatus() == ThingStatus.OFFLINE
                && info.getStatusDetail() == ThingStatusDetail.CONFIGURATION_ERROR));
        verify(callback, never()).stateUpdated(any(), any());
    }

    /**
     * {@code group-id} is read-only: a command on it must not reach the group-activation logic or throw,
     * even through the generic {@code handleCommand} dispatcher.
     */
    @Test
    public void commandOnGroupIdChannelIsIgnored() {
        TestableDiagralGroupHandler handler = handlerWithGroupId("3");
        handler.initialize();
        ChannelUID groupIdChannel = new ChannelUID(handler.getThing().getUID(), CHANNEL_GROUP_ID);

        handler.handleCommand(groupIdChannel, new StringType("5"));
        handler.handleCommand(groupIdChannel, RefreshType.REFRESH);

        verify(bridgeHandler, never()).activateGroup(any());
        verify(bridgeHandler, never()).disableGroup(any());
    }

    /** Guards the assumption every test above relies on: a real command on {@code active} still works. */
    @Test
    public void activeChannelCommandStillReachesTheBridge() {
        TestableDiagralGroupHandler handler = handlerWithGroupId("3");
        handler.initialize();
        ChannelUID activeChannel = new ChannelUID(handler.getThing().getUID(), CHANNEL_GROUP_ACTIVE);

        handler.handleCommand(activeChannel, OnOffType.ON);

        verify(bridgeHandler).activateGroup("3");
    }
}
