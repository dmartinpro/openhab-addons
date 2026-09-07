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

import static org.openhab.binding.diagral.internal.DiagralBindingConstants.*;

import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
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
import org.openhab.core.thing.binding.BaseThingHandlerFactory;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.thing.binding.ThingHandlerFactory;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * The {@link DiagralHandlerFactory} is responsible for creating thing handlers.
 *
 * <p>
 * This factory creates the appropriate handler for each supported thing type:
 * <ul>
 * <li>Bridge - {@link DiagralBridgeHandler}</li>
 * <li>Alarm System - {@link DiagralSystemHandler}</li>
 * <li>Motion Sensor - {@link DiagralMotionSensorHandler}</li>
 * <li>Contact Sensor - {@link DiagralContactSensorHandler}</li>
 * <li>Group - {@link DiagralGroupHandler}</li>
 * <li>Siren - {@link DiagralSirenHandler}</li>
 * <li>Keypad - {@link DiagralKeypadHandler}</li>
 * <li>Plug - {@link DiagralPlugHandler}</li>
 * <li>Transmitter - {@link DiagralTransmitterHandler}</li>
 * <li>Camera - {@link DiagralCameraHandler}</li>
 * </ul>
 * </p>
 *
 * @author David Martin - Initial contribution
 */
@NonNullByDefault
@Component(configurationPid = "binding.diagral", service = ThingHandlerFactory.class)
public class DiagralHandlerFactory extends BaseThingHandlerFactory {

    /**
     * Maps each child thing type to its handler's constructor.
     *
     * <p>
     * The bridge is deliberately absent: it is the one type whose handler needs a collaborator beyond the
     * {@link Thing} itself (the shared HTTP client), so it is constructed explicitly in
     * {@link #createHandler(Thing)} rather than bent into this shape.
     * </p>
     */
    private static final Map<ThingTypeUID, Function<Thing, ThingHandler>> CHILD_HANDLER_FACTORIES = Map.of(
            THING_TYPE_ALARM_SYSTEM, DiagralSystemHandler::new, //
            THING_TYPE_MOTION_SENSOR, DiagralMotionSensorHandler::new, //
            THING_TYPE_CONTACT_SENSOR, DiagralContactSensorHandler::new, //
            THING_TYPE_GROUP, DiagralGroupHandler::new, //
            THING_TYPE_SIREN, DiagralSirenHandler::new, //
            THING_TYPE_KEYPAD, DiagralKeypadHandler::new, //
            THING_TYPE_PLUG, DiagralPlugHandler::new, //
            THING_TYPE_TRANSMITTER, DiagralTransmitterHandler::new, //
            THING_TYPE_CAMERA, DiagralCameraHandler::new);

    /**
     * Every thing type this binding supports - derived from {@link #CHILD_HANDLER_FACTORIES} rather than
     * listed again, so the two cannot disagree about what is supported.
     */
    private static final Set<ThingTypeUID> SUPPORTED_THING_TYPES_UIDS = Stream
            .concat(Stream.of(THING_TYPE_BRIDGE), CHILD_HANDLER_FACTORIES.keySet().stream())
            .collect(Collectors.toUnmodifiableSet());

    private final HttpClient httpClient;

    /**
     * Creates a new DiagralHandlerFactory.
     *
     * @param httpClientFactory the HTTP client factory for creating HTTP clients
     */
    @Activate
    public DiagralHandlerFactory(@Reference HttpClientFactory httpClientFactory) {
        this.httpClient = httpClientFactory.getCommonHttpClient();
    }

    /**
     * Checks whether this factory can create a handler for the given thing type.
     *
     * @param thingTypeUID the thing type to check
     * @return {@code true} if the thing type is one of the 10 types this binding supports
     */
    @Override
    public boolean supportsThingType(ThingTypeUID thingTypeUID) {
        return SUPPORTED_THING_TYPES_UIDS.contains(thingTypeUID);
    }

    /**
     * Creates the handler instance for a given thing, based on its thing type.
     *
     * <p>
     * Adding a thing type means one entry in {@link #CHILD_HANDLER_FACTORIES}; {@link
     * #SUPPORTED_THING_TYPES_UIDS} follows from it automatically.
     * </p>
     *
     * @param thing the thing to create a handler for
     * @return the new handler instance, or {@code null} if the thing's type isn't supported (shouldn't
     *         happen in practice, since the framework only calls this for types {@link
     *         #supportsThingType(ThingTypeUID)} reported as supported)
     */
    @Override
    protected @Nullable ThingHandler createHandler(Thing thing) {
        ThingTypeUID thingTypeUID = thing.getThingTypeUID();

        if (THING_TYPE_BRIDGE.equals(thingTypeUID)) {
            return new DiagralBridgeHandler((Bridge) thing, httpClient);
        }

        Function<Thing, ThingHandler> handlerFactory = CHILD_HANDLER_FACTORIES.get(thingTypeUID);
        return handlerFactory == null ? null : handlerFactory.apply(thing);
    }
}
