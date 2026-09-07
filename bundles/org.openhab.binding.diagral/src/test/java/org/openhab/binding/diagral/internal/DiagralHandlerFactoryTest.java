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
package org.openhab.binding.diagral.internal;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static org.openhab.binding.diagral.internal.DiagralBindingConstants.*;

import java.util.Map;
import java.util.Objects;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jetty.client.HttpClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openhab.binding.diagral.internal.bridge.DiagralBridgeHandler;
import org.openhab.binding.diagral.internal.handler.DiagralCameraHandler;
import org.openhab.binding.diagral.internal.handler.DiagralContactSensorHandler;
import org.openhab.binding.diagral.internal.handler.DiagralGroupHandler;
import org.openhab.binding.diagral.internal.handler.DiagralKeypadHandler;
import org.openhab.binding.diagral.internal.handler.DiagralMotionSensorHandler;
import org.openhab.binding.diagral.internal.handler.DiagralPlugHandler;
import org.openhab.binding.diagral.internal.handler.DiagralSirenHandler;
import org.openhab.binding.diagral.internal.handler.DiagralSystemHandler;
import org.openhab.binding.diagral.internal.handler.DiagralTransmitterHandler;
import org.openhab.core.io.net.http.HttpClientFactory;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.binding.ThingHandler;

/**
 * Unit tests for {@link DiagralHandlerFactory}, added alongside the D5 cleanup that replaced its
 * ten-branch {@code if/else} chain with a lookup table.
 *
 * <p>
 * The mapping had no coverage at all before, which is exactly where a table-driven rewrite could silently
 * mis-wire a type. These tests pin every supported type to its handler class, and pin the supported-types
 * set to the table it is now derived from.
 * </p>
 *
 * @author David Martin - Initial contribution
 */
@NonNullByDefault
public class DiagralHandlerFactoryTest {

    private @NonNullByDefault({}) DiagralHandlerFactory factory;

    /** Builds a factory with a mocked HTTP client factory. */
    @BeforeEach
    public void setUp() {
        HttpClientFactory httpClientFactory = mock(HttpClientFactory.class);
        when(httpClientFactory.getCommonHttpClient()).thenReturn(mock(HttpClient.class));
        factory = new DiagralHandlerFactory(httpClientFactory);
    }

    /**
     * Creates a handler for one thing type.
     *
     * @param thingTypeUID the type to create
     * @param bridge whether the thing should be a {@link Bridge}
     * @return the handler the factory produced
     */
    private ThingHandler create(ThingTypeUID thingTypeUID, boolean bridge) {
        Thing thing = bridge ? mock(Bridge.class) : mock(Thing.class);
        when(thing.getThingTypeUID()).thenReturn(thingTypeUID);
        when(thing.getUID()).thenReturn(new ThingUID(thingTypeUID, "test"));

        return Objects.requireNonNull(factory.createHandler(thing), "no handler created for " + thingTypeUID);
    }

    /** D5: every supported type maps to its own handler class - the wiring the lookup table replaced. */
    @Test
    public void everySupportedTypeMapsToItsHandler() {
        assertThat(create(THING_TYPE_BRIDGE, true), is(instanceOf(DiagralBridgeHandler.class)));

        Map<ThingTypeUID, Class<?>> expected = Map.of( //
                THING_TYPE_ALARM_SYSTEM, DiagralSystemHandler.class, //
                THING_TYPE_MOTION_SENSOR, DiagralMotionSensorHandler.class, //
                THING_TYPE_CONTACT_SENSOR, DiagralContactSensorHandler.class, //
                THING_TYPE_GROUP, DiagralGroupHandler.class, //
                THING_TYPE_SIREN, DiagralSirenHandler.class, //
                THING_TYPE_KEYPAD, DiagralKeypadHandler.class, //
                THING_TYPE_PLUG, DiagralPlugHandler.class, //
                THING_TYPE_TRANSMITTER, DiagralTransmitterHandler.class, //
                THING_TYPE_CAMERA, DiagralCameraHandler.class);

        expected.forEach((thingTypeUID, handlerClass) -> assertThat("wrong handler for " + thingTypeUID,
                create(thingTypeUID, false), is(instanceOf(handlerClass))));
    }

    /** Every mapped type must also be reported as supported, or the framework never asks for it. */
    @Test
    public void everyMappedTypeIsReportedAsSupported() {
        for (ThingTypeUID thingTypeUID : new ThingTypeUID[] { THING_TYPE_BRIDGE, THING_TYPE_ALARM_SYSTEM,
                THING_TYPE_MOTION_SENSOR, THING_TYPE_CONTACT_SENSOR, THING_TYPE_GROUP, THING_TYPE_SIREN,
                THING_TYPE_KEYPAD, THING_TYPE_PLUG, THING_TYPE_TRANSMITTER, THING_TYPE_CAMERA }) {
            assertThat(thingTypeUID + " should be supported", factory.supportsThingType(thingTypeUID), is(true));
        }
    }

    /** An unknown type is neither supported nor given a handler. */
    @Test
    public void unknownTypeIsRejected() {
        ThingTypeUID unknown = new ThingTypeUID("diagral", "does-not-exist");

        assertThat(factory.supportsThingType(unknown), is(false));

        Thing thing = mock(Thing.class);
        when(thing.getThingTypeUID()).thenReturn(unknown);
        assertThat(factory.createHandler(thing), is(nullValue()));
    }
}
