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
package org.openhab.binding.navimow.internal.api;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * {@link NavimowHaPluginMimicLiveTest} is a manual, opt-in test against Segway's real cloud API -
 * <b>not part of the automated suite</b>, and deliberately independent of every other class in this
 * binding (no {@code NavimowApiClient}, no DTOs, no {@code NavimowAuthorizationProvider}). It exists
 * to answer one narrow question: does the request <i>mechanics</i> of the official
 * {@code segwaynavimow/NavimowHA} plugin's SDK ({@code navimow_sdk.api.MowerAPI}) - not just its
 * headers, which {@code NavimowApiClient} already matches - make a difference to
 * {@code mqtt/userInfo/get/v2}, which has rejected every attempt so far regardless of client
 * library, host, or network origin (see
 * {@code NavimowBindingConstants.BUSINESS_CODE_OAUTH_INFO_ILLEGAL}).
 *
 * <p>
 * Two things this test does differently from every earlier attempt, both taken directly from
 * {@code navimow_sdk.api.MowerAPI}'s actual source rather than guessed:
 * <ul>
 * <li><b>Exact header set.</b> {@code MowerAPI._get_auth_headers()} returns only
 * {@code Authorization}; {@code _async_request()} adds only {@code requestId} on top. No
 * {@code Content-Type} is ever set on a GET by the official SDK (the community
 * {@code home-assistant-navimow} plugin does add one - this test deliberately does not, to match the
 * official SDK specifically).
 * <li><b>Same persistent connection, same call order.</b> The official plugin's
 * {@code async_setup_entry} calls {@code async_get_devices()} (authList) and then, moments later,
 * {@code async_get_mqtt_user_info()} - both through the same {@code aiohttp.ClientSession}, so the
 * same pooled keep-alive TCP/TLS connection likely carries both. Every earlier attempt from this
 * binding's investigation called {@code mqtt/userInfo} cold, as the only request on a fresh
 * connection. This test calls authList first and mqtt/userInfo second, on one shared Jetty
 * {@link HttpClient}, to reproduce that.
 * </ul>
 *
 * <p>
 * What this test does <b>not</b> attempt: a genuinely fresh OAuth login (it reuses the account
 * bridge's already-refreshed token, the same as every earlier attempt), or anything not literally
 * present in the official SDK's source (e.g. no invented User-Agent override - the SDK doesn't set
 * one, so neither does this test).
 *
 * <p>
 * To run this manually: obtain a currently-valid access token (see
 * {@code NavimowApiClientMqttUserInfoLiveTest}'s Javadoc for how), export it as
 * {@code NAVIMOW_TEST_ACCESS_TOKEN}, remove {@code @Disabled}, and run just this test. Restore
 * {@code @Disabled} afterwards.
 *
 * <p>
 * <b>Live-run result, 2026-09-15: conclusive, and it's not about MQTT at all.</b> Both calls were
 * rejected with the identical {@code {"code":4005,"desc":"CODE_OAUTH_INFO_ILLEGAL"}} -
 * <i>including {@code authList}</i>, an endpoint the real account bridge calls successfully every
 * single poll cycle, using what should be the same token value. Matching the official SDK's exact
 * headers and connection reuse changed nothing. The only variable left that distinguishes every
 * failing attempt (this one, every {@code curl} attempt, the earlier standalone Jetty test) from
 * every succeeding one (the account bridge's own calls) is: <b>which process is making the
 * call</b> - not its IP, not its client library, not the endpoint. That fits an access token bound
 * to whichever client/session first started using it (almost certainly the account bridge itself),
 * where reusing the same bearer value from a second process looks - to Segway's backend -
 * indistinguishable from a stolen token being replayed. See
 * {@code NavimowBindingConstants.BUSINESS_CODE_OAUTH_INFO_ILLEGAL} for the consolidated writeup.
 * Practical consequence: no external tool, however faithful, can validate this API's behavior
 * against a token copied out of a running bridge - only code running inside that same bridge can.
 *
 * @author David Martin - Initial contribution
 */
@Disabled("Manual/live test against the real Navimow cloud API - see class Javadoc for how to run it")
public class NavimowHaPluginMimicLiveTest {

    // Exactly navimow_sdk.const's API_BASE_URL + the two endpoint paths, concatenated exactly as
    // MowerAPI._async_request does: f"{self.base_url}/{endpoint.lstrip('/')}".
    private static final String API_BASE_URL = "https://navimow-fra.ninebot.com";
    private static final String AUTH_LIST_PATH = "/openapi/smarthome/authList";
    private static final String MQTT_USER_INFO_PATH = "/openapi/mqtt/userInfo/get/v2";

    private HttpClient httpClient = new HttpClient(new SslContextFactory.Client());

    @BeforeEach
    void startClient() throws Exception {
        httpClient = new HttpClient(new SslContextFactory.Client());
        httpClient.start();
    }

    @AfterEach
    void stopClient() throws Exception {
        httpClient.stop();
    }

    @Test
    void mimicOfficialPluginSetupSequence() throws Exception {
        String accessToken = System.getenv("NAVIMOW_TEST_ACCESS_TOKEN");
        assumeTrue(accessToken != null && !accessToken.isBlank(),
                "Set NAVIMOW_TEST_ACCESS_TOKEN to a currently-valid access token to run this live test");
        String token = Objects.requireNonNull(accessToken);

        System.out.println("Step 1/2: GET authList (mirrors async_setup_entry's async_get_devices() call)");
        ContentResponse authListResponse = minimalGet(API_BASE_URL + AUTH_LIST_PATH, token);
        System.out.println(
                "authList: HTTP " + authListResponse.getStatus() + " body=" + authListResponse.getContentAsString());

        System.out.println(
                "Step 2/2: GET mqtt/userInfo on the SAME HttpClient/connection, immediately after (mirrors async_setup_entry's async_get_mqtt_user_info() call moments later, same aiohttp.ClientSession)");
        ContentResponse mqttResponse = minimalGet(API_BASE_URL + MQTT_USER_INFO_PATH, token);
        System.out.println("mqtt/userInfo: HTTP " + mqttResponse.getStatus() + " body="
                + redact(mqttResponse.getContentAsString()));
    }

    /**
     * A GET request carrying exactly the headers {@code navimow_sdk.api.MowerAPI} sends for a GET:
     * {@code Authorization} and {@code requestId}, nothing else - no {@code Content-Type}, no
     * {@code User-Agent} override.
     */
    private ContentResponse minimalGet(String url, String accessToken) throws Exception {
        Request request = httpClient.newRequest(url).method(HttpMethod.GET)
                .header(HttpHeader.AUTHORIZATION, "Bearer " + accessToken)
                .header("requestId", UUID.randomUUID().toString()).timeout(15, TimeUnit.SECONDS);
        return request.send();
    }

    private String redact(String body) {
        return body.replaceAll("\"(userName|pwdInfo)\":\"[^\"]*\"", "\"$1\":\"<redacted>\"");
    }
}
