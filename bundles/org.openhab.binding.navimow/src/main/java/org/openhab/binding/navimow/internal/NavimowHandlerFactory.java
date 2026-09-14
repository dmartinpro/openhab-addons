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
package org.openhab.binding.navimow.internal;

import static org.openhab.binding.navimow.internal.NavimowBindingConstants.THING_TYPE_ACCOUNT;
import static org.openhab.binding.navimow.internal.NavimowBindingConstants.THING_TYPE_MOWER;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.openhab.binding.navimow.internal.handler.NavimowAccountHandler;
import org.openhab.binding.navimow.internal.handler.NavimowMowerHandler;
import org.openhab.core.auth.client.oauth2.OAuthFactory;
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
import org.osgi.service.http.HttpService;

/**
 * The {@link NavimowHandlerFactory} is responsible for creating things and thing
 * handlers.
 *
 * @author David Martin - Initial contribution
 */
@NonNullByDefault
@Component(configurationPid = "binding.navimow", service = ThingHandlerFactory.class)
public class NavimowHandlerFactory extends BaseThingHandlerFactory {

    private final HttpClient httpClient;
    private final HttpService httpService;
    private final OAuthFactory oAuthFactory;

    @Activate
    public NavimowHandlerFactory(final @Reference HttpClientFactory httpClientFactory,
            final @Reference HttpService httpService, final @Reference OAuthFactory oAuthFactory) {
        this.httpClient = httpClientFactory.getCommonHttpClient();
        this.httpService = httpService;
        this.oAuthFactory = oAuthFactory;
    }

    @Override
    public boolean supportsThingType(ThingTypeUID thingTypeUID) {
        return NavimowBindingConstants.SUPPORTED_THING_TYPES_UIDS.contains(thingTypeUID);
    }

    @Override
    protected @Nullable ThingHandler createHandler(Thing thing) {
        ThingTypeUID thingTypeUID = thing.getThingTypeUID();

        if (THING_TYPE_ACCOUNT.equals(thingTypeUID)) {
            return new NavimowAccountHandler((Bridge) thing, httpClient, httpService, oAuthFactory);
        } else if (THING_TYPE_MOWER.equals(thingTypeUID)) {
            return new NavimowMowerHandler(thing);
        }

        return null;
    }
}
