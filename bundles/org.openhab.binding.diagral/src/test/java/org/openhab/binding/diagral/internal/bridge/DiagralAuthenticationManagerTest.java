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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Locale;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openhab.binding.diagral.internal.bridge.DiagralAuthenticationManager.SignedRequest;
import org.openhab.binding.diagral.internal.dto.DiagralLoginRequest;
import org.openhab.binding.diagral.internal.exception.DiagralAuthenticationException;
import org.openhab.binding.diagral.internal.exception.DiagralException;
import org.openhab.binding.diagral.internal.util.DiagralCryptoUtil;

/**
 * Unit tests for {@link DiagralAuthenticationManager}, covering the credential handling that the
 * security review of 2026-09-04 changed: the superseded-API-key bookkeeping that stops every
 * re-authentication from orphaning a live key server-side (S2), and the atomic
 * {@link DiagralAuthenticationManager#signRequest()} that replaced the previous three-lock
 * check/sign/read sequence (S5).
 *
 * @author David Martin - Initial contribution
 */
@NonNullByDefault
public class DiagralAuthenticationManagerTest {

    private static final String USERNAME = "user@example.test";
    private static final String PASSWORD = "s3cr3t-password";
    private static final String SERIAL_ID = "DIAG56AAX000123";
    private static final String PIN_CODE = "1234";

    private @NonNullByDefault({}) DiagralAuthenticationManager manager;

    /**
     * Creates a fresh, unauthenticated manager before each test.
     */
    @BeforeEach
    public void setUp() {
        manager = new DiagralAuthenticationManager(USERNAME, PASSWORD, SERIAL_ID, PIN_CODE);
    }

    /**
     * A newly constructed manager holds no key pair and reports itself unauthenticated.
     */
    @Test
    public void newManagerIsNotAuthenticated() {
        assertThat(manager.isAuthenticated(), is(false));
        assertThat(manager.getApiKey(), is(nullValue()));
    }

    /**
     * The login request is built from the credentials supplied at construction, so callers never need a
     * password accessor (S4).
     */
    @Test
    public void createLoginRequestCarriesTheConstructedCredentials() {
        DiagralLoginRequest request = manager.createLoginRequest();

        assertThat(request.username, is(USERNAME));
        assertThat(request.password, is(PASSWORD));
    }

    /**
     * S5: signing produces an API key, timestamp, and HMAC that belong together - the signature must be
     * exactly the HMAC-SHA256 of "timestamp.serialId.apiKey" under the secret key, in lowercase hex.
     */
    @Test
    public void signRequestProducesASelfConsistentTriple() throws DiagralException {
        manager.setApiKeys("api-key-1", "secret-key-1");

        SignedRequest signed = manager.signRequest();

        assertThat(signed.apiKey(), is("api-key-1"));
        String expected = DiagralCryptoUtil
                .hmacSha256(signed.timestamp() + "." + SERIAL_ID + "." + "api-key-1", "secret-key-1")
                .toLowerCase(Locale.ROOT);
        assertThat(signed.hmac(), is(expected));
        assertThat(signed.hmac(), is(equalTo(signed.hmac().toLowerCase(Locale.ROOT))));
    }

    /**
     * S5: signing is refused outright when no key pair is held, rather than emitting a request that the
     * API would reject.
     */
    @Test
    public void signRequestFailsWhenNotAuthenticated() {
        assertThrows(DiagralAuthenticationException.class, () -> manager.signRequest());
    }

    /**
     * S5: once the keys are cleared - which is what a 401/403 does, potentially from another thread - no
     * further request can be signed with the retired pair.
     */
    @Test
    public void signRequestFailsAfterKeysAreCleared() throws DiagralException {
        manager.setApiKeys("api-key-1", "secret-key-1");
        manager.signRequest(); // succeeds while the pair is held

        manager.clearApiKeys();

        assertThat(manager.isAuthenticated(), is(false));
        assertThrows(DiagralAuthenticationException.class, () -> manager.signRequest());
    }

    /**
     * S5: two signatures over the same key pair must both verify against their own timestamp - i.e. the
     * timestamp travelling with a signature is the one it was computed over.
     */
    @Test
    public void eachSignatureMatchesItsOwnTimestamp() throws DiagralException {
        manager.setApiKeys("api-key-1", "secret-key-1");

        SignedRequest first = manager.signRequest();
        SignedRequest second = manager.signRequest();

        for (SignedRequest signed : new SignedRequest[] { first, second }) {
            String expected = DiagralCryptoUtil
                    .hmacSha256(signed.timestamp() + "." + SERIAL_ID + "." + signed.apiKey(), "secret-key-1")
                    .toLowerCase(Locale.ROOT);
            assertThat(signed.hmac(), is(expected));
        }
    }

    /**
     * S2: clearing the keys must remember the retired key so it can still be deleted from the Diagral
     * account, since the API never expires it on its own.
     */
    @Test
    public void clearingKeysQueuesTheRetiredKeyForDeletion() {
        manager.setApiKeys("api-key-1", "secret-key-1");

        manager.clearApiKeys();

        assertThat(manager.takeSupersededApiKey(), is("api-key-1"));
    }

    /**
     * S2: the superseded key is handed out exactly once, so a failed deletion isn't retried forever on
     * every subsequent authentication.
     */
    @Test
    public void supersededKeyIsDrainedOnFirstTake() {
        manager.setApiKeys("api-key-1", "secret-key-1");
        manager.clearApiKeys();

        assertThat(manager.takeSupersededApiKey(), is("api-key-1"));
        assertThat(manager.takeSupersededApiKey(), is(nullValue()));
    }

    /**
     * S2: clearing an already-empty manager queues nothing - there is no key to orphan.
     */
    @Test
    public void clearingWithoutAKeyQueuesNothing() {
        manager.clearApiKeys();

        assertThat(manager.takeSupersededApiKey(), is(nullValue()));
    }

    /**
     * S2: across a full retire-then-replace cycle, exactly the outgoing key is queued for deletion and
     * the incoming one stays active - the invariant that stops keys accumulating on the account.
     */
    @Test
    public void replacingAKeyQueuesOnlyTheOutgoingOne() throws DiagralException {
        manager.setApiKeys("api-key-1", "secret-key-1");

        // What DiagralHttpClient.authenticate() now does: retire, then mint a replacement.
        manager.clearApiKeys();
        String superseded = manager.takeSupersededApiKey();
        manager.setApiKeys("api-key-2", "secret-key-2");

        assertThat(superseded, is("api-key-1"));
        assertThat(manager.getApiKey(), is("api-key-2"));
        assertThat(manager.isAuthenticated(), is(true));
        assertThat(manager.signRequest().apiKey(), is("api-key-2"));
        assertThat(manager.takeSupersededApiKey(), is(nullValue()));
    }
}
