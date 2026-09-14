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

import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.thing.ThingTypeUID;

/**
 * The {@link NavimowBindingConstants} class defines common constants, which are
 * used across the whole binding.
 *
 * <p>
 * The OAuth2 endpoints and client credentials below were not published by Segway/Navimow as a
 * documented developer API. They were reverse-engineered from two independent open-source Home
 * Assistant integrations ({@code segwaynavimow/NavimowHA} and {@code niddu85/home-assistant-navimow})
 * which both hardcode the same {@code client_id}/{@code client_secret} pair. The second, independent
 * implementation confirms this is a standard OAuth2 Authorization Code Grant with an arbitrary
 * (not allow-listed to a single fixed URL) {@code redirect_uri}, which is what makes reusing it from
 * openHAB's own local callback servlet workable.
 *
 * @author David Martin - Initial contribution
 */
@NonNullByDefault
public class NavimowBindingConstants {

    public static final String BINDING_ID = "navimow";

    // List of all Thing Type UIDs
    public static final ThingTypeUID THING_TYPE_ACCOUNT = new ThingTypeUID(BINDING_ID, "account");
    public static final ThingTypeUID THING_TYPE_MOWER = new ThingTypeUID(BINDING_ID, "mower");

    public static final Set<ThingTypeUID> SUPPORTED_THING_TYPES_UIDS = Set.of(THING_TYPE_ACCOUNT, THING_TYPE_MOWER);

    // List of all Channel ids
    public static final String CHANNEL_ACTIVITY = "activity";
    public static final String CHANNEL_CONTROL = "control";
    public static final String CHANNEL_BATTERY_LEVEL = "battery-level";

    // Device properties
    public static final String PROPERTY_MODEL = "model";

    /**
     * OAuth2 client id shared by every known third-party Navimow integration (identifies the client
     * as "homeassistant" to Segway's servers, but is not enforced to a Home Assistant-specific
     * redirect_uri in practice).
     */
    public static final String OAUTH_CLIENT_ID = "homeassistant";

    /**
     * OAuth2 client secret paired with {@link #OAUTH_CLIENT_ID}. Reverse-engineered, not officially
     * published.
     */
    public static final String OAUTH_CLIENT_SECRET = "57056e15-722e-42be-bbaa-b0cbfb208a52";

    /**
     * Hosted HTML login page acting as the OAuth2 authorization endpoint. Not a machine-readable API
     * endpoint - the binding only redirects the user's browser here.
     */
    public static final String OAUTH_AUTHORIZE_URL = "https://navimow-h5-fra.willand.com/smartHome/login";

    /**
     * OAuth2 token endpoint used for both the authorization_code exchange and refresh_token grants.
     */
    public static final String OAUTH_TOKEN_URL = "https://navimow-fra.ninebot.com/openapi/oauth/getAccessToken";

    /** Base URL for the REST "smarthome" API surface (device list, status, commands). */
    public static final String API_BASE_URL = "https://navimow-fra.ninebot.com/openapi/smarthome";

    public static final String API_PATH_AUTH_LIST = "/authList";
    public static final String API_PATH_GET_VEHICLE_STATUS = "/getVehicleStatus";
    public static final String API_PATH_SEND_COMMANDS = "/sendCommands";

    /** Default REST polling interval in seconds, used when the bridge config leaves it unset. */
    public static final int DEFAULT_POLLING_INTERVAL_S = 60;
}
