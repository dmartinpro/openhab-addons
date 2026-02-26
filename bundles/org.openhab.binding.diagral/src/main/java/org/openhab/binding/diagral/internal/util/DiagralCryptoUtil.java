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
package org.openhab.binding.diagral.internal.util;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.diagral.internal.exception.DiagralException;
import org.openhab.core.util.HexUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link DiagralCryptoUtil} provides cryptographic utilities for the Diagral binding.
 *
 * @author David Martin - Initial contribution
 */
@NonNullByDefault
public class DiagralCryptoUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(DiagralCryptoUtil.class);
    private static final String HMAC_SHA256_ALGORITHM = "HmacSHA256";

    private DiagralCryptoUtil() {
        // prevent instantiation
    }

    /**
     * Generate hmac for Diagral API requests based on this tuple: "timestamp.serialId.apiKey"
     * 
     * @param timestamp
     * @param serialId
     * @param apiKey
     * @param secret
     * @return
     * @throws DiagralException
     */
    public static String hmacSha256(String timestamp, String serialId, String apiKey, String secret)
            throws DiagralException {
        String data = String.format("%s.%s.%s", timestamp, serialId, apiKey);
        return DiagralCryptoUtil.hmacSha256(data, secret);
    }

    /**
     * Generate hmac for Diagral API requests based on this tuple: "timestamp.serialId.apiKey"
     * Timestamp will be generated based on the current time (Instant.now())
     * 
     * @param serialId
     * @param apiKey
     * @param secret
     * @return
     * @throws DiagralException
     */
    public static String hmacSha256(String serialId, String apiKey, String secret) throws DiagralException {
        long timestamp = Instant.now().getEpochSecond();
        return DiagralCryptoUtil.hmacSha256(String.valueOf(timestamp), serialId, apiKey, secret);
    }

    /**
     * Calculate an HMAC-SHA256 signature for Diagral API requests.
     *
     * @param data the data to sign (typically "timestamp.serialId.apiKey")
     * @param secret the secret key to use for signing
     * @return the resulting HMAC-SHA256 as uppercase hexadecimal string
     * @throws DiagralException if the HMAC calculation fails
     */
    public static String hmacSha256(String data, String secret) throws DiagralException {
        try {
            Mac sha256HMAC = Mac.getInstance(HMAC_SHA256_ALGORITHM);
            SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),
                    HMAC_SHA256_ALGORITHM);
            sha256HMAC.init(secretKeySpec);

            byte[] hash = sha256HMAC.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexUtils.bytesToHex(hash).toUpperCase();
        } catch (NoSuchAlgorithmException e) {
            String message = "HMAC-SHA256 algorithm not found. This should never happen. Check your Java setup.";
            LOGGER.error(message, e);
            throw new DiagralException(message, e);
        } catch (InvalidKeyException e) {
            String message = "Invalid secret key for HMAC calculation";
            LOGGER.error(message, e);
            throw new DiagralException(message, e);
        }
    }
}
