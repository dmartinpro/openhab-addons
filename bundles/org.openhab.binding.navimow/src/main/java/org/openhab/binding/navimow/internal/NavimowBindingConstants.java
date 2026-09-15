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
     * Business-level "code" value the cloud API uses to report an invalid/expired access token -
     * <b>or possibly something else entirely; see the 2026-09-15 update below.</b>
     *
     * <p>
     * <b>Live-observed 2026-09-14</b> against the real API on a genuinely expired token: the
     * response was HTTP 200 with body {@code {"code":4005,"desc":"CODE_OAUTH_INFO_ILLEGAL"}} - not
     * an HTTP 401/403. Only directly confirmed on {@code mqtt/userInfo/get/v2} (not a REST call this
     * binding makes); however, the independent {@code niddu85/home-assistant-navimow} plugin's
     * source treats this exact {@code code}/{@code desc} pair as its "token expired, needs refresh"
     * signal specifically on {@code getVehicleStatus} - one of the endpoints this binding does call -
     * which is why {@code NavimowApiClient} generalizes the check to every endpoint rather than just
     * the one it was directly observed on.
     *
     * <p>
     * <b>2026-09-15 update - reproduced again, but the "expired token" framing now looks doubtful.</b>
     * During a live command-sequence test, a {@code STOP} command and every following status poll
     * were issued as standalone HTTP calls (outside this binding's own client, since {@code STOP}
     * wasn't part of {@code NavimowCommand} yet) using freshly-read, currently-valid access tokens -
     * every single one got this exact {@code code}/{@code desc} back, while this binding's own real
     * calls succeeded throughout the identical time window. That pattern - rejected consistently
     * outside the real client, never once triggered by the real client - fits "the server is
     * fingerprinting something about the request/client and rejecting ad-hoc ones" at least as well
     * as "token expired", possibly better. If that reading is right, generalizing this code to mean
     * "needs re-authentication" (as {@code NavimowApiClient#requireSuccess} currently does) could be
     * the wrong response to it.
     *
     * <p>
     * <b>2026-09-15, later the same day - the "ad-hoc client" theory was wrong; revised to network
     * origin.</b> A manual Java test called {@code mqtt/userInfo} through a real Jetty
     * {@code HttpClient} - the same library {@code NavimowApiClient} itself uses, not {@code curl} -
     * and still got this exact {@code code}/{@code desc} back. Since a genuine Jetty client failed
     * the same way {@code curl} did, "ad-hoc tool vs. real client" cannot be the distinguishing
     * factor. The one thing every failing attempt still has in common: they all ran from the
     * developer's host machine, a different network origin than the account bridge's own Docker
     * container, whose calls succeeded throughout. The best-supported theory now is an IP/origin-bound
     * access token, not a request-shape or client-identity check.
     *
     * <p>
     * <b>Still not reconfirmed against this binding's own {@code authList}/{@code getVehicleStatus}/
     * {@code sendCommands} calls.</b> That absence is now doing double duty as evidence: it's
     * consistent with either "this account's token just hasn't expired at the wrong moment yet" or
     * "this binding's own calls always originate from the account bridge's own container, so they
     * never cross whatever origin check {@code mqtt/userInfo} applies." Worth checking the logs
     * later: if {@code NavimowAuthenticationException} is only ever thrown by real HTTP 401/403,
     * revisit whether this business-code check belongs here at all.
     */
    public static final int BUSINESS_CODE_OAUTH_INFO_ILLEGAL = 4005;

    /** @see #BUSINESS_CODE_OAUTH_INFO_ILLEGAL */
    public static final String BUSINESS_DESC_OAUTH_INFO_ILLEGAL = "CODE_OAUTH_INFO_ILLEGAL";
}
