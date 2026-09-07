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
package org.openhab.binding.diagral.internal.testsupport;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.openhab.binding.diagral.internal.DiagralBindingConstants.API_ENDPOINT_ACTIVATE_GROUP;
import static org.openhab.binding.diagral.internal.DiagralBindingConstants.API_ENDPOINT_ANOMALIES;
import static org.openhab.binding.diagral.internal.DiagralBindingConstants.API_ENDPOINT_API_KEY;
import static org.openhab.binding.diagral.internal.DiagralBindingConstants.API_ENDPOINT_API_KEYS;
import static org.openhab.binding.diagral.internal.DiagralBindingConstants.API_ENDPOINT_CONFIGURATIONS;
import static org.openhab.binding.diagral.internal.DiagralBindingConstants.API_ENDPOINT_DISABLE;
import static org.openhab.binding.diagral.internal.DiagralBindingConstants.API_ENDPOINT_DISABLE_GROUP;
import static org.openhab.binding.diagral.internal.DiagralBindingConstants.API_ENDPOINT_ENABLE;
import static org.openhab.binding.diagral.internal.DiagralBindingConstants.API_ENDPOINT_LOGIN;
import static org.openhab.binding.diagral.internal.DiagralBindingConstants.API_ENDPOINT_PARTIAL_START_1;
import static org.openhab.binding.diagral.internal.DiagralBindingConstants.API_ENDPOINT_PARTIAL_START_2;
import static org.openhab.binding.diagral.internal.DiagralBindingConstants.API_ENDPOINT_PRESENCE;
import static org.openhab.binding.diagral.internal.DiagralBindingConstants.API_ENDPOINT_START;
import static org.openhab.binding.diagral.internal.DiagralBindingConstants.API_ENDPOINT_STATUS;
import static org.openhab.binding.diagral.internal.DiagralBindingConstants.API_ENDPOINT_STOP;
import static org.openhab.binding.diagral.internal.DiagralBindingConstants.API_ENDPOINT_SYSTEMS;
import static org.openhab.binding.diagral.internal.DiagralBindingConstants.API_ENDPOINT_USER_SYSTEMS;
import static org.openhab.binding.diagral.internal.DiagralBindingConstants.DEVICE_ANOMALY_NAME_INHIBITED;
import static org.openhab.binding.diagral.internal.DiagralBindingConstants.HEADER_AUTHORIZATION;
import static org.openhab.binding.diagral.internal.DiagralBindingConstants.HEADER_X_APIKEY;
import static org.openhab.binding.diagral.internal.DiagralBindingConstants.HEADER_X_HMAC;
import static org.openhab.binding.diagral.internal.DiagralBindingConstants.HEADER_X_PIN_CODE;
import static org.openhab.binding.diagral.internal.DiagralBindingConstants.HEADER_X_TIMESTAMP;
import static org.openhab.binding.diagral.internal.DiagralBindingConstants.MODE_FULL;
import static org.openhab.binding.diagral.internal.DiagralBindingConstants.MODE_OFF;
import static org.openhab.binding.diagral.internal.DiagralBindingConstants.MODE_PARTIAL1;
import static org.openhab.binding.diagral.internal.DiagralBindingConstants.MODE_PARTIAL2;
import static org.openhab.binding.diagral.internal.DiagralBindingConstants.MODE_PRESENCE;
import static org.openhab.binding.diagral.internal.DiagralBindingConstants.MODE_TEMPO_GROUP;
import static org.openhab.binding.diagral.internal.DiagralBindingConstants.PRODUCT_TYPE_ALARM;
import static org.openhab.binding.diagral.internal.DiagralBindingConstants.PRODUCT_TYPE_CENTRAL;
import static org.openhab.binding.diagral.internal.DiagralBindingConstants.PRODUCT_TYPE_COMMAND;
import static org.openhab.binding.diagral.internal.DiagralBindingConstants.PRODUCT_TYPE_PLUG;
import static org.openhab.binding.diagral.internal.DiagralBindingConstants.PRODUCT_TYPE_SENSOR;
import static org.openhab.binding.diagral.internal.DiagralBindingConstants.SYSTEM_STATUS_GROUP;

import java.io.EOFException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.http.HttpMethod;
import org.openhab.binding.diagral.internal.dto.DiagralAnomalies;
import org.openhab.binding.diagral.internal.dto.DiagralAnomalyDetail;
import org.openhab.binding.diagral.internal.dto.DiagralAnomalyName;
import org.openhab.binding.diagral.internal.dto.DiagralApiKeyRequest;
import org.openhab.binding.diagral.internal.dto.DiagralLoginRequest;
import org.openhab.binding.diagral.internal.dto.DiagralSystemConfiguration;
import org.openhab.binding.diagral.internal.dto.DiagralSystemDetails;
import org.openhab.binding.diagral.internal.dto.DiagralSystemStatus;
import org.openhab.binding.diagral.internal.exception.DiagralException;
import org.openhab.binding.diagral.internal.util.DiagralCryptoUtil;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

/**
 * The {@link FakeDiagralApiServer} is a stateful, in-process simulation of the Diagral cloud API's
 * business logic, used as a test fixture so integration-style tests can exercise
 * {@code DiagralHttpClient}/{@code DiagralAuthenticationManager}/{@code DiagralBridgeHandler} together
 * without ever making a real network call.
 *
 * <p>
 * Phase 1 scope: the two-step authentication flow ({@code login} + {@code api_key} generation), API-key
 * deletion, and the three read-only "fetch state" endpoints ({@code status}, {@code configurations},
 * the bare system endpoint for details).
 * </p>
 *
 * <p>
 * Phase 2 scope: the arm/disarm state machine behind {@code status}. Every mode command ({@code stop}/
 * {@code start}/{@code presence}/{@code partial_start_1}/{@code partial_start_2}) and group command
 * ({@code activate_group}/{@code disable_group}) mutates this fake's internal state rather than the
 * {@code status} response directly, and {@code status} reads then reproduce the two live-confirmed quirks
 * documented on {@code DiagralBindingConstants}: a transitional status ({@code TEMPO_1}/{@code TEMPO_2}/
 * {@code TEMPO_GROUP}) for a configurable number of reads (see {@link #setTransitionReads(int)}) before
 * settling, and an {@code activated_groups} list that is only ever trustworthy while settled at
 * {@code GROUP} - empty for every named mode and every transitional status, exactly like the real API.
 * </p>
 *
 * <p>
 * Phase 3 scope: device enable/disable ({@code .../{type}/{productId}/enable} and {@code /disable}), the
 * {@code anomalies} endpoint that verifies them (returning {@code 404} when nothing at all is inhibited,
 * exactly like the documented real quirk), and general-purpose fault injection ({@link #injectTimeout()}/
 * {@link #injectConnectionFailure()}/{@link #injectHttpStatus(int)}) that can be queued in front of
 * <em>any</em> request, named-endpoint or not - for exercising this bundle's retry/failure-counting logic
 * (e.g. {@code MAX_CONSECUTIVE_POLL_FAILURES}) without a real flaky network. {@link
 * #injectServerErrorButApplyOnEnableDisable()} reproduces the specific documented Diagral quirk where an
 * enable/disable call returns an empty-bodied {@code 500} despite having actually applied server-side -
 * see {@code DiagralHttpClient.actionProduct()}'s Javadoc.
 * </p>
 *
 * <p>
 * Wired in at the exact same seam {@code DiagralHttpClientTest} and {@code DiagralBridgeHandlerTest}
 * already use: a mocked Jetty {@link HttpClient}. Call {@link #installOn(HttpClient)} on an
 * {@code @Mock HttpClient} field to make it behave like this fake server, or {@link #asMockedHttpClient()}
 * to obtain a fresh one. Every request is parsed into a {@link RecordedRequest} at the moment
 * {@code request.send()} is invoked (so it reflects the request's fully-built state: method, headers, and
 * body), routed by method+path, and answered statefully - e.g. a successful {@code api_key} call rotates
 * the API key that later signed requests are validated against, exactly like the real API.
 * </p>
 *
 * <p>
 * <b>Caveat on non-2xx status codes for auth failures on signed requests</b> (bad API key/HMAC/PIN code):
 * unlike the documented, live-confirmed status codes in {@code DiagralHttpClient}'s Javadoc (400/401/403/
 * 404/429, sourced from real observed traffic), the specific codes this fake returns for a bad signature
 * or a wrong PIN code are a reasonable simulation choice, not a verified fact about the real API - treat
 * them as good-enough-for-testing-this-binding's-handling, not as documentation of Diagral's behaviour.
 * </p>
 *
 * @author David Martin - Initial contribution
 */
@NonNullByDefault
public class FakeDiagralApiServer {

    /**
     * The transitional {@code status} value the real API reports while heading to {@code PARTIAL1} or
     * {@code PRESENCE}, per {@code DiagralBindingConstants.MODE_TEMPO_GROUP}'s Javadoc. Not a formal
     * constant on {@code DiagralBindingConstants} itself, since production code never compares against it
     * by name - every status other than the five named modes and {@code GROUP} falls through the same
     * "not a named mode" branch in {@code DiagralBridgeHandler.isGroupActive()}/{@code getDisplayedMode()}.
     */
    private static final String TRANSITIONAL_TEMPO_1 = "TEMPO_1";

    /** The transitional {@code status} value the real API reports while heading to {@code PARTIAL2}. */
    private static final String TRANSITIONAL_TEMPO_2 = "TEMPO_2";

    /**
     * Matches the {@code .../{type}/{productId}/enable} or {@code /disable} path suffix (relative to
     * {@code /systems/{serialId}}) that {@code DiagralHttpClient.actionProduct()} builds - group 1 is the
     * product type, group 2 the per-category device index, group 3 whether it's an enable or disable call.
     */
    private static final Pattern ACTION_PRODUCT_PATTERN = Pattern.compile("^/([A-Z]+)/(\\d+)("
            + Pattern.quote(API_ENDPOINT_ENABLE) + "|" + Pattern.quote(API_ENDPOINT_DISABLE) + ")$");

    private final Gson gson = new GsonBuilder().create();

    private final String username;
    private final String password;
    private final String serialId;
    private final String pinCode;

    /** Access tokens ever issued by {@link #handleLogin}; never expired, so any of them stays usable. */
    private final Set<String> validAccessTokens = new LinkedHashSet<>();

    /** API keys currently registered server-side (i.e. issued but not yet deleted). */
    private final Set<String> issuedApiKeys = new LinkedHashSet<>();

    /** API keys deleted via the {@code DELETE .../api_keys/{apiKey}} endpoint, in call order. */
    private final List<String> deletedApiKeys = new ArrayList<>();

    /** Every request this fake has answered, in call order, for test assertions. */
    private final List<RecordedRequest> requestLog = new ArrayList<>();

    private @Nullable String currentApiKey;
    private @Nullable String currentSecretKey;

    private DiagralSystemConfiguration systemConfiguration = defaultSystemConfiguration();
    private DiagralSystemDetails systemDetails = new DiagralSystemDetails();

    /** Group indices currently armed via a direct {@code activate_group} call (not a named mode). */
    private final Set<Integer> directlyActivatedGroups = new LinkedHashSet<>();

    /**
     * How many {@code status} reads after a command still show the transitional value (see
     * {@link #setTransitionReads(int)}).
     */
    private int transitionReadsConfigured = 1;

    /** Transitional reads still owed before the pending command (if any) settles. Zero means settled. */
    private int transitionReadsRemaining;

    /**
     * The transitional {@code status} value ({@code TEMPO_1}/{@code TEMPO_2}/{@code TEMPO_GROUP}) to serve while
     * {@link #transitionReadsRemaining} is positive.
     */
    private String pendingTransitionalStatus = MODE_OFF;

    /** The {@code status} value the pending command settles to once {@link #transitionReadsRemaining} reaches zero. */
    private String pendingTargetStatus = MODE_OFF;

    /** The {@code activated_groups} the pending command settles to alongside {@link #pendingTargetStatus}. */
    private List<Integer> pendingTargetActivatedGroups = List.of();

    /**
     * The last settled (non-transitional) {@code status} value - what {@code status} reports once nothing is pending.
     */
    private String settledStatus = MODE_OFF;

    /** The last settled {@code activated_groups} - what {@code status} reports once nothing is pending. */
    private List<Integer> settledActivatedGroups = List.of();

    /**
     * Each device's current "inhibited" (disabled) state, keyed by product type + per-category index -
     * mirrors {@code DiagralDevice#inhibited}/{@code DiagralAnomalyDetail}. Absent means not inhibited
     * (the same "absence = no anomaly" convention {@code DiagralAnomalies} itself documents), so only
     * inhibited devices are ever stored here.
     */
    private final Map<DeviceKey, Boolean> deviceInhibited = new LinkedHashMap<>();

    /**
     * One-shot: the next {@code enable}/{@code disable} call returns an empty-bodied {@code 500} but
     * still applies the change server-side - the documented real Diagral quirk (see
     * {@link #injectServerErrorButApplyOnEnableDisable()}).
     */
    private boolean pendingServerErrorButApply;

    /** Requests queued to fail before ever reaching {@link #route}, consumed one per request, FIFO. */
    private final Deque<Fault> injectedFaults = new ArrayDeque<>();

    /**
     * Constructs a fake Diagral API pre-configured with one account's credentials.
     *
     * @param username the Diagral account email the fake expects on {@code login}
     * @param password the Diagral account password the fake expects on {@code login}
     * @param serialId the Diagral box serial ID this fake serves - must match what the
     *            {@code DiagralAuthenticationManager} under test was built with, since it appears in every
     *            endpoint path
     * @param pinCode the PIN code the fake expects on the {@code X-PIN-CODE} header of pin-gated requests
     */
    public FakeDiagralApiServer(String username, String password, String serialId, String pinCode) {
        this.username = username;
        this.password = password;
        this.serialId = serialId;
        this.pinCode = pinCode;
    }

    /**
     * Builds the default {@code configurations} response: a fully-populated-but-empty installation (no
     * devices, no groups), so a bridge authenticating against a fresh fake server doesn't NPE walking
     * empty-but-non-null lists.
     *
     * @return a fresh default {@link DiagralSystemConfiguration}
     */
    private static DiagralSystemConfiguration defaultSystemConfiguration() {
        DiagralSystemConfiguration config = new DiagralSystemConfiguration();
        config.installationState = 1;
        config.presenceGroup = List.of();
        config.partialGroup1 = List.of();
        config.partialGroup2 = List.of();
        config.sensors = List.of();
        config.sirens = List.of();
        config.cameras = List.of();
        config.transmitters = List.of();
        config.commands = List.of();
        config.groups = List.of();
        return config;
    }

    /**
     * Directly sets the settled {@code status} response this fake serves, bypassing the arm/disarm state
     * machine entirely - no transitional reads, takes effect on the very next {@code status} request.
     *
     * <p>
     * For scripting an arbitrary fixture value directly (as Phase 1's tests do). To exercise the
     * transitional behaviour instead, drive it through the actual commands (
     * {@code DiagralHttpClient.setSystemMode()}/{@code activateGroup()}/{@code disableGroup()}) - see
     * {@link #setTransitionReads(int)}.
     * </p>
     *
     * @param systemStatus the status to serve; a {@code null} {@link DiagralSystemStatus#status} is
     *            treated as {@code OFF} and a {@code null} {@link DiagralSystemStatus#activatedGroups} as
     *            empty, so this fake's internal state stays non-null
     * @return this fake, for chaining
     */
    public FakeDiagralApiServer setSystemStatus(DiagralSystemStatus systemStatus) {
        String status = systemStatus.status;
        List<Integer> activatedGroups = systemStatus.activatedGroups;
        settleImmediately(status != null ? status : MODE_OFF, activatedGroups != null ? activatedGroups : List.of());
        return this;
    }

    /**
     * Configures how many {@code status} reads after an arm/disarm command still report the transitional
     * value ({@code TEMPO_1}/{@code TEMPO_2}/{@code TEMPO_GROUP}) before settling to the command's target -
     * simulating a group's {@code outputDelay} without any real waiting. Defaults to {@code 1}: the read
     * immediately following a command sees the transitional status, and the next one after that sees the
     * settled result.
     *
     * <p>
     * Pass {@code 0} to make every command settle immediately, useful for tests that only care about the
     * end state and want to skip the transitional window entirely.
     * </p>
     *
     * @param reads the number of transitional {@code status} reads to serve before settling; must be
     *            {@code >= 0}
     * @return this fake, for chaining
     */
    public FakeDiagralApiServer setTransitionReads(int reads) {
        if (reads < 0) {
            throw new IllegalArgumentException("reads must be >= 0: " + reads);
        }
        this.transitionReadsConfigured = reads;
        return this;
    }

    /**
     * Replaces the {@code configurations} response the fake serves from the next matching request onward.
     *
     * @param systemConfiguration the configuration to serve
     * @return this fake, for chaining
     */
    public FakeDiagralApiServer setSystemConfiguration(DiagralSystemConfiguration systemConfiguration) {
        this.systemConfiguration = systemConfiguration;
        return this;
    }

    /**
     * Replaces the system-details response the fake serves from the next matching request onward.
     *
     * @param systemDetails the details to serve
     * @return this fake, for chaining
     */
    public FakeDiagralApiServer setSystemDetails(DiagralSystemDetails systemDetails) {
        this.systemDetails = systemDetails;
        return this;
    }

    /**
     * Directly sets one device's "inhibited" state, without going through an {@code enable}/
     * {@code disable} call - for pre-seeding a device as already inhibited before a test's first request.
     *
     * @param type the product type ({@code DiagralBindingConstants.PRODUCT_TYPE_*})
     * @param productId the per-category numeric device index
     * @param inhibited the inhibited state to set
     * @return this fake, for chaining
     */
    public FakeDiagralApiServer setDeviceInhibited(String type, int productId, boolean inhibited) {
        DeviceKey key = new DeviceKey(type, productId);
        if (inhibited) {
            deviceInhibited.put(key, true);
        } else {
            deviceInhibited.remove(key);
        }
        return this;
    }

    /**
     * Makes the next {@code enable}/{@code disable} call return an empty-bodied {@code 500} while still
     * applying the change server-side - the specific documented Diagral cloud quirk that
     * {@code DiagralHttpClient.actionProduct()} works around by verifying the device's real state via
     * {@code anomalies} rather than trusting the error response. One-shot: consumed by the next matching
     * call, so call again to reproduce it more than once.
     *
     * @return this fake, for chaining
     */
    public FakeDiagralApiServer injectServerErrorButApplyOnEnableDisable() {
        this.pendingServerErrorButApply = true;
        return this;
    }

    /**
     * Queues the next request (to any endpoint, not just {@code enable}/{@code disable}) to fail with a
     * client-side timeout, mirroring the {@code Request timeout} failures this bundle has repeatedly
     * observed live against the real API (see this bundle's {@code CLAUDE.md}). Surfaces to
     * {@code DiagralHttpClient} callers as a plain {@code DiagralException} wrapping a
     * {@link TimeoutException}, exactly like a real Jetty request timeout would.
     *
     * @return this fake, for chaining
     */
    public FakeDiagralApiServer injectTimeout() {
        injectedFaults.add(new Fault(FaultKind.TIMEOUT, 0));
        return this;
    }

    /**
     * Queues the next request (to any endpoint) to fail with a client-side connection failure, mirroring
     * the {@code EOFException}s this bundle has repeatedly observed live against the real API mid-response
     * (see this bundle's {@code CLAUDE.md}). Surfaces to {@code DiagralHttpClient} callers as a plain
     * {@code DiagralException} wrapping an {@link ExecutionException} whose cause is an {@link
     * EOFException}, exactly like Jetty would report a connection dropped mid-request.
     *
     * @return this fake, for chaining
     */
    public FakeDiagralApiServer injectConnectionFailure() {
        injectedFaults.add(new Fault(FaultKind.EXECUTION_FAILURE, 0));
        return this;
    }

    /**
     * Queues the next request (to any endpoint) to return the given HTTP status with a generic body,
     * regardless of what endpoint it targets - for exercising {@code DiagralHttpClient}'s status-code
     * handling (e.g. {@code 429} rate-limiting, an arbitrary {@code 5xx}) without needing a scenario that
     * naturally produces it.
     *
     * @param status the HTTP status code to return for the next request
     * @return this fake, for chaining
     */
    public FakeDiagralApiServer injectHttpStatus(int status) {
        injectedFaults.add(new Fault(FaultKind.HTTP_STATUS, status));
        return this;
    }

    /**
     * Gets every request this fake has answered so far, in call order.
     *
     * @return an unmodifiable snapshot of the request log
     */
    public List<RecordedRequest> getRequestLog() {
        return List.copyOf(requestLog);
    }

    /**
     * Gets the API keys deleted via the key-deletion endpoint so far, in call order - useful for asserting
     * the S2 superseded-key cleanup behaviour end to end against this fake.
     *
     * @return an unmodifiable snapshot of deleted API keys
     */
    public List<String> getDeletedApiKeys() {
        return List.copyOf(deletedApiKeys);
    }

    /**
     * Gets the API key this fake currently considers valid for signing.
     *
     * @return the current API key, or {@code null} if no successful authentication has happened yet
     */
    public @Nullable String getCurrentApiKey() {
        return currentApiKey;
    }

    /**
     * Creates a fresh mocked Jetty {@link HttpClient} wired to this fake server.
     *
     * @return a Mockito mock behaving like this fake's simulated Diagral API
     */
    public HttpClient asMockedHttpClient() {
        HttpClient httpClient = mock(HttpClient.class);
        installOn(httpClient);
        return httpClient;
    }

    /**
     * Wires an existing mocked Jetty {@link HttpClient} (e.g. an {@code @Mock} field already used
     * elsewhere in a test class) to behave like this fake server.
     *
     * <p>
     * Mirrors the low-level stubbing pattern already used by {@code DiagralHttpClientTest}, but instead of
     * replaying a pre-queued list of canned responses, it captures each request's full state (method,
     * headers, body) into a {@link PendingRequest} and resolves the response lazily in {@link #route} at
     * {@code request.send()} time - which is what lets responses depend on this fake's evolving state
     * (e.g. which API key is currently valid).
     * </p>
     *
     * @param httpClient the mocked Jetty HTTP client to wire
     */
    public void installOn(HttpClient httpClient) {
        when(httpClient.newRequest(anyString())).thenAnswer(invocation -> {
            String url = invocation.getArgument(0);
            Request request = mock(Request.class);
            PendingRequest pending = new PendingRequest(url);

            when(request.method(any(HttpMethod.class))).thenAnswer(m -> {
                pending.method = m.getArgument(0).toString();
                return request;
            });
            when(request.header(anyString(), any())).thenAnswer(h -> {
                // Explicit (Object) cast: getArgument(1)'s inferred type otherwise makes javac resolve
                // String.valueOf(...) to the char[] overload instead of the Object one, throwing a
                // ClassCastException at runtime for any non-char[] header value (e.g. every String header
                // this class actually sends).
                pending.headers.put(h.getArgument(0).toString(), String.valueOf((Object) h.getArgument(1)));
                return request;
            });
            when(request.timeout(anyLong(), any())).thenReturn(request);
            when(request.content(any())).thenAnswer(c -> {
                pending.body = extractBody(c.getArgument(0));
                return request;
            });
            when(request.getMethod()).thenAnswer(m -> pending.method);
            when(request.getURI()).thenReturn(URI.create(url));

            ContentResponse response = mock(ContentResponse.class);
            when(request.send()).thenAnswer(s -> {
                requestLog.add(pending.toRecordedRequest());

                Fault fault = injectedFaults.poll();
                if (fault != null) {
                    switch (fault.kind()) {
                        case TIMEOUT:
                            throw new TimeoutException("fake-injected timeout");
                        case EXECUTION_FAILURE:
                            throw new ExecutionException("fake-injected connection failure", new EOFException());
                        case HTTP_STATUS:
                            when(response.getStatus()).thenReturn(fault.httpStatus());
                            when(response.getContentAsString()).thenReturn("{\"detail\":\"fake-injected status\"}");
                            return response;
                    }
                }

                FakeResponse fake = route(pending);
                when(response.getStatus()).thenReturn(fake.status());
                when(response.getContentAsString()).thenReturn(fake.body());
                return response;
            });
            return request;
        });
    }

    /**
     * Decodes the UTF-8 text body Jetty's {@code StringContentProvider} was constructed with.
     *
     * <p>
     * {@code DiagralHttpClient} always passes a real (not mocked) {@code StringContentProvider} to
     * {@code request.content(...)}, so this walks its {@code Iterable<ByteBuffer>} contents rather than
     * needing any special-casing for the mocked request itself.
     * </p>
     *
     * @param contentProvider the value passed to {@code Request.content(ContentProvider)}, expected to be
     *            iterable over UTF-8-encoded {@link ByteBuffer} chunks
     * @return the decoded request body, or an empty string if {@code contentProvider} isn't iterable
     */
    private static String extractBody(@Nullable Object contentProvider) {
        if (!(contentProvider instanceof Iterable<?> iterable)) {
            return "";
        }
        StringBuilder body = new StringBuilder();
        for (Object chunk : iterable) {
            if (chunk instanceof ByteBuffer buffer) {
                byte[] bytes = new byte[buffer.remaining()];
                buffer.duplicate().get(bytes);
                body.append(new String(bytes, StandardCharsets.UTF_8));
            }
        }
        return body.toString();
    }

    /**
     * Strips the scheme/host/query from a full request URL, leaving just the path this fake routes on.
     *
     * @param url the full request URL (e.g. {@code https://appv3.tt-monitor.com/emerald/v1/systems/S1/status})
     * @return the path relative to the API base (e.g. {@code /systems/S1/status})
     */
    private static String pathOf(String url) {
        URI uri = URI.create(url);
        return uri.getPath().substring("/emerald/v1".length());
    }

    /**
     * Routes one fully-captured request to the matching simulated endpoint, mirroring
     * {@code DiagralBindingConstants}' endpoint paths.
     *
     * @param req the request to route, captured at {@code send()} time
     * @return the simulated HTTP response
     */
    private FakeResponse route(PendingRequest req) {
        String path = pathOf(req.url);
        String method = req.method;

        if ("POST".equals(method) && path.equals(API_ENDPOINT_LOGIN)) {
            return handleLogin(req);
        }
        if ("POST".equals(method) && path.equals(API_ENDPOINT_API_KEY)) {
            return handleGenerateApiKey(req);
        }
        String apiKeyDeletePrefix = API_ENDPOINT_USER_SYSTEMS + "/" + serialId + API_ENDPOINT_API_KEYS + "/";
        if ("DELETE".equals(method) && path.startsWith(apiKeyDeletePrefix)) {
            return handleDeleteApiKey(req, path.substring(apiKeyDeletePrefix.length()));
        }
        String systemPath = API_ENDPOINT_SYSTEMS + "/" + serialId;
        if ("GET".equals(method) && (systemPath + API_ENDPOINT_STATUS).equals(path)) {
            return handleAuthenticatedGet(req, true, this::consumeStatusRead);
        }
        if ("GET".equals(method) && (systemPath + API_ENDPOINT_CONFIGURATIONS).equals(path)) {
            return handleAuthenticatedGet(req, false, () -> gson.toJson(systemConfiguration));
        }
        if ("GET".equals(method) && path.equals(systemPath)) {
            return handleAuthenticatedGet(req, true, () -> gson.toJson(systemDetails));
        }
        if ("POST".equals(method) && (systemPath + API_ENDPOINT_STOP).equals(path)) {
            return handleSetNamedMode(req, MODE_OFF);
        }
        if ("POST".equals(method) && (systemPath + API_ENDPOINT_START).equals(path)) {
            return handleSetNamedMode(req, MODE_FULL);
        }
        if ("POST".equals(method) && (systemPath + API_ENDPOINT_PRESENCE).equals(path)) {
            return handleSetNamedMode(req, MODE_PRESENCE);
        }
        if ("POST".equals(method) && (systemPath + API_ENDPOINT_PARTIAL_START_1).equals(path)) {
            return handleSetNamedMode(req, MODE_PARTIAL1);
        }
        if ("POST".equals(method) && (systemPath + API_ENDPOINT_PARTIAL_START_2).equals(path)) {
            return handleSetNamedMode(req, MODE_PARTIAL2);
        }
        if ("POST".equals(method) && (systemPath + API_ENDPOINT_ACTIVATE_GROUP).equals(path)) {
            return handleActivateGroup(req);
        }
        if ("POST".equals(method) && (systemPath + API_ENDPOINT_DISABLE_GROUP).equals(path)) {
            return handleDisableGroup(req);
        }
        if ("GET".equals(method) && (systemPath + API_ENDPOINT_ANOMALIES).equals(path)) {
            return handleGetAnomalies(req);
        }
        if ("POST".equals(method) && path.startsWith(systemPath + "/")) {
            Matcher actionProduct = ACTION_PRODUCT_PATTERN.matcher(path.substring(systemPath.length()));
            if (actionProduct.matches()) {
                return handleActionProduct(req, actionProduct.group(1), Integer.parseInt(actionProduct.group(2)),
                        API_ENDPOINT_ENABLE.equals(actionProduct.group(3)));
            }
        }
        return new FakeResponse(404, "{\"detail\":\"no such fake Diagral endpoint: " + method + " " + path + "\"}");
    }

    /**
     * Simulates {@code POST /users/authenticate/login}: validates the submitted credentials against this
     * fake's configured account and, on success, mints a new bearer access token.
     *
     * @param req the captured login request
     * @return {@code 200} with a fresh access token on matching credentials, otherwise {@code 401}
     */
    private FakeResponse handleLogin(PendingRequest req) {
        DiagralLoginRequest login = gson.fromJson(req.body, DiagralLoginRequest.class);
        if (login == null || !username.equals(login.username) || !password.equals(login.password)) {
            return new FakeResponse(401, "{\"detail\":\"invalid credentials\"}");
        }
        String token = UUID.randomUUID().toString();
        validAccessTokens.add(token);
        return new FakeResponse(200, "{\"access_token\":\"" + token + "\"}");
    }

    /**
     * Simulates {@code POST /users/api_key}: on a valid bearer token, mints a brand-new API key/secret
     * key pair and makes it the fake's current signing credential - mirroring the real API's behaviour of
     * never invalidating the previous pair on its own (see {@code DiagralHttpClient.authenticate()}'s
     * Javadoc on why S2's explicit deletion step exists).
     *
     * @param req the captured API-key-generation request
     * @return {@code 200} with a freshly minted key pair, or {@code 401} if the bearer token isn't one
     *         this fake has issued
     */
    private FakeResponse handleGenerateApiKey(PendingRequest req) {
        if (!isValidBearerToken(req)) {
            return new FakeResponse(401, "{\"detail\":\"invalid or missing access token\"}");
        }
        DiagralApiKeyRequest apiKeyRequest = gson.fromJson(req.body, DiagralApiKeyRequest.class);
        if (apiKeyRequest == null || !serialId.equals(apiKeyRequest.serialId)) {
            return new FakeResponse(400, "{\"detail\":\"unknown serial id\"}");
        }

        String apiKey = UUID.randomUUID().toString();
        String secretKey = UUID.randomUUID().toString();
        issuedApiKeys.add(apiKey);
        currentApiKey = apiKey;
        currentSecretKey = secretKey;
        return new FakeResponse(200, "{\"api_key\":\"" + apiKey + "\",\"secret_key\":\"" + secretKey + "\"}");
    }

    /**
     * Simulates {@code DELETE /users/systems/{serialId}/api_keys/{apiKey}}: retires the named key so it
     * can no longer sign requests, and records the deletion for test assertions (see
     * {@link #getDeletedApiKeys()}).
     *
     * @param req the captured deletion request
     * @param apiKeyToDelete the API key targeted by the request, parsed from the URL path
     * @return {@code 200} on a valid bearer token (deletion is treated as idempotent, matching how
     *         {@code DiagralHttpClient.deleteSupersededApiKey()} tolerates it), otherwise {@code 401}
     */
    private FakeResponse handleDeleteApiKey(PendingRequest req, String apiKeyToDelete) {
        if (!isValidBearerToken(req)) {
            return new FakeResponse(401, "{\"detail\":\"invalid or missing access token\"}");
        }
        issuedApiKeys.remove(apiKeyToDelete);
        deletedApiKeys.add(apiKeyToDelete);
        if (apiKeyToDelete.equals(currentApiKey)) {
            currentApiKey = null;
            currentSecretKey = null;
        }
        return new FakeResponse(200, "{}");
    }

    /**
     * Simulates a named-mode command ({@code stop}/{@code start}/{@code presence}/{@code partial_start_1}/
     * {@code partial_start_2}): starts (or, for {@code OFF}, immediately completes) an arm/disarm
     * transition, superseding any group directly activated via {@link #handleActivateGroup} - mirrors
     * production {@code DiagralBridgeHandler.setSystemMode()} treating a named-mode command as arming the
     * whole system, not layering onto a direct group activation.
     *
     * <p>
     * Per the live-confirmed quirk documented on {@code DiagralBindingConstants.MODE_TEMPO_GROUP}/
     * {@code SYSTEM_STATUS_GROUP}, the settled {@code activated_groups} for every named mode is always
     * empty - group membership for a named mode is derivable only from configuration, never from this
     * field, which is exactly why {@code DiagralBridgeHandler.isGroupActive()} doesn't trust it here.
     * </p>
     *
     * @param req the captured command request
     * @param mode the named mode this command arms ({@code MODE_OFF}/{@code MODE_FULL}/
     *            {@code MODE_PRESENCE}/{@code MODE_PARTIAL1}/{@code MODE_PARTIAL2})
     * @return {@code 200} with the immediate (possibly still-transitional) status on success, or the
     *         validation failure from {@link #validateSignedRequest}
     */
    private FakeResponse handleSetNamedMode(PendingRequest req, String mode) {
        FakeResponse failure = validateSignedRequest(req, true);
        if (failure != null) {
            return failure;
        }

        directlyActivatedGroups.clear();
        if (MODE_OFF.equals(mode)) {
            settleImmediately(MODE_OFF, List.of());
        } else {
            String transitional = MODE_PARTIAL2.equals(mode) ? TRANSITIONAL_TEMPO_2
                    : MODE_FULL.equals(mode) ? MODE_TEMPO_GROUP : TRANSITIONAL_TEMPO_1;
            beginTransition(transitional, mode, List.of());
        }
        return new FakeResponse(200, currentStatusJson());
    }

    /**
     * Simulates {@code POST .../activate_group}: arms one or more groups directly, entering the same
     * {@code TEMPO_GROUP} transitional window a {@code FULL} command does, before settling to
     * {@code GROUP} status with {@code activated_groups} populated - the one status where that field is
     * live-confirmed trustworthy (see {@code DiagralBindingConstants.SYSTEM_STATUS_GROUP}).
     *
     * @param req the captured command request; its body must be the {@code {"groups":[<int>,...]}} shape
     *            production {@code DiagralHttpClient.buildGroupsPayload()} sends
     * @return {@code 200} with the immediate status on success, {@code 400} if the body isn't that shape,
     *         or the validation failure from {@link #validateSignedRequest}
     */
    private FakeResponse handleActivateGroup(PendingRequest req) {
        FakeResponse failure = validateSignedRequest(req, true);
        if (failure != null) {
            return failure;
        }
        List<Integer> groups = parseGroupsPayload(req.body);
        if (groups == null) {
            return new FakeResponse(400, "{\"detail\":\"groups payload required\"}");
        }

        directlyActivatedGroups.addAll(groups);
        beginTransition(MODE_TEMPO_GROUP, SYSTEM_STATUS_GROUP, List.copyOf(directlyActivatedGroups));
        return new FakeResponse(200, currentStatusJson());
    }

    /**
     * Simulates {@code POST .../disable_group}: disarms one or more directly-activated groups.
     *
     * <p>
     * <b>Simulation choice, not a verified real-API fact</b> (see this class's Javadoc caveat): this
     * settles immediately rather than going through a transitional window, since nothing in this bundle's
     * documented live findings pins down disable's transitional behaviour the way {@code activate_group}'s
     * is pinned down. Settles to {@code OFF} once no directly-activated group remains, otherwise back to
     * {@code GROUP} with whichever groups are still active.
     * </p>
     *
     * @param req the captured command request; its body must be the {@code {"groups":[<int>,...]}} shape
     * @return {@code 200} with the resulting status on success, {@code 400} if the body isn't that shape,
     *         or the validation failure from {@link #validateSignedRequest}
     */
    private FakeResponse handleDisableGroup(PendingRequest req) {
        FakeResponse failure = validateSignedRequest(req, true);
        if (failure != null) {
            return failure;
        }
        List<Integer> groups = parseGroupsPayload(req.body);
        if (groups == null) {
            return new FakeResponse(400, "{\"detail\":\"groups payload required\"}");
        }

        directlyActivatedGroups.removeAll(groups);
        if (directlyActivatedGroups.isEmpty()) {
            settleImmediately(MODE_OFF, List.of());
        } else {
            settleImmediately(SYSTEM_STATUS_GROUP, List.copyOf(directlyActivatedGroups));
        }
        return new FakeResponse(200, currentStatusJson());
    }

    /**
     * Simulates {@code POST .../{type}/{productId}/enable} or {@code /disable}: flips one device's
     * "inhibited" state, which the {@code anomalies} endpoint (see {@link #handleGetAnomalies}) then
     * reflects.
     *
     * <p>
     * If {@link #injectServerErrorButApplyOnEnableDisable()} was called, this consumes that one-shot flag,
     * applies the change exactly as it normally would, but still returns an empty-bodied {@code 500} -
     * reproducing the documented Diagral cloud quirk that {@code DiagralHttpClient.actionProduct()} works
     * around by re-checking the device's real state via {@code anomalies} rather than trusting the error.
     * </p>
     *
     * @param req the captured command request
     * @param type the product type parsed from the URL
     * @param productId the per-category device index parsed from the URL
     * @param enable {@code true} for an {@code enable} call, {@code false} for {@code disable}
     * @return {@code 200} (or the quirked {@code 500}) on success, or the validation failure from
     *         {@link #validateSignedRequest}
     */
    private FakeResponse handleActionProduct(PendingRequest req, String type, int productId, boolean enable) {
        FakeResponse failure = validateSignedRequest(req, true);
        if (failure != null) {
            return failure;
        }

        setDeviceInhibited(type, productId, !enable);
        if (pendingServerErrorButApply) {
            pendingServerErrorButApply = false;
            return new FakeResponse(500, "");
        }
        return new FakeResponse(200, "");
    }

    /**
     * Simulates {@code GET .../anomalies}: reports every device this fake currently considers inhibited,
     * grouped into the per-category lists {@code DiagralHttpClient.isDeviceInhibited()} reads. Returns
     * {@code 404} when nothing at all is inhibited, matching the documented real API quirk that
     * {@code DiagralHttpClient.getAnomalies()} translates back into an empty {@link DiagralAnomalies}
     * rather than an error.
     *
     * @param req the captured request
     * @return {@code 200} with the current anomalies on success, {@code 404} if nothing is inhibited, or
     *         the validation failure from {@link #validateSignedRequest}
     */
    private FakeResponse handleGetAnomalies(PendingRequest req) {
        FakeResponse failure = validateSignedRequest(req, false);
        if (failure != null) {
            return failure;
        }
        if (deviceInhibited.isEmpty()) {
            return new FakeResponse(404, "");
        }
        return new FakeResponse(200, gson.toJson(buildAnomalies()));
    }

    /**
     * Builds the {@code anomalies} response from {@link #deviceInhibited}, sorting each inhibited device
     * into the category list matching its product type - the same type-to-category mapping
     * {@code DiagralHttpClient.isDeviceInhibited()} uses. A device whose type has no matching category
     * (only {@code BOX}) is silently dropped, mirroring that method's {@code null}-returning default case:
     * such a device can never be verified this way regardless of what the real API returns.
     *
     * @return the anomalies reflecting every currently-inhibited device
     */
    private DiagralAnomalies buildAnomalies() {
        DiagralAnomalies anomalies = new DiagralAnomalies();
        for (Map.Entry<DeviceKey, Boolean> entry : deviceInhibited.entrySet()) {
            if (!entry.getValue()) {
                continue;
            }
            DeviceKey key = entry.getKey();
            DiagralAnomalyDetail detail = new DiagralAnomalyDetail();
            detail.deviceIndex = key.productId();
            DiagralAnomalyName inhibited = new DiagralAnomalyName();
            inhibited.name = DEVICE_ANOMALY_NAME_INHIBITED;
            detail.anomalyNames = List.of(inhibited);

            switch (key.type()) {
                case PRODUCT_TYPE_SENSOR:
                    anomalies.sensors = append(anomalies.sensors, detail);
                    break;
                case PRODUCT_TYPE_ALARM:
                    anomalies.sirens = append(anomalies.sirens, detail);
                    break;
                case PRODUCT_TYPE_COMMAND:
                    anomalies.commands = append(anomalies.commands, detail);
                    break;
                case PRODUCT_TYPE_PLUG:
                    anomalies.transmitters = append(anomalies.transmitters, detail);
                    break;
                case PRODUCT_TYPE_CENTRAL:
                    anomalies.central = append(anomalies.central, detail);
                    break;
                default:
                    // No matching category (e.g. BOX) - not representable, same as production's null case.
                    break;
            }
        }
        return anomalies;
    }

    /**
     * Appends one detail to a possibly-null category list, returning a new list rather than mutating -
     * {@code DiagralAnomalies}' fields aren't guaranteed mutable {@link List} implementations.
     *
     * @param list the category list so far, or {@code null} if empty
     * @param detail the detail to append
     * @return a new list containing every prior element plus {@code detail}
     */
    private static List<DiagralAnomalyDetail> append(@Nullable List<DiagralAnomalyDetail> list,
            DiagralAnomalyDetail detail) {
        List<DiagralAnomalyDetail> result = new ArrayList<>(list == null ? List.of() : list);
        result.add(detail);
        return result;
    }

    /**
     * Parses an {@code activate_group}/{@code disable_group} request body into the group indices it names,
     * validating it matches the {@code {"groups":[<int>,...]}} shape the real API requires (see
     * {@code DiagralHttpClient.buildGroupsPayload()}'s Javadoc on the 422 the real API returns for the
     * older, wrong {@code {"group_id":"<string>"}} shape - this fake returns 400 for the same reason
     * {@link #handleActivateGroup}/{@link #handleDisableGroup} do for any other malformed body).
     *
     * @param body the raw request body
     * @return the group indices named by the payload, or {@code null} if the body doesn't match the
     *         expected shape
     */
    private @Nullable List<Integer> parseGroupsPayload(String body) {
        try {
            GroupsPayload payload = gson.fromJson(body, GroupsPayload.class);
            return (payload == null || payload.groups == null) ? null : payload.groups;
        } catch (JsonSyntaxException e) {
            return null;
        }
    }

    /**
     * Begins an arm/disarm transition: from the next {@code status} read onward, {@code status} reports
     * {@code transitionalStatus} for {@link #transitionReadsConfigured} reads before settling to
     * {@code targetStatus}/{@code targetActivatedGroups} - or, if {@link #transitionReadsConfigured} is
     * {@code 0}, settles immediately with no transitional window at all.
     *
     * @param transitionalStatus the {@code status} value to report while the transition is in progress
     * @param targetStatus the {@code status} value to settle to
     * @param targetActivatedGroups the {@code activated_groups} to settle to alongside {@code targetStatus}
     */
    private void beginTransition(String transitionalStatus, String targetStatus, List<Integer> targetActivatedGroups) {
        if (transitionReadsConfigured <= 0) {
            settleImmediately(targetStatus, targetActivatedGroups);
            return;
        }
        this.pendingTransitionalStatus = transitionalStatus;
        this.pendingTargetStatus = targetStatus;
        this.pendingTargetActivatedGroups = targetActivatedGroups;
        this.transitionReadsRemaining = transitionReadsConfigured;
    }

    /**
     * Settles {@code status} to a value immediately, clearing any transition in progress.
     *
     * @param status the {@code status} value to settle to
     * @param activatedGroups the {@code activated_groups} to settle to alongside {@code status}
     */
    private void settleImmediately(String status, List<Integer> activatedGroups) {
        this.settledStatus = status;
        this.settledActivatedGroups = activatedGroups;
        this.transitionReadsRemaining = 0;
    }

    /**
     * Renders the current {@code status}/{@code activated_groups} as JSON without consuming a transitional
     * read - used for a command's own immediate response body, which production
     * {@code DiagralHttpClient.logImmediateStatus()} only ever parses for diagnostic logging, never acts
     * on, so peeking here rather than advancing the state machine is the correct simulation.
     *
     * @return the JSON body a {@code status} read would currently produce, without consuming it
     */
    private String currentStatusJson() {
        DiagralSystemStatus status = new DiagralSystemStatus();
        if (transitionReadsRemaining > 0) {
            status.status = pendingTransitionalStatus;
            status.activatedGroups = List.of();
        } else {
            status.status = settledStatus;
            status.activatedGroups = settledActivatedGroups;
        }
        return gson.toJson(status);
    }

    /**
     * Renders the {@code status} endpoint's response, consuming one transitional read if a transition is
     * in progress - the read that pushes {@link #transitionReadsRemaining} to zero settles the state
     * machine as part of producing its own (still-transitional) response, so the read after that one sees
     * the settled result.
     *
     * @return the JSON body for a {@code status} read
     */
    private String consumeStatusRead() {
        String json = currentStatusJson();
        if (transitionReadsRemaining > 0) {
            transitionReadsRemaining--;
            if (transitionReadsRemaining == 0) {
                settledStatus = pendingTargetStatus;
                settledActivatedGroups = pendingTargetActivatedGroups;
            }
        }
        return json;
    }

    /**
     * Simulates any of the three signed read-only endpoints this fake serves ({@code status}/
     * {@code configurations}/system details): validates the {@code X-APIKEY}/{@code X-HMAC} signature
     * (and, when required, the {@code X-PIN-CODE}) against this fake's current credentials before handing
     * back the requested body.
     *
     * @param req the captured request
     * @param requirePinCode whether {@code X-PIN-CODE} must also match, mirroring the {@code pinCode}
     *            flag {@code DiagralHttpClient.executeGet} passes per endpoint
     * @param bodySupplier produces the JSON body to serve once validation passes
     * @return {@code 200} with the supplied body on success, {@code 401} on a bad/missing API
     *         key or signature, {@code 403} on a wrong PIN code
     */
    private FakeResponse handleAuthenticatedGet(PendingRequest req, boolean requirePinCode,
            Supplier<String> bodySupplier) {
        FakeResponse failure = validateSignedRequest(req, requirePinCode);
        return failure != null ? failure : new FakeResponse(200, bodySupplier.get());
    }

    /**
     * Validates the {@code X-APIKEY}/{@code X-HMAC} signature (and, when required, the
     * {@code X-PIN-CODE}) of any signed request against this fake's current credentials - shared by every
     * signed endpoint this fake serves, GET or POST alike.
     *
     * @param req the captured request
     * @param requirePinCode whether {@code X-PIN-CODE} must also match, mirroring the {@code pinCode} flag
     *            production {@code DiagralHttpClient} passes per endpoint
     * @return {@code null} if the request is valid, otherwise the {@code 401}/{@code 403} response to
     *         return instead of handling the request
     */
    private @Nullable FakeResponse validateSignedRequest(PendingRequest req, boolean requirePinCode) {
        String apiKey = currentApiKey;
        String secretKey = currentSecretKey;
        String suppliedApiKey = req.headers.get(HEADER_X_APIKEY);
        if (apiKey == null || secretKey == null || !apiKey.equals(suppliedApiKey)) {
            return new FakeResponse(401, "{\"detail\":\"invalid or missing API key\"}");
        }

        String timestamp = req.headers.get(HEADER_X_TIMESTAMP);
        String suppliedHmac = req.headers.get(HEADER_X_HMAC);
        String expectedHmac = computeHmac(timestamp, apiKey, secretKey);
        if (expectedHmac == null || suppliedHmac == null
                || !expectedHmac.toLowerCase(Locale.ROOT).equals(suppliedHmac.toLowerCase(Locale.ROOT))) {
            return new FakeResponse(401, "{\"detail\":\"invalid signature\"}");
        }

        if (requirePinCode && !pinCode.equals(req.headers.get(HEADER_X_PIN_CODE))) {
            return new FakeResponse(403, "{\"detail\":\"invalid pin code\"}");
        }

        return null;
    }

    /**
     * Checks whether a request carries an {@code Authorization: Bearer <token>} header naming a token
     * this fake has issued via {@link #handleLogin}.
     *
     * @param req the captured request
     * @return {@code true} if the bearer token is one this fake recognizes as valid
     */
    private boolean isValidBearerToken(PendingRequest req) {
        String authorization = req.headers.get(HEADER_AUTHORIZATION);
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return false;
        }
        return validAccessTokens.contains(authorization.substring("Bearer ".length()));
    }

    /**
     * Recomputes the HMAC-SHA256 signature a genuine request for this timestamp/API key would carry, the
     * same way {@code DiagralAuthenticationManager.signRequest()} does, so it can be compared against what
     * the request under test actually sent.
     *
     * @param timestamp the {@code X-TIMESTAMP} header value the request carried
     * @param apiKey the API key the signature should have been computed over
     * @param secretKey this fake's current secret key, used as the HMAC signing key
     * @return the expected signature, or {@code null} if {@code timestamp} is missing/malformed
     */
    private @Nullable String computeHmac(@Nullable String timestamp, String apiKey, String secretKey) {
        if (timestamp == null) {
            return null;
        }
        try {
            String dataToSign = timestamp + "." + serialId + "." + apiKey;
            return DiagralCryptoUtil.hmacSha256(dataToSign, secretKey);
        } catch (DiagralException e) {
            return null;
        }
    }

    /**
     * The JSON shape {@code activate_group}/{@code disable_group} request bodies must match:
     * {@code {"groups":[<int>,...]}}. A plain field-holder rather than a record so Gson can populate it
     * reflectively without needing the record adapter, matching the style of this bundle's other DTOs.
     */
    @NonNullByDefault
    private static final class GroupsPayload {
        private @Nullable List<Integer> groups;
    }

    /**
     * Identifies one device by product type + per-category index, exactly the two values
     * {@code DiagralHttpClient.actionProduct()}/{@code isDeviceInhibited()} use to correlate an
     * enable/disable command with its {@code anomalies} entry.
     *
     * @param type the product type ({@code DiagralBindingConstants.PRODUCT_TYPE_*})
     * @param productId the per-category numeric device index
     */
    private record DeviceKey(String type, int productId) {
    }

    /** Which kind of fault {@link #injectedFaults} should produce for the request it applies to. */
    private enum FaultKind {
        TIMEOUT,
        EXECUTION_FAILURE,
        HTTP_STATUS
    }

    /**
     * One queued fault, consumed by exactly one upcoming request regardless of which endpoint it targets -
     * see {@link #injectTimeout()}/{@link #injectConnectionFailure()}/{@link #injectHttpStatus(int)}.
     *
     * @param kind which kind of fault to produce
     * @param httpStatus the status code to return, only meaningful for {@link FaultKind#HTTP_STATUS}
     */
    private record Fault(FaultKind kind, int httpStatus) {
    }

    /**
     * One request in progress: accumulates method/headers/body as {@code DiagralHttpClient} builds it, so
     * {@link #route} can inspect the complete request once {@code send()} is finally called.
     */
    @NonNullByDefault
    private static final class PendingRequest {
        private final String url;
        private String method = "GET";
        private final Map<String, String> headers = new LinkedHashMap<>();
        private String body = "";

        private PendingRequest(String url) {
            this.url = url;
        }

        /**
         * Freezes this in-progress request into an immutable {@link RecordedRequest} for the request log.
         *
         * @return the recorded snapshot of this request
         */
        private RecordedRequest toRecordedRequest() {
            return new RecordedRequest(method, pathOf(url), Map.copyOf(headers), body);
        }
    }

    /**
     * An immutable snapshot of one request this fake answered, exposed via {@link #getRequestLog()} so
     * tests can assert on what was actually sent (e.g. that a PIN code header was attached, or that a
     * group-activation payload had the expected shape).
     *
     * @param method the HTTP method
     * @param path the request path relative to the API base URL
     * @param headers every header the request carried
     * @param body the request body, or an empty string for a bodyless request
     */
    public record RecordedRequest(String method, String path, Map<String, String> headers, String body) {
    }

    /**
     * The simulated HTTP response {@link #route} produces for one request.
     *
     * @param status the HTTP status code to return
     * @param body the response body to return
     */
    private record FakeResponse(int status, String body) {
    }
}
