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
import org.openhab.binding.navimow.internal.api.NavimowApiClient;
import org.openhab.binding.navimow.internal.api.exceptions.NavimowAuthenticationException;
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

    /**
     * Business-level "code" value the cloud API uses to report an invalid/expired access token.
     *
     * <p>
     * <b>Live-observed 2026-09-14</b> against the real API on a genuinely expired token: the
     * response was HTTP 200 with body {@code {"code":4005,"desc":"CODE_OAUTH_INFO_ILLEGAL"}} - not
     * an HTTP 401/403. Only directly confirmed on {@code mqtt/userInfo/get/v2} (not a REST call this
     * binding makes); however, the independent {@code niddu85/home-assistant-navimow} plugin's
     * source treats this exact {@code code}/{@code desc} pair as its "token expired, needs refresh"
     * signal specifically on {@code getVehicleStatus} - one of the endpoints this binding does call -
     * which is why {@link NavimowApiClient} generalizes the check to every endpoint rather than just
     * the one it was directly observed on.
     *
     * <p>
     * <b>Not yet reconfirmed against this binding's own {@code authList}/{@code getVehicleStatus}/
     * {@code sendCommands} calls in the wild.</b> Worth checking the logs after this has been running
     * for a while: if {@link NavimowAuthenticationException} is never actually triggered by this path
     * (only by real HTTP 401/403), either this account/token combination never hits it in practice, or
     * the generalization from the community plugin doesn't hold - in which case this check is dead
     * code, not a fix.
     */
    public static final int BUSINESS_CODE_OAUTH_INFO_ILLEGAL = 4005;

    /** @see #BUSINESS_CODE_OAUTH_INFO_ILLEGAL */
    public static final String BUSINESS_DESC_OAUTH_INFO_ILLEGAL = "CODE_OAUTH_INFO_ILLEGAL";
}
