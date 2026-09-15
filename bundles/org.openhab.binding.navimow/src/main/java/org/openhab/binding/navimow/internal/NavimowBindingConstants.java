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

    /**
     * MQTT broker connection-info endpoint. Lives under a different path prefix than
     * {@link #API_BASE_URL} ({@code /openapi/mqtt/...} rather than {@code /openapi/smarthome/...}),
     * and its response envelope has a different shape too - {@code data} carries the fields directly
     * rather than nesting a further {@code payload} object - hence the dedicated
     * {@code MqttUserInfoResponse} envelope rather than reusing {@code NavimowApiEnvelope}.
     */
    public static final String MQTT_USER_INFO_URL = "https://navimow-fra.ninebot.com/openapi/mqtt/userInfo/get/v2";

    /** Default REST polling interval in seconds, used when the bridge config leaves it unset. */
    public static final int DEFAULT_POLLING_INTERVAL_S = 60;

    /**
     * Business-level "code" value the cloud API uses to report a token/session it won't honor.
     * Response shape: HTTP 200 with body {@code {"code":4005,"desc":"CODE_OAUTH_INFO_ILLEGAL"}}, not
     * an HTTP 401/403.
     *
     * <p>
     * <b>Conclusive as of 2026-09-15: this is not about MQTT, and never was.</b> Four theories were
     * tried and eliminated in sequence before the real pattern became visible:
     * <ol>
     * <li>"Token had genuinely expired" - true the one time it was first found, but didn't explain
     * what came next.
     * <li>"Ad-hoc tool ({@code curl}) vs. this binding's real client" - disproven by a standalone Java
     * test using the same Jetty {@code HttpClient} library the binding itself uses, which failed
     * identically.
     * <li>"Network origin (IP) differs from the account bridge's container" - disproven by directly
     * comparing egress IPs (identical - both sit behind the same NAT) and by calling from inside the
     * container itself, which still failed.
     * <li>"Something {@code mqtt/userInfo}-specific" - disproven by
     * {@code NavimowHaPluginMimicLiveTest}, which reproduced the official {@code segwaynavimow/
     * NavimowHA} plugin's exact request mechanics (its precise header set, and its connection-reuse
     * pattern of calling {@code authList} then {@code mqtt/userInfo} on one persistent client) and got
     * {@code CODE_OAUTH_INFO_ILLEGAL} on <i>both</i> calls - including {@code authList}, an endpoint
     * the real account bridge calls successfully every single poll cycle with what should be the same
     * token value.
     * </ol>
     *
     * <p>
     * <b>The pattern that survives all four rounds:</b> every failing attempt was a different process
     * than the one currently using the token; every succeeding call was the account bridge itself.
     * Not the IP, not the client library, not the endpoint - just "is this the process the token was
     * issued to." That fits an access token bound to whichever client/session first started using it
     * (here, the account bridge): a second process replaying the same bearer value looks, to Segway's
     * backend, indistinguishable from a stolen token being replayed elsewhere - which is exactly the
     * scenario a security-conscious backend would want to block, benign intent notwithstanding.
     *
     * <p>
     * <b>Practical consequence:</b> no external tool, however faithful to the real client's request
     * shape, can validate this API's behavior against a token copied out of a running bridge - only
     * code running inside that same bridge process can. This is why {@link NavimowApiClient}'s own
     * {@code authList}/{@code getVehicleStatus}/{@code sendCommands} calls have never independently
     * triggered this code (they always run as the bridge itself) while every deliberate external probe
     * has hit it on every endpoint tried.
     *
     * <p>
     * <b>Confirmed 2026-09-15, definitively.</b> A one-off diagnostic call to {@code mqtt/userInfo},
     * made from inside {@code NavimowAccountHandler} itself (the account bridge's own already-bound
     * session, not a copied token used elsewhere), succeeded immediately with a real, complete
     * response. This proves the theory above rather than just being consistent with it:
     * {@code mqtt/userInfo} was never broken, and this business code was never really about MQTT -
     * every prior failure was purely about which process was asking.
     */
    public static final int BUSINESS_CODE_OAUTH_INFO_ILLEGAL = 4005;

    /** @see #BUSINESS_CODE_OAUTH_INFO_ILLEGAL */
    public static final String BUSINESS_DESC_OAUTH_INFO_ILLEGAL = "CODE_OAUTH_INFO_ILLEGAL";
}
