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

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.diagral.internal.exception.DiagralException;
import org.openhab.binding.diagral.internal.util.DiagralCryptoUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link DiagralAuthenticationManager} manages authentication credentials and API keys for the Diagral API.
 *
 * <p>
 * Holds the account credentials (username/password/serialId/pinCode) for the lifetime of one
 * {@code DiagralBridgeHandler}, plus whatever {@code apiKey}/{@code secretKey} pair is currently valid
 * (obtained via {@code DiagralHttpClient.authenticate()} and stored with {@link #setApiKeys}). The
 * apiKey/secretKey fields are mutable and access to them is synchronized because they're read from the
 * scheduler thread that issues HTTP requests but written from whichever thread runs the authentication
 * flow - both happen concurrently once the bridge is polling.
 * </p>
 *
 * @author David Martin - Initial contribution
 */
@NonNullByDefault
public class DiagralAuthenticationManager {

    private final Logger logger = LoggerFactory.getLogger(DiagralAuthenticationManager.class);

    private final String username;
    private final String password;
    private final String serialId;
    private final String pinCode;

    private @Nullable String apiKey;
    private @Nullable String secretKey;
    private boolean authenticated = false;

    /**
     * Constructs a new authentication manager for one Diagral account/box.
     *
     * @param username the Diagral account's email address
     * @param password the Diagral account's password
     * @param serialId the serial ID of the Diagral box to control
     * @param pinCode the PIN code used to authorize system-control (arm/disarm, enable/disable) requests
     */
    public DiagralAuthenticationManager(String username, String password, String serialId, String pinCode) {
        this.username = username;
        this.password = password;
        this.serialId = serialId;
        this.pinCode = pinCode;
    }

    /**
     * Gets the Diagral account's username.
     *
     * @return the username (email address) supplied at construction
     */
    public String getUsername() {
        return username;
    }

    /**
     * Gets the Diagral account's password.
     *
     * @return the password supplied at construction
     */
    public String getPassword() {
        return password;
    }

    /**
     * Gets the serial ID of the Diagral box this manager authenticates against.
     *
     * @return the serial ID supplied at construction
     */
    public String getSerialId() {
        return serialId;
    }

    /**
     * Gets the PIN code used to authorize system-control requests.
     *
     * @return the PIN code supplied at construction
     */
    public String getPinCode() {
        return pinCode;
    }

    /**
     * Gets the currently stored API key, if any.
     *
     * @return the API key, or null if not currently authenticated
     */
    public synchronized @Nullable String getApiKey() {
        return apiKey;
    }

    /**
     * Gets the currently stored secret key, if any.
     *
     * @return the secret key, or null if not currently authenticated
     */
    public synchronized @Nullable String getSecretKey() {
        return secretKey;
    }

    /**
     * Checks whether this manager currently holds a valid API key/secret key pair.
     *
     * @return {@code true} if authenticated and both keys are present
     */
    public synchronized boolean isAuthenticated() {
        return authenticated && apiKey != null && secretKey != null;
    }

    /**
     * Stores the API key and secret key after successful authentication
     *
     * @param apiKey the API key
     * @param secretKey the secret key
     */
    public synchronized void setApiKeys(String apiKey, String secretKey) {
        this.apiKey = apiKey;
        this.secretKey = secretKey;
        this.authenticated = true;
        logger.debug("API keys successfully stored");
    }

    /**
     * Clears the stored API keys (used when re-authentication is needed)
     */
    public synchronized void clearApiKeys() {
        this.apiKey = null;
        this.secretKey = null;
        this.authenticated = false;
        logger.debug("API keys cleared");
    }

    /**
     * Generates the HMAC-SHA256 signature for a Diagral API request
     *
     * @param timestamp the current Unix timestamp in seconds
     * @return the HMAC signature as uppercase hexadecimal string
     * @throws DiagralException if signature generation fails or keys are not available
     */
    public synchronized String generateSignature(long timestamp) throws DiagralException {
        String currentApiKey = apiKey;
        String currentSecretKey = secretKey;

        if (currentApiKey == null || currentSecretKey == null) {
            throw new DiagralException("Cannot generate signature: API keys not available");
        }

        // Data to sign: "timestamp.serialId.apiKey"
        String dataToSign = timestamp + "." + serialId + "." + currentApiKey;

        return DiagralCryptoUtil.hmacSha256(dataToSign, currentSecretKey);
    }
}
