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

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;

import org.eclipse.jetty.client.HttpClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openhab.binding.navimow.internal.api.dto.NavimowDevice;
import org.openhab.binding.navimow.internal.api.dto.NavimowDeviceStatus;
import org.openhab.binding.navimow.internal.api.exceptions.NavimowAuthenticationException;
import org.openhab.binding.navimow.internal.api.exceptions.NavimowCommunicationException;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

/**
 * Tests for {@link NavimowApiClient} against a real embedded HTTP server (WireMock), exercising
 * request/response handling that would be awkward to verify against mocked Jetty request builders:
 * business-code failures, HTTP-level auth failures, and the "already in state" idempotent success
 * path. The client is pointed at the WireMock server via its {@code baseUrl} constructor
 * parameter, so no request ever reaches Segway's real cloud.
 *
 * @author David Martin - Initial contribution
 */
class NavimowApiClientTest {

    private static final WireMockServer WIREMOCK_SERVER = new WireMockServer(
            WireMockConfiguration.options().dynamicPort());
    private static final HttpClient HTTP_CLIENT = new HttpClient();
    private static final String TEST_TOKEN = "test-access-token";

    private NavimowApiClient apiClient;

    @BeforeAll
    static void setUpHttpServer() throws Exception {
        WIREMOCK_SERVER.start();
        HTTP_CLIENT.start();
    }

    @AfterAll
    static void tearDownHttpServer() throws Exception {
        WIREMOCK_SERVER.stop();
        HTTP_CLIENT.stop();
    }

    @BeforeEach
    void setUp() {
        WireMock.configureFor("localhost", WIREMOCK_SERVER.port());
        WireMock.reset();
        apiClient = new NavimowApiClient(HTTP_CLIENT,
                "http://localhost:" + WIREMOCK_SERVER.port() + "/openapi/smarthome", () -> TEST_TOKEN);
    }

    @AfterEach
    void tearDown() {
        WireMock.reset();
    }

    @Test
    void getDevicesParsesSuccessfulResponse() throws Exception {
        stubFor(get(urlPathEqualTo("/openapi/smarthome/authList"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody("""
                        {"code":1,"desc":"success","data":{"payload":{"devices":[
                          {"id":"device-1","name":"Front Lawn","online":true}
                        ]}}}
                        """)));

        List<NavimowDevice> devices = apiClient.getDevices();

        assertThat(devices, hasSize(1));
        assertThat(devices.get(0).id, is("device-1"));
    }

    @Test
    void getDevicesThrowsCommunicationExceptionOnBusinessFailure() {
        stubFor(get(urlPathEqualTo("/openapi/smarthome/authList"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"code\":0,\"desc\":\"invalid token\",\"data\":null}")));

        assertThrows(NavimowCommunicationException.class, () -> apiClient.getDevices());
    }

    @Test
    void getDevicesThrowsAuthenticationExceptionOnHttp401() {
        stubFor(get(urlPathEqualTo("/openapi/smarthome/authList")).willReturn(aResponse().withStatus(401)));

        assertThrows(NavimowAuthenticationException.class, () -> apiClient.getDevices());
    }

    @Test
    void getDevicesThrowsCommunicationExceptionOnHttp500() {
        stubFor(get(urlPathEqualTo("/openapi/smarthome/authList")).willReturn(aResponse().withStatus(500)));

        assertThrows(NavimowCommunicationException.class, () -> apiClient.getDevices());
    }

    @Test
    void getDeviceStatusesReturnsEmptyMapWithoutNetworkCallForNoIds() throws Exception {
        Map<String, NavimowDeviceStatus> statuses = apiClient.getDeviceStatuses(List.of());

        assertThat(statuses.isEmpty(), is(true));
        verify(0, postRequestedFor(urlPathEqualTo("/openapi/smarthome/getVehicleStatus")));
    }

    @Test
    void getDeviceStatusesParsesSuccessfulResponse() throws Exception {
        stubFor(post(urlEqualTo("/openapi/smarthome/getVehicleStatus"))
                .withRequestBody(equalToJson("{\"devices\":[{\"id\":\"device-1\"}]}"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(
                        """
                                {"code":1,"desc":"success","data":{"payload":{"devices":[
                                  {"id":"device-1","vehicleState":"isDocked","capacityRemaining":[{"unit":"PERCENTAGE","rawValue":90}]}
                                ]}}}
                                """)));

        Map<String, NavimowDeviceStatus> statuses = apiClient.getDeviceStatuses(List.of("device-1"));

        assertThat(statuses.size(), is(1));
        NavimowDeviceStatus status = statuses.get("device-1");
        assertThat(status.vehicleState, is("isDocked"));
        assertThat(status.getBatteryPercentage(), is(90));
    }

    @Test
    void sendCommandSucceedsOnPlainSuccess() {
        stubFor(post(urlEqualTo("/openapi/smarthome/sendCommands"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody("""
                        {"code":1,"desc":"success","data":{"payload":{"commands":[{"status":"SUCCESS"}]}}}
                        """)));

        assertDoesNotThrow(() -> apiClient.sendCommand("device-1", NavimowCommand.START));
    }

    @Test
    void sendCommandTreatsAlreadyInStateAsSuccess() {
        stubFor(post(urlEqualTo("/openapi/smarthome/sendCommands"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody("""
                        {"code":1,"desc":"success","data":{"payload":{"commands":[
                          {"status":"ERROR","errorCode":"alreadyInState"}
                        ]}}}
                        """)));

        assertDoesNotThrow(() -> apiClient.sendCommand("device-1", NavimowCommand.DOCK));
    }

    @Test
    void sendCommandThrowsOnRealError() {
        stubFor(post(urlEqualTo("/openapi/smarthome/sendCommands"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody("""
                        {"code":1,"desc":"success","data":{"payload":{"commands":[
                          {"status":"ERROR","errorCode":"deviceOffline"}
                        ]}}}
                        """)));

        assertThrows(NavimowCommunicationException.class,
                () -> apiClient.sendCommand("device-1", NavimowCommand.START));
    }
}
