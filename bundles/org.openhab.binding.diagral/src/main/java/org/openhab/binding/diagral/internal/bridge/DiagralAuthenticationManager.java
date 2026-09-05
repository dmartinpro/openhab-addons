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

import java.util.Locale;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.diagral.internal.dto.DiagralLoginRequest;
import org.openhab.binding.diagral.internal.exception.DiagralAuthenticationException;
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
     * The API key this manager most recently held before {@link #clearApiKeys()} retired it, kept so it
     * can still be deleted server-side even though it is no longer usable for signing.
     *
     * <p>
     * Exists because the Diagral cloud API mints a brand-new key pair on every {@code
     * DiagralHttpClient.authenticate()} and never expires the old one: without this, each
     * re-authentication (which the 401/403 path triggers by clearing the keys first) would silently leave
     * another live API key registered against the user's account forever. Drained exactly once by {@link
     * #takeSupersededApiKey()}, which {@code DiagralHttpClient.authenticate()} calls to delete it before
     * generating the replacement.
     * </p>
     */
    private @Nullable String supersededApiKey;

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
     * Builds the login request body for this account's credentials.
     *
     * <p>
     * Deliberately returns a ready-made {@link DiagralLoginRequest} rather than exposing a {@code
     * getPassword()} accessor, so the account password never has to leave this class - it goes straight
     * from here into the JSON body {@code DiagralHttpClient.login()} sends.
     * </p>
     *
     * @return a login request carrying this account's username and password
     */
    public DiagralLoginRequest createLoginRequest() {
        return new DiagralLoginRequest(username, password);
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
     * Clears the stored API keys (used when re-authentication is needed).
     *
     * <p>
     * Also remembers whatever API key was active in {@link #supersededApiKey}, so the next
     * authentication can delete it server-side rather than orphaning it - see that field's Javadoc.
     * </p>
     */
    public synchronized void clearApiKeys() {
        String current = apiKey;
        if (current != null) {
            supersededApiKey = current;
        }
        this.apiKey = null;
        this.secretKey = null;
        this.authenticated = false;
        logger.debug("API keys cleared");
    }

    /**
     * Returns - exactly once - the API key retired by the last {@link #clearApiKeys()} call, so the
     * caller can delete it from the Diagral account before minting a replacement.
     *
     * <p>
     * Draining rather than merely reading it means a failed deletion attempt isn't retried forever on
     * every subsequent authentication; the key is simply given up on, which is no worse than the
     * pre-existing behaviour of never attempting it at all.
     * </p>
     *
     * @return the superseded API key, or {@code null} if there is none pending
     */
    public synchronized @Nullable String takeSupersededApiKey() {
        String superseded = supersededApiKey;
        supersededApiKey = null;
        return superseded;
    }

    /**
     * Produces everything one signed Diagral API request needs - API key, timestamp, and matching HMAC -
     * in a single atomic step.
     *
     * <p>
     * Deliberately does all of it under one lock. The previous shape (a separate {@code isAuthenticated()}
     * check, {@code generateSignature()} call, and {@code getApiKey()} read from
     * {@code DiagralHttpClient.executeRequest()}) took the lock three times, so a concurrent {@link
     * #clearApiKeys()} - which the 401/403 path triggers from whichever thread hit it - could land between
     * them and produce a request whose {@code X-HMAC} was computed with a key pair that no longer matched
     * the {@code X-APIKEY} header it was sent with. Returning one immutable {@link SignedRequest} makes
     * that interleaving impossible.
     * </p>
     *
     * @return the API key, timestamp, and signature to send with a single request
     * @throws DiagralAuthenticationException if this manager isn't currently holding a valid key pair
     * @throws DiagralException if the HMAC calculation itself fails
     */
    public synchronized SignedRequest signRequest() throws DiagralException {
        String currentApiKey = apiKey;
        String currentSecretKey = secretKey;

        if (!authenticated || currentApiKey == null || currentSecretKey == null) {
            throw new DiagralAuthenticationException("Not authenticated - please authenticate first");
        }

        long timestamp = System.currentTimeMillis() / 1000;
        // Data to sign: "timestamp.serialId.apiKey"
        String dataToSign = timestamp + "." + serialId + "." + currentApiKey;
        String hmac = DiagralCryptoUtil.hmacSha256(dataToSign, currentSecretKey).toLowerCase(Locale.ROOT);

        return new SignedRequest(currentApiKey, timestamp, hmac);
    }

    /**
     * An immutable, self-consistent set of credentials for exactly one signed Diagral API request, as
     * produced by {@link #signRequest()}.
     *
     * <p>
     * The three values must travel together: the signature is computed over the timestamp and API key it
     * is returned with, so mixing one request's HMAC with another's key or timestamp yields a request the
     * API rejects. Bundling them in a record is what lets {@link #signRequest()} hand them out under a
     * single lock.
     * </p>
     *
     * @param apiKey the API key to send as the {@code X-APIKEY} header
     * @param timestamp the Unix timestamp (seconds) the signature was computed over, sent as {@code
     *            X-TIMESTAMP}
     * @param hmac the lowercase-hex HMAC-SHA256 signature to send as the {@code X-HMAC} header
     */
    public record SignedRequest(String apiKey, long timestamp, String hmac) {
    }
}
