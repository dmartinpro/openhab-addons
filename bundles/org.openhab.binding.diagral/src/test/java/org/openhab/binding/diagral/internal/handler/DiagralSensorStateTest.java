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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.openhab.binding.diagral.internal.DiagralBindingConstants.*;

import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.openhab.binding.diagral.internal.dto.DiagralDevice;
import org.openhab.core.config.core.Configuration;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.thing.Channel;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.binding.ThingHandlerCallback;
import org.openhab.core.types.State;
import org.openhab.core.types.UnDefType;

/**
 * Unit tests for finding C5 of the 2026-09-04 review: the motion and contact channels must report
 * {@code UNDEF} rather than a fabricated {@code OFF}/{@code CLOSED}, because the Diagral cloud API
 * provides no live value for either.
 *
 * <p>
 * This matters specifically because it is a security binding - a channel that permanently asserts "no
 * motion" or "door closed" reads as working while telling the user nothing, and a rule built on it would
 * never fire.
 * </p>
 *
 * @author David Martin - Initial contribution
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@NonNullByDefault
public class DiagralSensorStateTest {

    /**
     * Builds a handler wired to a mocked callback, so the states it publishes can be captured.
     *
     * @param handler the handler under test
     * @param thing the mocked thing it handles
     * @param channelId the channel the handler is expected to update
     * @return the mocked callback
     */
    private ThingHandlerCallback wire(DiagralSensorHandler handler, Thing thing, String channelId) {
        ThingUID thingUID = new ThingUID("diagral", "sensor", "test");
        when(thing.getUID()).thenReturn(thingUID);
        when(thing.getConfiguration()).thenReturn(new Configuration(Map.of()));
        ChannelUID channelUID = new ChannelUID(thingUID, channelId);
        when(thing.getChannel(channelId)).thenReturn(mock(Channel.class));
        when(thing.getChannel(channelUID)).thenReturn(mock(Channel.class));

        ThingHandlerCallback callback = mock(ThingHandlerCallback.class);
        handler.setCallback(callback);
        return callback;
    }

    /**
     * Captures the state published to one channel.
     *
     * @param callback the mocked callback
     * @param channelId the channel to capture
     * @return the published state
     */
    private State captureState(ThingHandlerCallback callback, String channelId) {
        ArgumentCaptor<ChannelUID> uid = ArgumentCaptor.forClass(ChannelUID.class);
        ArgumentCaptor<State> state = ArgumentCaptor.forClass(State.class);
        verify(callback, atLeastOnce()).stateUpdated(uid.capture(), state.capture());

        for (int i = uid.getAllValues().size() - 1; i >= 0; i--) {
            if (channelId.equals(uid.getAllValues().get(i).getId())) {
                return state.getAllValues().get(i);
            }
        }
        throw new AssertionError("no state published to channel " + channelId);
    }

    /**
     * C5: the motion channel reports UNDEF, not a fabricated OFF.
     */
    @Test
    public void motionChannelReportsUndef() {
        Thing thing = mock(Thing.class);
        DiagralMotionSensorHandler handler = new DiagralMotionSensorHandler(thing);
        ThingHandlerCallback callback = wire(handler, thing, CHANNEL_MOTION);

        handler.updateSensorSpecificChannels(new DiagralDevice());

        State published = captureState(callback, CHANNEL_MOTION);
        assertThat(published, is(instanceOf(UnDefType.class)));
        assertThat(published, is(UnDefType.UNDEF));
        assertThat("must not claim 'no motion'", published, is(not(OnOffType.OFF)));
    }

    /**
     * C5: the contact channel reports UNDEF, not a fabricated CLOSED.
     */
    @Test
    public void contactChannelReportsUndef() {
        Thing thing = mock(Thing.class);
        DiagralContactSensorHandler handler = new DiagralContactSensorHandler(thing);
        ThingHandlerCallback callback = wire(handler, thing, CHANNEL_CONTACT);

        handler.updateSensorSpecificChannels(new DiagralDevice());

        State published = captureState(callback, CHANNEL_CONTACT);
        assertThat(published, is(instanceOf(UnDefType.class)));
        assertThat(published, is(UnDefType.UNDEF));
    }

    /**
     * Regression guard for C5: the channels that <em>do</em> have real backing data must keep reporting
     * real values. Only motion/contact are unavailable from the API - {@code enabled} comes straight from
     * the device's {@code inhibited} flag and must not become UNDEF too.
     */
    @Test
    public void enabledChannelStillReportsRealData() throws Exception {
        Thing thing = mock(Thing.class);
        DiagralMotionSensorHandler handler = new DiagralMotionSensorHandler(thing);
        ThingHandlerCallback callback = wire(handler, thing, CHANNEL_ENABLED);

        DiagralDevice device = new DiagralDevice();
        device.inhibited = false;
        invokeUpdateChannels(handler, device);

        assertThat(captureState(callback, CHANNEL_ENABLED), is(OnOffType.ON));

        Thing inhibitedThing = mock(Thing.class);
        DiagralMotionSensorHandler inhibitedHandler = new DiagralMotionSensorHandler(inhibitedThing);
        ThingHandlerCallback inhibitedCallback = wire(inhibitedHandler, inhibitedThing, CHANNEL_ENABLED);
        DiagralDevice inhibitedDevice = new DiagralDevice();
        inhibitedDevice.inhibited = true;
        invokeUpdateChannels(inhibitedHandler, inhibitedDevice);

        assertThat(captureState(inhibitedCallback, CHANNEL_ENABLED), is(OnOffType.OFF));
    }

    /**
     * Regression guard for C5: {@code low-battery} is derived from the device's real anomalies map and
     * must keep reporting a real value.
     */
    @Test
    public void lowBatteryChannelStillReportsRealData() throws Exception {
        Thing thing = mock(Thing.class);
        DiagralMotionSensorHandler handler = new DiagralMotionSensorHandler(thing);
        ThingHandlerCallback callback = wire(handler, thing, CHANNEL_LOW_BATTERY);

        DiagralDevice device = new DiagralDevice();
        device.anomalies = Map.of(DEVICE_ANOMALY_POWER_SUPPLY_ALERT, true);
        invokeUpdateChannels(handler, device);

        assertThat(captureState(callback, CHANNEL_LOW_BATTERY), is(OnOffType.ON));
    }

    /**
     * Invokes {@code DiagralSensorHandler.updateChannels(DiagralDevice)}, which is private.
     *
     * @param handler the handler under test
     * @param device the device data to apply
     */
    private void invokeUpdateChannels(DiagralSensorHandler handler, DiagralDevice device) throws Exception {
        java.lang.reflect.Method method = DiagralSensorHandler.class.getDeclaredMethod("updateChannels",
                DiagralDevice.class);
        method.setAccessible(true);
        method.invoke(handler, device);
    }

    /**
     * Keeps the {@code eq} import meaningful and asserts the harness itself publishes to the channel
     * under test rather than silently to none.
     */
    @Test
    public void harnessPublishesToTheExpectedChannel() {
        Thing thing = mock(Thing.class);
        DiagralContactSensorHandler handler = new DiagralContactSensorHandler(thing);
        ThingHandlerCallback callback = wire(handler, thing, CHANNEL_CONTACT);

        handler.updateSensorSpecificChannels(new DiagralDevice());

        verify(callback).stateUpdated(argThat(uid -> CHANNEL_CONTACT.equals(uid.getId())), eq(UnDefType.UNDEF));
    }
}
