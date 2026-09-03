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
package org.openhab.binding.diagral.internal.bridge;

import static org.openhab.binding.diagral.internal.DiagralBindingConstants.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.util.StringContentProvider;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpStatus;
import org.openhab.binding.diagral.internal.dto.DiagralAnomalies;
import org.openhab.binding.diagral.internal.dto.DiagralAnomalyDetail;
import org.openhab.binding.diagral.internal.dto.DiagralAnomalyName;
import org.openhab.binding.diagral.internal.dto.DiagralApiKeyRequest;
import org.openhab.binding.diagral.internal.dto.DiagralApiKeyResponse;
import org.openhab.binding.diagral.internal.dto.DiagralLoginRequest;
import org.openhab.binding.diagral.internal.dto.DiagralLoginResponse;
import org.openhab.binding.diagral.internal.dto.DiagralSystemConfiguration;
import org.openhab.binding.diagral.internal.dto.DiagralSystemDetails;
import org.openhab.binding.diagral.internal.dto.DiagralSystemStatus;
import org.openhab.binding.diagral.internal.exception.DiagralApiException;
import org.openhab.binding.diagral.internal.exception.DiagralAuthenticationException;
import org.openhab.binding.diagral.internal.exception.DiagralException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

/**
 * The {@link DiagralHttpClient} handles HTTP communication with the Diagral API.
 *
 * @author David Martin - Initial contribution
 */
@NonNullByDefault
public class DiagralHttpClient {

    private final Logger logger = LoggerFactory.getLogger(DiagralHttpClient.class);
    private static final int REQUEST_TIMEOUT_SECONDS = 10;
    private static final String CONTENT_TYPE_JSON = "application/json";
    private static final Set<String> VALID_PRODUCT_TYPES = Set.of(PRODUCT_TYPE_CENTRAL, PRODUCT_TYPE_SENSOR,
            PRODUCT_TYPE_COMMAND, PRODUCT_TYPE_ALARM, PRODUCT_TYPE_BOX, PRODUCT_TYPE_PLUG);

    private final HttpClient httpClient;
    private final DiagralAuthenticationManager authManager;
    private final Gson gson;

    public DiagralHttpClient(HttpClient httpClient, DiagralAuthenticationManager authManager) {
        this.httpClient = httpClient;
        this.authManager = authManager;
        this.gson = new GsonBuilder().create();
    }

    /**
     * Performs the complete authentication flow: login and API key generation
     *
     * @throws DiagralAuthenticationException if authentication fails
     */
    public void authenticate() throws DiagralAuthenticationException {
        try {
            logger.debug("Starting authentication for user: {}", authManager.getUsername());

            // Step 1: Login to get access token
            String accessToken = login();

            // Step 2: Generate API key pair
            generateApiKey(accessToken);

            logger.info("Authentication successful");
        } catch (DiagralAuthenticationException e) {
            authManager.clearApiKeys();
            throw e;
        }
    }

    /**
     * Performs login and returns the access token
     *
     * @return the access token
     * @throws DiagralAuthenticationException if login fails
     */
    private String login() throws DiagralAuthenticationException {
        String url = API_BASE_URL + API_ENDPOINT_LOGIN + "?vendor=" + VENDOR_PARAM;

        DiagralLoginRequest loginRequest = new DiagralLoginRequest(authManager.getUsername(),
                authManager.getPassword());
        String requestBody = gson.toJson(loginRequest);

        try {
            String responseBody = executeUnauthenticatedPost(url, requestBody);
            DiagralLoginResponse response = gson.fromJson(responseBody, DiagralLoginResponse.class);

            String token = (response != null) ? response.accessToken : null;
            if (token == null || token.isEmpty()) {
                throw new DiagralAuthenticationException("Login failed: No access token received");
            }

            logger.debug("Login successful, access token obtained");
            return token;
        } catch (JsonSyntaxException e) {
            throw new DiagralAuthenticationException("Invalid JSON response from login", e);
        } catch (DiagralException e) {
            throw new DiagralAuthenticationException("Login request failed", e);
        }
    }

    /**
     * Generates API key pair using the access token
     *
     * @param accessToken the access token from login
     * @throws DiagralAuthenticationException if API key generation fails
     */
    private void generateApiKey(String accessToken) throws DiagralAuthenticationException {
        String url = API_BASE_URL + API_ENDPOINT_API_KEY;

        DiagralApiKeyRequest apiKeyRequest = new DiagralApiKeyRequest(authManager.getSerialId());
        String requestBody = gson.toJson(apiKeyRequest);

        try {
            logger.debug("API key pair about to be generated for device with serial id {}", requestBody);

            String responseBody = executeWithToken(url, HttpMethod.POST, requestBody, accessToken);
            DiagralApiKeyResponse response = gson.fromJson(responseBody, DiagralApiKeyResponse.class);

            String apiKey = (response != null) ? response.apiKey : null;
            String secretKey = (response != null) ? response.secretKey : null;

            if (apiKey == null || secretKey == null) {
                throw new DiagralAuthenticationException("API key generation failed: Invalid response");
            }

            authManager.setApiKeys(apiKey, secretKey);
            logger.debug("API key pair generated successfully");
        } catch (JsonSyntaxException e) {
            throw new DiagralAuthenticationException("Invalid JSON response from API key generation", e);
        } catch (DiagralException e) {
            throw new DiagralAuthenticationException("API key generation request failed", e);
        }
    }

    /**
     * Gets the current system status
     *
     * @return the system status
     * @throws DiagralException if the request fails
     */
    public DiagralSystemStatus getSystemStatus() throws DiagralException {
        String endpoint = API_ENDPOINT_SYSTEMS + "/" + authManager.getSerialId() + API_ENDPOINT_STATUS;
        String responseBody = executeGet(endpoint, true);

        try {
            DiagralSystemStatus status = gson.fromJson(responseBody, DiagralSystemStatus.class);
            if (status == null) {
                throw new DiagralApiException("Invalid system status response", HttpStatus.OK_200);
            }
            return status;
        } catch (JsonSyntaxException e) {
            throw new DiagralApiException("Failed to parse system status response", HttpStatus.OK_200, e);
        }
    }

    /**
     * Gets the complete system configuration
     *
     * @return the system configuration
     * @throws DiagralException if the request fails
     */
    public DiagralSystemConfiguration getSystemConfiguration() throws DiagralException {
        String endpoint = API_ENDPOINT_SYSTEMS + "/" + authManager.getSerialId() + API_ENDPOINT_CONFIGURATIONS;
        String responseBody = executeGet(endpoint, false);

        try {
            DiagralSystemConfiguration config = gson.fromJson(responseBody, DiagralSystemConfiguration.class);
            if (config == null) {
                throw new DiagralApiException("Invalid system configuration response", HttpStatus.OK_200);
            }
            return config;
        } catch (JsonSyntaxException e) {
            throw new DiagralApiException("Failed to parse system configuration response", HttpStatus.OK_200, e);
        }
    }

    /**
     * Gets the complete system details
     *
     * @return the system details
     * @throws DiagralException if the request fails
     */
    public DiagralSystemDetails getSystemDetails() throws DiagralException {
        String endpoint = API_ENDPOINT_SYSTEMS + "/" + authManager.getSerialId();
        String responseBody = executeGet(endpoint, true);

        try {
            DiagralSystemDetails details = gson.fromJson(responseBody, DiagralSystemDetails.class);
            if (details == null) {
                throw new DiagralApiException("Invalid system details response", HttpStatus.OK_200);
            }
            return details;
        } catch (JsonSyntaxException e) {
            throw new DiagralApiException("Failed to parse system details response", HttpStatus.OK_200, e);
        }
    }

    /**
     * Gets the anomalies currently reported for the system.
     *
     * @return the anomalies, or an empty {@link DiagralAnomalies} if none are reported
     * @throws DiagralException if the request fails for a reason other than "no anomalies found"
     */
    public DiagralAnomalies getAnomalies() throws DiagralException {
        String endpoint = API_ENDPOINT_SYSTEMS + "/" + authManager.getSerialId() + API_ENDPOINT_ANOMALIES;
        String responseBody;
        try {
            responseBody = executeGet(endpoint, false);
        } catch (DiagralApiException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND_404) {
                logger.debug("No anomalies found for the system");
                return new DiagralAnomalies();
            }
            throw e;
        }

        try {
            DiagralAnomalies anomalies = gson.fromJson(responseBody, DiagralAnomalies.class);
            return anomalies != null ? anomalies : new DiagralAnomalies();
        } catch (JsonSyntaxException e) {
            throw new DiagralApiException("Failed to parse anomalies response", HttpStatus.OK_200, e);
        }
    }

    /**
     * Sets the system mode
     *
     * @param mode the mode to set (OFF, FULL, PRESENCE, PARTIAL1, PARTIAL2)
     * @throws DiagralException if the request fails
     */
    public void setSystemMode(String mode) throws DiagralException {
        String endpoint;
        switch (mode) {
            case MODE_OFF:
                endpoint = API_ENDPOINT_SYSTEMS + "/" + authManager.getSerialId() + API_ENDPOINT_STOP;
                break;
            case MODE_FULL:
                endpoint = API_ENDPOINT_SYSTEMS + "/" + authManager.getSerialId() + API_ENDPOINT_START;
                break;
            case MODE_PRESENCE:
                endpoint = API_ENDPOINT_SYSTEMS + "/" + authManager.getSerialId() + API_ENDPOINT_PRESENCE;
                break;
            case MODE_PARTIAL1:
                endpoint = API_ENDPOINT_SYSTEMS + "/" + authManager.getSerialId() + API_ENDPOINT_PARTIAL_START_1;
                break;
            case MODE_PARTIAL2:
                endpoint = API_ENDPOINT_SYSTEMS + "/" + authManager.getSerialId() + API_ENDPOINT_PARTIAL_START_2;
                break;
            default:
                throw new DiagralApiException("Invalid system mode: " + mode, 0);
        }

        executePost(endpoint, "", true);
        logger.debug("System mode set to: {}", mode);
    }

    /**
     * Activates a device group
     *
     * @param groupId the group ID to activate
     * @throws DiagralException if the request fails
     */
    public void activateGroup(String groupId) throws DiagralException {
        String endpoint = API_ENDPOINT_SYSTEMS + "/" + authManager.getSerialId() + API_ENDPOINT_ACTIVATE_GROUP;
        String payload = "{\"group_id\":\"" + groupId + "\"}";
        executePost(endpoint, payload, true);
        logger.debug("Group {} activated", groupId);
    }

    /**
     * Disables a device group
     *
     * @param groupId the group ID to disable
     * @throws DiagralException if the request fails
     */
    public void disableGroup(String groupId) throws DiagralException {
        String endpoint = API_ENDPOINT_SYSTEMS + "/" + authManager.getSerialId() + API_ENDPOINT_DISABLE_GROUP;
        String payload = "{\"group_id\":\"" + groupId + "\"}";
        executePost(endpoint, payload, true);
        logger.debug("Group {} disabled", groupId);
    }

    /**
     * Enables (un-inhibits) a device.
     *
     * @param type the product type (see {@link #VALID_PRODUCT_TYPES})
     * @param productId the per-category numeric device index
     * @throws DiagralException if the request fails
     */
    public void enableProduct(String type, int productId) throws DiagralException {
        actionProduct(API_ENDPOINT_ENABLE, type, productId);
    }

    /**
     * Disables (inhibits) a device.
     *
     * @param type the product type (see {@link #VALID_PRODUCT_TYPES})
     * @param productId the per-category numeric device index
     * @throws DiagralException if the request fails
     */
    public void disableProduct(String type, int productId) throws DiagralException {
        actionProduct(API_ENDPOINT_DISABLE, type, productId);
    }

    /**
     * Performs an enable/disable action on a device.
     *
     * @param actionEndpoint the action endpoint suffix ({@link #API_ENDPOINT_ENABLE} or
     *            {@link #API_ENDPOINT_DISABLE})
     * @param type the product type
     * @param productId the per-category numeric device index
     * @throws DiagralException if the request fails and the resulting device state could not be
     *             verified as matching the intended outcome (see {@link #wasProductActionActuallyApplied})
     */
    private void actionProduct(String actionEndpoint, String type, int productId) throws DiagralException {
        if (!VALID_PRODUCT_TYPES.contains(type)) {
            throw new DiagralApiException("Invalid product type: " + type, 0);
        }

        String endpoint = API_ENDPOINT_SYSTEMS + "/" + authManager.getSerialId() + "/" + type + "/" + productId
                + actionEndpoint;
        boolean targetEnabled = API_ENDPOINT_ENABLE.equals(actionEndpoint);
        try {
            executePost(endpoint, "", true);
        } catch (DiagralApiException e) {
            // Known Diagral cloud API quirk (see README "Known Limitations"): this endpoint has been
            // observed to always respond with an empty-bodied HTTP 500 even when it *did* apply the
            // enable/disable action server-side. Rather than surface a misleading failure to the user
            // when the command actually worked, verify the device's real "inhibited" state via the
            // /anomalies endpoint and treat a confirmed match with the intended outcome as success.
            // A genuine failure (mismatch, or verification itself failing) still propagates normally.
            if (e.getStatusCode() == HttpStatus.INTERNAL_SERVER_ERROR_500
                    && wasProductActionActuallyApplied(type, productId, targetEnabled)) {
                logger.warn(
                        "Product {} ({}) action {} returned HTTP 500 but the resulting device state was verified "
                                + "as applied - treating as a known Diagral API quirk, not a failure",
                        productId, type, actionEndpoint);
                return;
            }
            throw e;
        }
        logger.debug("Product {} ({}) action {} completed", productId, type, actionEndpoint);
    }

    /**
     * Checks whether an enable/disable action was actually applied server-side, despite an error
     * response, by inspecting the device's live "inhibited" anomaly state.
     *
     * @param type the product type
     * @param productId the per-category numeric device index
     * @param targetEnabled true if the action was meant to enable (un-inhibit) the device
     * @return true if the device's current inhibited state matches the intended outcome
     */
    private boolean wasProductActionActuallyApplied(String type, int productId, boolean targetEnabled) {
        try {
            DiagralAnomalies anomalies = getAnomalies();
            Boolean inhibited = isDeviceInhibited(anomalies, type, productId);
            return inhibited != null && inhibited != targetEnabled;
        } catch (DiagralException e) {
            logger.debug("Could not verify device {} ({}) state after HTTP 500: {}", productId, type, e.getMessage());
            return false;
        }
    }

    /**
     * Determines whether a device currently reports an "inhibited" anomaly.
     *
     * <p>
     * A null category list from the API means "nothing in that category currently has an anomaly" (the
     * device is confirmed not inhibited) - this is different from a product type that has no matching
     * anomaly category at all (e.g. {@link DiagralBindingConstants#PRODUCT_TYPE_BOX}), which can't be
     * verified this way regardless of what the API returns.
     * </p>
     *
     * @param anomalies the current anomalies
     * @param type the product type, used to select the matching device category
     * @param productId the per-category numeric device index
     * @return true/false if determined, or null if this product type has no matching anomaly category
     */
    private static @Nullable Boolean isDeviceInhibited(DiagralAnomalies anomalies, String type, int productId) {
        List<DiagralAnomalyDetail> details;
        switch (type) {
            case PRODUCT_TYPE_SENSOR:
                details = anomalies.sensors;
                break;
            case PRODUCT_TYPE_ALARM:
                details = anomalies.sirens;
                break;
            case PRODUCT_TYPE_COMMAND:
                details = anomalies.commands;
                break;
            case PRODUCT_TYPE_PLUG:
                details = anomalies.transmitters;
                break;
            case PRODUCT_TYPE_CENTRAL:
                details = anomalies.central;
                break;
            default:
                // Product type has no matching anomaly category - can't verify regardless of API response
                return null;
        }
        if (details == null) {
            // No anomalies at all in this category right now, so this device isn't inhibited
            return false;
        }

        for (DiagralAnomalyDetail detail : details) {
            Integer deviceIndex = detail.deviceIndex;
            if (deviceIndex != null && deviceIndex == productId) {
                return hasInhibitedAnomaly(detail);
            }
        }
        return false;
    }

    /**
     * Checks whether a device's anomaly details include the "inhibited" anomaly name.
     *
     * @param detail the device's anomaly details
     * @return true if the device is currently reported as inhibited
     */
    private static boolean hasInhibitedAnomaly(DiagralAnomalyDetail detail) {
        List<DiagralAnomalyName> names = detail.anomalyNames;
        if (names == null) {
            return false;
        }
        for (DiagralAnomalyName name : names) {
            if (DEVICE_ANOMALY_NAME_INHIBITED.equals(name.name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Executes a signed GET request
     *
     * @param endpoint the API endpoint (without base URL)
     * @return the response body as string
     * @throws DiagralException if the request fails
     */
    private String executeGet(String endpoint, boolean pinCode) throws DiagralException {
        return executeRequest(API_BASE_URL + endpoint, HttpMethod.GET, "", pinCode);
    }

    /**
     * Executes a signed POST request
     *
     * @param endpoint the API endpoint (without base URL)
     * @param payload the request payload
     * @return the response body as string
     * @throws DiagralException if the request fails
     */
    private String executePost(String endpoint, String payload, boolean pinCode) throws DiagralException {
        return executeRequest(API_BASE_URL + endpoint, HttpMethod.POST, payload, pinCode);
    }

    /**
     * Executes a signed HTTP request
     *
     * @param url the full URL
     * @param method the HTTP method
     * @param payload the request payload (empty string for GET)
     * @return the response body as string
     * @throws DiagralException if the request fails
     */
    private String executeRequest(String url, HttpMethod method, String payload, boolean pinCode)
            throws DiagralException {
        if (!authManager.isAuthenticated()) {
            throw new DiagralAuthenticationException("Not authenticated - please authenticate first");
        }

        long timestamp = System.currentTimeMillis() / 1000;
        String hmac = authManager.generateSignature(timestamp).toLowerCase();
        String apiKey = authManager.getApiKey();

        if (apiKey == null) {
            throw new DiagralAuthenticationException("API key not available");
        }

        Request request = httpClient.newRequest(url);
        request.method(method);
        request.header(HEADER_X_HMAC, hmac);
        request.header(HEADER_X_TIMESTAMP, String.valueOf(timestamp));
        request.header(HEADER_X_APIKEY, apiKey);
        if (pinCode) {
            request.header(HEADER_X_PIN_CODE, authManager.getPinCode());
        }
        request.header(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON);
        request.timeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        if (method == HttpMethod.POST && !payload.isEmpty()) {
            request.content(new StringContentProvider(payload, StandardCharsets.UTF_8));
        }
        return executeHttpRequest(request);
    }

    /**
     * Executes an unauthenticated POST request (used for login)
     *
     * @param url the full URL
     * @param payload the request payload
     * @return the response body as string
     * @throws DiagralException if the request fails
     */
    private String executeUnauthenticatedPost(String url, String payload) throws DiagralException {
        Request request = httpClient.newRequest(url);
        request.method(HttpMethod.POST);
        request.header(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON);
        request.timeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        request.content(new StringContentProvider(payload, StandardCharsets.UTF_8));

        return executeHttpRequest(request);
    }

    /**
     * Executes a request with an access token (used for API key generation)
     *
     * @param url the full URL
     * @param method the HTTP method
     * @param payload the request payload
     * @param accessToken the access token
     * @return the response body as string
     * @throws DiagralException if the request fails
     */
    private String executeWithToken(String url, HttpMethod method, String payload, String accessToken)
            throws DiagralException {
        Request request = httpClient.newRequest(url);
        request.method(method);
        request.header(HEADER_AUTHORIZATION, "Bearer " + accessToken);
        request.header(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON);
        request.timeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        if (method == HttpMethod.POST && !payload.isEmpty()) {
            request.content(new StringContentProvider(payload, StandardCharsets.UTF_8));
        }

        return executeHttpRequest(request);
    }

    /**
     * Executes the HTTP request and handles the response
     *
     * @param request the Jetty request object
     * @return the response body as string
     * @throws DiagralException if the request fails
     */
    private String executeHttpRequest(Request request) throws DiagralException {
        try {
            logger.debug("HTTP request about to be sent: {}", request);

            ContentResponse response = request.send();
            int statusCode = response.getStatus();
            String responseBody = response.getContentAsString();
            logger.debug("HTTP response received: {}", responseBody);

            if (statusCode >= 200 && statusCode < 300) {
                return responseBody;
            } else if (statusCode == HttpStatus.BAD_REQUEST_400 || statusCode == HttpStatus.UNAUTHORIZED_401
                    || statusCode == HttpStatus.FORBIDDEN_403) {
                logger.warn("Authentication failed with status {}, clearing API keys", statusCode);
                authManager.clearApiKeys();
                throw new DiagralAuthenticationException("Authentication failed with status: " + statusCode);
            } else if (statusCode == HttpStatus.NOT_FOUND_404) {
                throw new DiagralApiException("Resource not found", statusCode);
            } else if (statusCode == HttpStatus.TOO_MANY_REQUESTS_429) {
                throw new DiagralApiException("Rate limit exceeded", statusCode);
            } else if (statusCode >= 500) {
                throw new DiagralApiException("Server error: " + statusCode, statusCode);
            } else {
                throw new DiagralApiException("Request failed with status: " + statusCode, statusCode);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DiagralException("Request interrupted", e);
        } catch (TimeoutException e) {
            throw new DiagralException("Request timeout", e);
        } catch (ExecutionException e) {
            throw new DiagralException("Request execution failed", e);
        }
    }
}
