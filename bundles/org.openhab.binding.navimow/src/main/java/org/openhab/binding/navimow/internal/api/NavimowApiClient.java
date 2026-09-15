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

import static org.openhab.binding.navimow.internal.NavimowBindingConstants.API_PATH_AUTH_LIST;
import static org.openhab.binding.navimow.internal.NavimowBindingConstants.API_PATH_GET_VEHICLE_STATUS;
import static org.openhab.binding.navimow.internal.NavimowBindingConstants.API_PATH_SEND_COMMANDS;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.HttpResponseException;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.util.StringContentProvider;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpStatus;
import org.openhab.binding.navimow.internal.NavimowBindingConstants;
import org.openhab.binding.navimow.internal.api.dto.AuthListPayload;
import org.openhab.binding.navimow.internal.api.dto.DeviceRef;
import org.openhab.binding.navimow.internal.api.dto.GetVehicleStatusRequest;
import org.openhab.binding.navimow.internal.api.dto.MqttUserInfo;
import org.openhab.binding.navimow.internal.api.dto.MqttUserInfoResponse;
import org.openhab.binding.navimow.internal.api.dto.NavimowApiEnvelope;
import org.openhab.binding.navimow.internal.api.dto.NavimowApiResponseBase;
import org.openhab.binding.navimow.internal.api.dto.NavimowDevice;
import org.openhab.binding.navimow.internal.api.dto.NavimowDeviceStatus;
import org.openhab.binding.navimow.internal.api.dto.SendCommandsPayload;
import org.openhab.binding.navimow.internal.api.dto.SendCommandsRequest;
import org.openhab.binding.navimow.internal.api.dto.SendCommandsRequestCommand;
import org.openhab.binding.navimow.internal.api.dto.SendCommandsRequestExecution;
import org.openhab.binding.navimow.internal.api.dto.SendCommandsResultEntry;
import org.openhab.binding.navimow.internal.api.dto.VehicleStatusPayload;
import org.openhab.binding.navimow.internal.api.exceptions.NavimowAuthenticationException;
import org.openhab.binding.navimow.internal.api.exceptions.NavimowCommunicationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;

/**
 * {@link NavimowApiClient} talks to the Navimow cloud "smarthome" REST surface: listing devices,
 * fetching status, and sending mower commands. It does not perform authentication itself - it asks
 * a {@link NavimowAuthorizationProvider} for a fresh bearer access token on every request, so token
 * acquisition/refresh stays the bridge handler's responsibility (through openHAB's
 * {@code OAuthClientService}).
 *
 * <p>
 * <b>A token this class obtained cannot be reused from anywhere else.</b> Every standalone call
 * tried against this API using a token copied out of a running bridge - to {@code mqtt/userInfo}, and
 * even to {@code authList}, an endpoint this class calls successfully every poll cycle - has been
 * rejected with a business-level "invalid token" response, regardless of client library, host, or
 * network egress IP. The evidence points to the token being bound to whichever process first started
 * using it (this class, inside the account bridge): a second process replaying the same bearer value
 * looks, to Segway's backend, like a stolen token being replayed. See
 * {@code NavimowBindingConstants.BUSINESS_CODE_OAUTH_INFO_ILLEGAL} for the full investigation.
 * Practically: this API can only be validated through code running inside the same process as the
 * account bridge - never through a token copied out to an external tool.
 *
 * @author David Martin - Initial contribution
 */
@NonNullByDefault
public class NavimowApiClient {

    private static final int REQUEST_TIMEOUT_SECONDS = 10;

    private final Logger logger = LoggerFactory.getLogger(NavimowApiClient.class);
    private final HttpClient httpClient;
    private final String baseUrl;
    private final String mqttUserInfoUrl;
    private final NavimowAuthorizationProvider authorizationProvider;
    private final Gson gson = new Gson();

    /**
     * @param httpClient the shared Jetty client used to issue REST requests
     * @param authorizationProvider supplies a currently-valid bearer access token for each request
     */
    public NavimowApiClient(HttpClient httpClient, NavimowAuthorizationProvider authorizationProvider) {
        this(httpClient, NavimowBindingConstants.API_BASE_URL, NavimowBindingConstants.MQTT_USER_INFO_URL,
                authorizationProvider);
    }

    /**
     * @param httpClient the shared Jetty client used to issue REST requests
     * @param baseUrl the REST API base URL - overridable (rather than always using
     *            {@link NavimowBindingConstants#API_BASE_URL}) so tests can point this client at a
     *            local stub server instead of Segway's live cloud
     * @param authorizationProvider supplies a currently-valid bearer access token for each request
     */
    public NavimowApiClient(HttpClient httpClient, String baseUrl, NavimowAuthorizationProvider authorizationProvider) {
        this(httpClient, baseUrl, NavimowBindingConstants.MQTT_USER_INFO_URL, authorizationProvider);
    }

    /**
     * @param httpClient the shared Jetty client used to issue REST requests
     * @param baseUrl the REST API base URL - see the two-argument overload's Javadoc
     * @param mqttUserInfoUrl the {@code mqtt/userInfo} endpoint's full URL - separately overridable
     *            since it lives under a different host path than {@code baseUrl} (see
     *            {@link NavimowBindingConstants#MQTT_USER_INFO_URL}), so tests can redirect it to a
     *            local stub server too instead of quietly falling through to Segway's live cloud
     * @param authorizationProvider supplies a currently-valid bearer access token for each request
     */
    public NavimowApiClient(HttpClient httpClient, String baseUrl, String mqttUserInfoUrl,
            NavimowAuthorizationProvider authorizationProvider) {
        this.httpClient = httpClient;
        this.baseUrl = baseUrl;
        this.mqttUserInfoUrl = mqttUserInfoUrl;
        this.authorizationProvider = authorizationProvider;
    }

    /**
     * Fetches the list of devices linked to the authenticated account.
     *
     * @return the devices, possibly empty
     * @throws NavimowAuthenticationException if the access token was rejected
     * @throws NavimowCommunicationException if the request failed or the response could not be parsed
     */
    public List<NavimowDevice> getDevices() throws NavimowAuthenticationException, NavimowCommunicationException {
        Type responseType = new TypeToken<NavimowApiEnvelope<AuthListPayload>>() {
        }.getType();
        NavimowApiEnvelope<AuthListPayload> response = get(API_PATH_AUTH_LIST, responseType);
        requireSuccess(response, "authList");

        AuthListPayload payload = response.data != null ? response.data.payload : null;
        List<NavimowDevice> devices = payload != null ? payload.devices : null;
        return devices != null ? devices : List.of();
    }

    /**
     * Fetches the current status for the given devices in a single request.
     *
     * @param deviceIds the device ids to query
     * @return a map of device id to its status, only containing devices the API actually returned
     * @throws NavimowAuthenticationException if the access token was rejected
     * @throws NavimowCommunicationException if the request failed or the response could not be parsed
     */
    public Map<String, NavimowDeviceStatus> getDeviceStatuses(List<String> deviceIds)
            throws NavimowAuthenticationException, NavimowCommunicationException {
        if (deviceIds.isEmpty()) {
            return Map.of();
        }
        List<DeviceRef> refs = new ArrayList<>();
        for (String id : deviceIds) {
            refs.add(new DeviceRef(id));
        }
        String body = gson.toJson(new GetVehicleStatusRequest(refs));

        Type responseType = new TypeToken<NavimowApiEnvelope<VehicleStatusPayload>>() {
        }.getType();
        NavimowApiEnvelope<VehicleStatusPayload> response = post(API_PATH_GET_VEHICLE_STATUS, body, responseType);
        requireSuccess(response, "getVehicleStatus");

        VehicleStatusPayload payload = response.data != null ? response.data.payload : null;
        List<NavimowDeviceStatus> statuses = payload != null ? payload.devices : null;

        Map<String, NavimowDeviceStatus> result = new HashMap<>();
        if (statuses != null) {
            for (NavimowDeviceStatus status : statuses) {
                String id = status.id;
                if (id != null) {
                    result.put(id, status);
                }
            }
        }
        return result;
    }

    /**
     * Sends a mower command.
     *
     * <p>
     * A device-side "already in state" business error (e.g. asking a docked mower to dock again) is
     * treated as success, mirroring the official {@code navimow-sdk}: it is an idempotent no-op, not
     * a real failure.
     *
     * @param deviceId the target device id
     * @param command the command to send
     * @throws NavimowAuthenticationException if the access token was rejected
     * @throws NavimowCommunicationException if the request failed, was rejected, or the response
     *             could not be parsed
     */
    public void sendCommand(String deviceId, NavimowCommand command)
            throws NavimowAuthenticationException, NavimowCommunicationException {
        SendCommandsRequestExecution execution = new SendCommandsRequestExecution(command.getExecutionCommand(),
                command.getParams());
        SendCommandsRequestCommand requestCommand = new SendCommandsRequestCommand(List.of(new DeviceRef(deviceId)),
                execution);
        String body = gson.toJson(new SendCommandsRequest(List.of(requestCommand)));

        Type responseType = new TypeToken<NavimowApiEnvelope<SendCommandsPayload>>() {
        }.getType();
        NavimowApiEnvelope<SendCommandsPayload> response = post(API_PATH_SEND_COMMANDS, body, responseType);
        requireSuccess(response, "sendCommands");

        SendCommandsPayload payload = response.data != null ? response.data.payload : null;
        List<SendCommandsResultEntry> results = payload != null ? payload.commands : null;
        if (results != null) {
            for (SendCommandsResultEntry result : results) {
                if (result.isRealError()) {
                    throw new NavimowCommunicationException("Command failed with error code: " + result.errorCode);
                }
            }
        }
    }

    /**
     * Fetches MQTT broker connection info for the authenticated account.
     *
     * <p>
     * Not called by anything in this binding yet (REST-only for now) - exists so the endpoint is
     * independently testable through a real client rather than only via ad-hoc tools. See the class
     * Javadoc for why that distinction turned out to matter.
     *
     * @return the MQTT connection info
     * @throws NavimowAuthenticationException if the access token was rejected
     * @throws NavimowCommunicationException if the request failed, the endpoint reported a
     *             non-success business code, or the response could not be parsed
     */
    public MqttUserInfo getMqttUserInfo() throws NavimowAuthenticationException, NavimowCommunicationException {
        Request request = httpClient.newRequest(mqttUserInfoUrl).method(HttpMethod.GET);
        MqttUserInfoResponse response = send(request, mqttUserInfoUrl, MqttUserInfoResponse.class);
        requireSuccess(response, "mqtt/userInfo");

        MqttUserInfo data = response.data;
        if (data == null) {
            throw new NavimowCommunicationException("mqtt/userInfo response had no data");
        }
        return data;
    }

    private void requireSuccess(NavimowApiResponseBase response, String endpoint)
            throws NavimowAuthenticationException, NavimowCommunicationException {
        if (response.isSuccess()) {
            return;
        }
        /*
         * The cloud API can report an invalid/expired access token as a business-level failure
         * (HTTP 200, body {"code":4005,"desc":"CODE_OAUTH_INFO_ILLEGAL"}) rather than an HTTP
         * 401/403 - see NavimowBindingConstants.BUSINESS_CODE_OAUTH_INFO_ILLEGAL for how this was
         * found and its confirmation status. Without this check, that case would be misclassified
         * as a generic communication error instead of triggering re-authentication.
         */
        if (response.code == NavimowBindingConstants.BUSINESS_CODE_OAUTH_INFO_ILLEGAL
                || NavimowBindingConstants.BUSINESS_DESC_OAUTH_INFO_ILLEGAL.equals(response.desc)) {
            throw new NavimowAuthenticationException(
                    endpoint + " rejected the access token (code " + response.code + ": " + response.desc + ")");
        }
        throw new NavimowCommunicationException(endpoint + " failed with code " + response.code + ": " + response.desc);
    }

    private <T> T get(String path, Type responseType)
            throws NavimowAuthenticationException, NavimowCommunicationException {
        Request request = httpClient.newRequest(baseUrl + path).method(HttpMethod.GET);
        return send(request, path, responseType);
    }

    private <T> T post(String path, String jsonBody, Type responseType)
            throws NavimowAuthenticationException, NavimowCommunicationException {
        Request request = httpClient.newRequest(baseUrl + path).method(HttpMethod.POST)
                .content(new StringContentProvider(jsonBody), "application/json");
        return send(request, path, responseType);
    }

    private <T> T send(Request request, String path, Type responseType)
            throws NavimowAuthenticationException, NavimowCommunicationException {
        request.header(HttpHeader.AUTHORIZATION, "Bearer " + authorizationProvider.getAccessToken())
                .header("requestId", UUID.randomUUID().toString()).timeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        int status = 0;
        try {
            logger.trace("Sending {} request to {}", request.getMethod(), path);
            ContentResponse response = request.send();
            status = response.getStatus();
            String json = response.getContentAsString();
            logger.trace("Response ({}): {}", status, json);

            if (status == HttpStatus.UNAUTHORIZED_401 || status == HttpStatus.FORBIDDEN_403) {
                throw new NavimowAuthenticationException("Request rejected with HTTP " + status);
            }
            if (!HttpStatus.isSuccess(status)) {
                throw new NavimowCommunicationException("Request failed with HTTP " + status);
            }
            if (json == null || json.isEmpty()) {
                throw new NavimowCommunicationException("Empty response body");
            }

            @Nullable
            T result = gson.fromJson(json, responseType);
            if (result == null) {
                throw new NavimowCommunicationException("Parsed response was null");
            }
            return result;
        } catch (JsonParseException e) {
            throw new NavimowCommunicationException("Error parsing response", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new NavimowCommunicationException(e);
        } catch (TimeoutException e) {
            throw new NavimowCommunicationException(e);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof HttpResponseException httpResponseException) {
                /*
                 * The cloud API may respond with HTTP 401/403 without a "WWW-Authenticate" header,
                 * violating RFC 7235. Jetty's AuthenticationProtocolHandler then throws
                 * HttpResponseException from request.send() itself, before a Response is ever
                 * returned to the caller - so this has to be unwrapped here to still recognize it
                 * as an authentication failure rather than a generic communication error.
                 */
                int responseStatus = httpResponseException.getResponse().getStatus();
                if (responseStatus == HttpStatus.UNAUTHORIZED_401 || responseStatus == HttpStatus.FORBIDDEN_403) {
                    throw new NavimowAuthenticationException("Request rejected with HTTP " + responseStatus, e);
                }
            }
            throw new NavimowCommunicationException(e);
        }
    }
}
