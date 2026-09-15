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
     * Business-level "code" value the cloud API uses to report an invalid/expired access token - or
     * possibly something else; the true cause is still unknown despite three rounds of live testing.
     * Response shape: HTTP 200 with body {@code {"code":4005,"desc":"CODE_OAUTH_INFO_ILLEGAL"}}, not
     * an HTTP 401/403.
     *
     * <p>
     * <b>Ruled out, in order, each disproven by the next test:</b>
     * <ol>
     * <li>2026-09-14 - "token had genuinely expired." True in the one case that found this, but
     * doesn't explain what came next.
     * <li>2026-09-15 - "ad-hoc tool ({@code curl}) vs. this binding's real client." A {@code STOP}
     * command and every status poll sent as standalone {@code curl} calls with fresh, valid tokens
     * were all rejected while the binding's own calls succeeded in the same window - but then a
     * standalone Java test using the same Jetty {@code HttpClient} library the binding itself uses
     * failed identically, ruling out "curl specifically."
     * <li>2026-09-15 - "network origin (IP) differs from the account bridge's container." Disproven by
     * directly comparing egress IPs: the container and the host machine share the exact same public
     * IP (both sit behind the same NAT), and a call made from inside the container itself (same
     * network namespace the binding's successful calls run in) still failed identically.
     * </ol>
     *
     * <p>
     * <b>Still standing, untested:</b> TLS/HTTP fingerprinting (JA3-style signature, ALPN/HTTP2
     * negotiation) differing between openHAB's shared {@code HttpClient} and any ad-hoc client; or
     * some form of session/connection continuity tied to the original OAuth login rather than the
     * bearer token alone. Not pursued further - each additional round means more probing of a
     * production third-party service for a question this binding (REST-only, no MQTT) doesn't
     * actually depend on the answer to.
     *
     * <p>
     * <b>Still not reconfirmed against this binding's own {@code authList}/{@code getVehicleStatus}/
     * {@code sendCommands} calls</b> - only ever seen via ad-hoc calls to {@code mqtt/userInfo} and,
     * once, a standalone {@code sendCommands}/{@code getVehicleStatus} call. Worth checking the logs
     * later: if {@code NavimowAuthenticationException} is only ever thrown by real HTTP 401/403 for
     * this binding's own traffic, this business-code check may be doing nothing in practice.
     */
    public static final int BUSINESS_CODE_OAUTH_INFO_ILLEGAL = 4005;

    /** @see #BUSINESS_CODE_OAUTH_INFO_ILLEGAL */
    public static final String BUSINESS_DESC_OAUTH_INFO_ILLEGAL = "CODE_OAUTH_INFO_ILLEGAL";
}
