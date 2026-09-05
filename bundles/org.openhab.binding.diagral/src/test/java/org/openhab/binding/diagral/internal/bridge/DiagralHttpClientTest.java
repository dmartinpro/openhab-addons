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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpStatus;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.openhab.binding.diagral.internal.exception.DiagralApiException;
import org.openhab.binding.diagral.internal.exception.DiagralAuthenticationException;
import org.openhab.binding.diagral.internal.exception.DiagralException;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

/**
 * Unit tests for {@link DiagralHttpClient}, covering the request/response handling that the security
 * review of 2026-09-04 changed: the HTTP status-code classification that no longer treats a 400 as an
 * expired credential (S3), and the API-key lifecycle that deletes a superseded key instead of orphaning
 * it (S2). Jetty's {@code HttpClient}/{@code Request}/{@code ContentResponse} are mocked, so no network
 * access is involved.
 *
 * @author David Martin - Initial contribution
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@NonNullByDefault
public class DiagralHttpClientTest {

    private @Mock @NonNullByDefault({}) HttpClient httpClient;

    private @NonNullByDefault({}) DiagralAuthenticationManager authManager;
    private @NonNullByDefault({}) DiagralHttpClient client;

    /** Every request the mocked Jetty client was asked to build, in order. */
    private final List<RecordedRequest> requests = new ArrayList<>();

    /** Response bodies/statuses to hand back, one per request, in order. */
    private final List<int[]> statuses = new ArrayList<>();
    private final List<String> bodies = new ArrayList<>();

    /** Captures everything {@link DiagralHttpClient} logs, so S1's redaction can be asserted directly. */
    private final ListAppender<ILoggingEvent> logAppender = new ListAppender<>();
    private @NonNullByDefault({}) Logger clientLogger;

    /**
     * One request the client built, captured so tests can assert on its method and URL.
     *
     * @param method the HTTP method set on the request
     * @param url the full URL the request was created for
     */
    private record RecordedRequest(String method, String url) {
    }

    /**
     * Wires a mocked Jetty {@link HttpClient} that records each request and replays a queued
     * status/body pair for it.
     */
    @BeforeEach
    public void setUp() throws Exception {
        // TRACE, deliberately: the point of S1 is that even the most verbose level a user can turn on
        // must not disclose credentials, so the tests below assert against a fully-open logger.
        clientLogger = (Logger) LoggerFactory.getLogger(DiagralHttpClient.class);
        clientLogger.setLevel(Level.TRACE);
        logAppender.start();
        clientLogger.addAppender(logAppender);

        authManager = new DiagralAuthenticationManager("user@example.test", "pw", "SERIAL1", "1234");
        client = new DiagralHttpClient(httpClient, authManager);

        when(httpClient.newRequest(anyString())).thenAnswer(invocation -> {
            String url = invocation.getArgument(0);
            Request request = mock(Request.class);
            int index = requests.size();
            requests.add(new RecordedRequest("?", url));

            when(request.method(any(HttpMethod.class))).thenAnswer(methodCall -> {
                requests.set(index, new RecordedRequest(methodCall.getArgument(0).toString(), url));
                return request;
            });
            when(request.header(anyString(), any())).thenReturn(request);
            when(request.timeout(anyLong(), any())).thenReturn(request);
            when(request.content(any())).thenReturn(request);
            when(request.getMethod()).thenAnswer(m -> requests.get(index).method());
            when(request.getURI()).thenReturn(URI.create(url));

            ContentResponse response = mock(ContentResponse.class);
            when(response.getStatus()).thenAnswer(s -> statuses.get(index)[0]);
            when(response.getContentAsString()).thenAnswer(b -> bodies.get(index));
            when(request.send()).thenReturn(response);
            return request;
        });
    }

    /**
     * Detaches the log appender so captured events don't leak between tests.
     */
    @AfterEach
    public void tearDown() {
        clientLogger.detachAppender(logAppender);
        logAppender.stop();
    }

    /**
     * Renders everything logged so far as one searchable string, with each event's placeholders already
     * substituted - i.e. exactly what would land in {@code openhab.log}.
     *
     * @return the concatenated formatted log output
     */
    private String capturedLog() {
        return logAppender.list.stream().map(ILoggingEvent::getFormattedMessage).collect(Collectors.joining("\n"));
    }

    /**
     * Queues the status/body the next built request should receive.
     *
     * @param status the HTTP status code to return
     * @param body the response body to return
     */
    private void enqueue(int status, String body) {
        statuses.add(new int[] { status });
        bodies.add(body);
    }

    /**
     * S3: a 400 on a signed request is an API error, not an authentication failure - it must not discard
     * a working key pair, which is what previously drove a mint-a-new-key-per-failure loop.
     */
    @Test
    public void badRequestIsAnApiErrorAndKeepsTheApiKey() {
        authManager.setApiKeys("api-key-1", "secret-key-1");
        enqueue(HttpStatus.BAD_REQUEST_400, "{\"detail\":\"validation error\"}");

        DiagralApiException thrown = assertThrows(DiagralApiException.class, () -> client.getSystemStatus());

        assertThat(thrown.getStatusCode(), is(HttpStatus.BAD_REQUEST_400));
        assertThat(thrown, is(not(instanceOf(DiagralAuthenticationException.class))));
        assertThat(authManager.isAuthenticated(), is(true));
        assertThat(authManager.getApiKey(), is("api-key-1"));
        assertThat(authManager.takeSupersededApiKey(), is(nullValue()));
    }

    /**
     * S3: 401 and 403 still are authentication failures, still clear the keys, and still queue the
     * retired key for deletion.
     */
    @Test
    public void unauthorizedAndForbiddenStillClearTheApiKey() {
        for (int status : new int[] { HttpStatus.UNAUTHORIZED_401, HttpStatus.FORBIDDEN_403 }) {
            requests.clear();
            statuses.clear();
            bodies.clear();
            authManager.setApiKeys("api-key-" + status, "secret-key-1");
            enqueue(status, "");

            assertThrows(DiagralAuthenticationException.class, () -> client.getSystemStatus());

            assertThat("status " + status, authManager.isAuthenticated(), is(false));
            assertThat("status " + status, authManager.takeSupersededApiKey(), is("api-key-" + status));
        }
    }

    /**
     * S3: a 400 returned during login is still surfaced as an authentication failure, because
     * {@code login()} re-wraps any transport-level failure - so removing 400 from the shared
     * authentication branch did not weaken bad-credential reporting.
     */
    @Test
    public void badRequestDuringLoginIsStillAnAuthenticationFailure() {
        enqueue(HttpStatus.BAD_REQUEST_400, "{\"detail\":\"bad credentials\"}");

        assertThrows(DiagralAuthenticationException.class, () -> client.authenticate());
    }

    /**
     * The other status-code branches are unchanged by the review: 404 and 429 keep their own messages,
     * and 5xx stays a generic API error so {@code actionProduct()}'s HTTP-500 workaround still triggers.
     */
    @Test
    public void otherStatusCodesKeepTheirExistingClassification() {
        authManager.setApiKeys("api-key-1", "secret-key-1");

        enqueue(HttpStatus.NOT_FOUND_404, "");
        assertThat(assertThrows(DiagralApiException.class, () -> client.getSystemStatus()).getStatusCode(),
                is(HttpStatus.NOT_FOUND_404));

        enqueue(HttpStatus.TOO_MANY_REQUESTS_429, "");
        assertThat(assertThrows(DiagralApiException.class, () -> client.getSystemStatus()).getStatusCode(),
                is(HttpStatus.TOO_MANY_REQUESTS_429));

        enqueue(HttpStatus.INTERNAL_SERVER_ERROR_500, "");
        assertThat(assertThrows(DiagralApiException.class, () -> client.getSystemStatus()).getStatusCode(),
                is(HttpStatus.INTERNAL_SERVER_ERROR_500));

        // None of these are credential failures, so the key pair survives all three.
        assertThat(authManager.isAuthenticated(), is(true));
    }

    /**
     * S2: a first-ever authentication has no key to supersede, so it logs in and generates a key pair
     * without issuing a DELETE.
     */
    @Test
    public void firstAuthenticationDoesNotDeleteAnything() throws DiagralException {
        enqueue(HttpStatus.OK_200, "{\"access_token\":\"token-1\"}");
        enqueue(HttpStatus.OK_200, "{\"api_key\":\"api-key-1\",\"secret_key\":\"secret-key-1\"}");

        client.authenticate();

        assertThat(authManager.getApiKey(), is("api-key-1"));
        assertThat(requests.stream().map(RecordedRequest::method).toList(), not(hasItem("DELETE")));
        assertThat(requests, hasSize(2));
    }

    /**
     * S2: the core fix - re-authenticating while a key is held must DELETE that key before minting its
     * replacement, so keys stop accumulating on the user's Diagral account.
     */
    @Test
    public void reauthenticationDeletesTheSupersededKey() throws DiagralException {
        authManager.setApiKeys("old-api-key", "old-secret-key");

        enqueue(HttpStatus.OK_200, "{\"access_token\":\"token-2\"}");
        enqueue(HttpStatus.NO_CONTENT_204, "");
        enqueue(HttpStatus.OK_200, "{\"api_key\":\"new-api-key\",\"secret_key\":\"new-secret-key\"}");

        client.authenticate();

        RecordedRequest delete = requests.stream().filter(r -> "DELETE".equals(r.method())).findFirst().orElseThrow();
        assertThat(delete.url(), Matchers.endsWith("/users/systems/SERIAL1/api_keys/old-api-key"));
        assertThat(authManager.getApiKey(), is("new-api-key"));
        assertThat(authManager.takeSupersededApiKey(), is(nullValue()));
    }

    /**
     * S2: re-authentication after a 401 also deletes the key that the 401 retired - the path that
     * previously orphaned a key on every credential failure.
     */
    @Test
    public void reauthenticationAfterA401DeletesTheClearedKey() throws DiagralException {
        authManager.setApiKeys("expired-api-key", "expired-secret-key");
        enqueue(HttpStatus.UNAUTHORIZED_401, "");
        assertThrows(DiagralAuthenticationException.class, () -> client.getSystemStatus());

        enqueue(HttpStatus.OK_200, "{\"access_token\":\"token-3\"}");
        enqueue(HttpStatus.NO_CONTENT_204, "");
        enqueue(HttpStatus.OK_200, "{\"api_key\":\"fresh-api-key\",\"secret_key\":\"fresh-secret-key\"}");

        client.authenticate();

        RecordedRequest delete = requests.stream().filter(r -> "DELETE".equals(r.method())).findFirst().orElseThrow();
        assertThat(delete.url(), Matchers.endsWith("/api_keys/expired-api-key"));
        assertThat(authManager.getApiKey(), is("fresh-api-key"));
    }

    /**
     * S2: cleaning up the old key is best-effort - a failed DELETE must not stop the binding from
     * authenticating with its new key pair.
     */
    @Test
    public void failedDeletionDoesNotBlockAuthentication() throws DiagralException {
        authManager.setApiKeys("old-api-key", "old-secret-key");

        enqueue(HttpStatus.OK_200, "{\"access_token\":\"token-4\"}");
        enqueue(HttpStatus.INTERNAL_SERVER_ERROR_500, "");
        enqueue(HttpStatus.OK_200, "{\"api_key\":\"new-api-key\",\"secret_key\":\"new-secret-key\"}");

        client.authenticate();

        assertThat(authManager.getApiKey(), is("new-api-key"));
        assertThat(authManager.isAuthenticated(), is(true));
    }

    /**
     * A signed request carries the HMAC headers produced by the single atomic
     * {@link DiagralAuthenticationManager#signRequest()} call (S5), and the PIN code only where the
     * endpoint requires it.
     */
    @Test
    public void signedRequestsCarryTheHmacHeaders() throws DiagralException {
        authManager.setApiKeys("api-key-1", "secret-key-1");
        enqueue(HttpStatus.OK_200, "{\"status\":\"OFF\"}");

        client.getSystemStatus();

        verify(httpClient).newRequest(ArgumentMatchers.endsWith("/systems/SERIAL1/status"));
        assertThat(requests, hasSize(1));
        assertThat(requests.get(0).method(), is("GET"));
    }

    /**
     * S1: the login response body carries the account access token and must never be logged, at any
     * level - this is the leak that let anyone with DEBUG logging enabled read a live credential out of
     * {@code openhab.log}.
     */
    @Test
    public void loginResponseBodyIsNeverLogged() throws DiagralException {
        enqueue(HttpStatus.OK_200, "{\"access_token\":\"super-secret-access-token\"}");
        enqueue(HttpStatus.OK_200, "{\"api_key\":\"api-key-1\",\"secret_key\":\"secret-key-1\"}");

        client.authenticate();

        assertThat(capturedLog(), not(containsString("super-secret-access-token")));
    }

    /**
     * S1: the API-key response carries both the API key and the secret key it is signed with - the
     * single most damaging value in this binding - so its body must never be logged either.
     */
    @Test
    public void apiKeyResponseBodyIsNeverLogged() throws DiagralException {
        enqueue(HttpStatus.OK_200, "{\"access_token\":\"token-1\"}");
        enqueue(HttpStatus.OK_200, "{\"api_key\":\"super-secret-api-key\",\"secret_key\":\"super-secret-hmac-key\"}");

        client.authenticate();

        String log = capturedLog();
        assertThat(log, not(containsString("super-secret-hmac-key")));
        assertThat(log, not(containsString("super-secret-api-key")));
        // The redaction placeholder is what should appear instead, proving the body was seen and dropped
        // rather than simply never reaching the log for some unrelated reason.
        assertThat(log, containsString("<redacted"));
    }

    /**
     * S1: an ordinary (non-credential) response body is still logged, at TRACE - the redaction must be
     * targeted, not a blanket loss of troubleshooting output.
     */
    @Test
    public void ordinaryResponseBodiesAreStillLoggedAtTrace() throws DiagralException {
        authManager.setApiKeys("api-key-1", "secret-key-1");
        enqueue(HttpStatus.OK_200, "{\"status\":\"PRESENCE\",\"activated_groups\":[2]}");

        client.getSystemStatus();

        assertThat(capturedLog(), containsString("\"status\":\"PRESENCE\""));
    }

    /**
     * S1: an API key that does reach the log (the deletion audit line) is masked down to its last four
     * characters, which correlates log lines without disclosing a usable key.
     */
    @Test
    public void loggedApiKeysAreMasked() throws DiagralException {
        authManager.setApiKeys("abcdefghij0123456789", "old-secret-key");
        enqueue(HttpStatus.OK_200, "{\"access_token\":\"token-5\"}");
        enqueue(HttpStatus.NO_CONTENT_204, "");
        enqueue(HttpStatus.OK_200, "{\"api_key\":\"new-api-key\",\"secret_key\":\"new-secret-key\"}");

        client.authenticate();

        String log = capturedLog();
        assertThat(log, not(containsString("abcdefghij0123456789")));
        assertThat(log, containsString("...6789"));
    }

    /**
     * A signed request is refused locally when no key pair is held, without touching the network.
     */
    @Test
    public void signedRequestWithoutCredentialsNeverReachesTheNetwork() {
        assertThrows(DiagralAuthenticationException.class, () -> client.getSystemStatus());

        assertThat(requests, is(empty()));
    }
}
