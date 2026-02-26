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

import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.openhab.binding.diagral.internal.bridge.DiagralBridgeHandler;
import org.openhab.binding.diagral.internal.handler.DiagralContactSensorHandler;
import org.openhab.binding.diagral.internal.handler.DiagralGroupHandler;
import org.openhab.binding.diagral.internal.handler.DiagralMotionSensorHandler;
import org.openhab.binding.diagral.internal.handler.DiagralSystemHandler;
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
 * </ul>
 * </p>
 *
 * @author David Martin - Initial contribution
 */
@NonNullByDefault
@Component(configurationPid = "binding.diagral", service = ThingHandlerFactory.class)
public class DiagralHandlerFactory extends BaseThingHandlerFactory {

    private static final Set<ThingTypeUID> SUPPORTED_THING_TYPES_UIDS = Set.of(THING_TYPE_BRIDGE,
            THING_TYPE_ALARM_SYSTEM, THING_TYPE_MOTION_SENSOR, THING_TYPE_CONTACT_SENSOR, THING_TYPE_GROUP);

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

    @Override
    public boolean supportsThingType(ThingTypeUID thingTypeUID) {
        return SUPPORTED_THING_TYPES_UIDS.contains(thingTypeUID);
    }

    @Override
    protected @Nullable ThingHandler createHandler(Thing thing) {
        ThingTypeUID thingTypeUID = thing.getThingTypeUID();

        if (THING_TYPE_BRIDGE.equals(thingTypeUID)) {
            return new DiagralBridgeHandler((Bridge) thing, httpClient);
        } else if (THING_TYPE_ALARM_SYSTEM.equals(thingTypeUID)) {
            return new DiagralSystemHandler(thing);
        } else if (THING_TYPE_MOTION_SENSOR.equals(thingTypeUID)) {
            return new DiagralMotionSensorHandler(thing);
        } else if (THING_TYPE_CONTACT_SENSOR.equals(thingTypeUID)) {
            return new DiagralContactSensorHandler(thing);
        } else if (THING_TYPE_GROUP.equals(thingTypeUID)) {
            return new DiagralGroupHandler(thing);
        }

        return null;
    }
}
