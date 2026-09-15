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

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.openhab.binding.navimow.internal.api.dto.MqttUserInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link NavimowApiClientMqttUserInfoLiveTest} is a manual, opt-in test against Segway's real cloud
 * API - <b>not part of the automated suite</b> ({@code @Disabled}, and gated behind an environment
 * variable even if that were lifted). It exists to settle whether {@code mqtt/userInfo} works when
 * called through a real HTTP client rather than an ad-hoc tool like {@code curl}, after every
 * {@code curl}-based attempt was rejected with a business-level "invalid token" response even with a
 * fresh, valid token.
 *
 * <p>
 * To run this manually: obtain a currently-valid access token for a real, authenticated Navimow
 * account bridge (e.g. from the running openHAB instance's OAuth token storage - see the
 * {@code NavimowAccountHandler} account bridge's Thing UID under
 * {@code StorageHandler.For.OAuthClientService.json} in openHAB's {@code userdata/jsondb}), export
 * it as {@code NAVIMOW_TEST_ACCESS_TOKEN}, remove the {@code @Disabled} annotation, and run just this
 * test (e.g. {@code mvn test -Dtest=NavimowApiClientMqttUserInfoLiveTest}). Restore
 * {@code @Disabled} afterwards - this must never run as part of the normal build.
 *
 * <p>
 * <b>Live-run result, 2026-09-15: also rejected</b> - {@code {"code":4005,"desc":"CODE_OAUTH_INFO_ILLEGAL"}},
 * the same as every {@code curl} attempt. This disproves the "ad-hoc tool" theory: this test uses a
 * real Jetty {@code HttpClient} (the same library the binding itself uses), not {@code curl}, and
 * still failed. The one thing every failing attempt shares - this test included - is running from a
 * different network origin than the binding's own successful calls: this test and every {@code curl}
 * attempt ran from the developer's host machine, while the binding runs (and succeeds) from inside a
 * Docker container with a different egress IP. That fits an IP/origin-bound token far better than
 * "curl vs. a real client" - see {@code NavimowBindingConstants.BUSINESS_CODE_OAUTH_INFO_ILLEGAL} and
 * {@link NavimowApiClient}'s class Javadoc for the corrected theory. A conclusive test would need to
 * run from the same network origin as the account bridge itself (i.e. inside the container) - not
 * attempted here, since that would mean adding a diagnostic call to production code rather than
 * keeping this endpoint's testing isolated to a throwaway test.
 *
 * @author David Martin - Initial contribution
 */
@Disabled("Manual/live test against the real Navimow cloud API - see class Javadoc for how to run it")
public class NavimowApiClientMqttUserInfoLiveTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(NavimowApiClientMqttUserInfoLiveTest.class);

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
    void liveGetMqttUserInfoAgainstRealApi() throws Exception {
        String accessToken = System.getenv("NAVIMOW_TEST_ACCESS_TOKEN");
        assumeTrue(accessToken != null && !accessToken.isBlank(),
                "Set NAVIMOW_TEST_ACCESS_TOKEN to a currently-valid access token to run this live test");
        String token = Objects.requireNonNull(accessToken);

        NavimowApiClient client = new NavimowApiClient(httpClient, () -> token);
        MqttUserInfo info = client.getMqttUserInfo();

        LOGGER.info("mqtt/userInfo succeeded: mqttHost={} mqttUrl={} userName={} pwdInfo={}", info.mqttHost,
                info.mqttUrl, info.userName, info.pwdInfo != null ? "<redacted>" : null);
    }
}
