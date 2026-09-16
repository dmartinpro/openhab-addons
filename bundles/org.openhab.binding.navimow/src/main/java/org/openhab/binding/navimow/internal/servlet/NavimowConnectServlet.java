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
package org.openhab.binding.navimow.internal.servlet;

import static org.openhab.binding.navimow.internal.NavimowBindingConstants.OAUTH_AUTHORIZE_URL;
import static org.openhab.binding.navimow.internal.NavimowBindingConstants.OAUTH_CLIENT_ID;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.navimow.internal.handler.NavimowAccountHandler;
import org.osgi.service.http.HttpService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link NavimowConnectServlet} implements the browser-facing half of the OAuth2 Authorization
 * Code flow for one Navimow account bridge.
 *
 * <p>
 * Visited with no query parameters, it shows a link to Navimow's hosted login page, using its own
 * full request URL as the {@code redirect_uri}. Segway's authorization server then redirects the
 * user's browser back here with {@code ?code=...} once login succeeds, at which point this servlet
 * hands the code to {@link NavimowAccountHandler#completeAuthorization(String, String)} to complete
 * the token exchange. This mirrors the pattern used by this repository's Netatmo binding
 * ({@code GrantServlet}), adapted from Netatmo's own OAuth app to Navimow's.
 *
 * @author David Martin - Initial contribution
 */
@NonNullByDefault
public class NavimowConnectServlet extends NavimowServlet {

    private static final long serialVersionUID = 1L;
    private static final String CONTENT_TYPE = "text/html;charset=UTF-8";

    private final Logger logger = LoggerFactory.getLogger(NavimowConnectServlet.class);

    /**
     * @param handler the owning account bridge, notified via
     *            {@link NavimowAccountHandler#completeAuthorization(String, String)} once Navimow
     *            redirects back with an authorization code
     * @param httpService the OSGi HTTP service to register this servlet with
     */
    public NavimowConnectServlet(NavimowAccountHandler handler, HttpService httpService) {
        super(handler, httpService, "connect");
    }

    /**
     * Handles all three states of the OAuth2 flow this servlet drives: an initial visit (no query
     * parameters, shows the sign-in link), an error redirect ({@code ?error=...}, shows the failure),
     * and a success redirect ({@code ?code=...}, hands the code to the account handler and shows a
     * closing message).
     */
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        StringBuffer requestUrl = req.getRequestURL();
        if (requestUrl == null) {
            logger.warn("Unexpected: requestUrl is null");
            return;
        }
        String servletBaseUrl = requestUrl.toString();

        String error = req.getParameter("error");
        String code = req.getParameter("code");

        resp.setContentType(CONTENT_TYPE);
        if (error != null) {
            logger.debug("Navimow redirected with an error: {}", error);
            resp.getWriter()
                    .append(renderPage("Authorization failed", "Navimow reported an error: " + escapeHtml(error)));
            return;
        }
        if (code != null) {
            logger.debug("Received Navimow authorization code, completing token exchange");
            handler.completeAuthorization(code, servletBaseUrl);
            resp.getWriter().append(renderPage("Authorization received",
                    "openHAB is completing sign-in. You can close this window and check the bridge Thing's status."));
            return;
        }

        String authorizeUrl = buildAuthorizeUrl(servletBaseUrl);
        resp.getWriter().append(renderPage("Connect to Navimow",
                "<a href=\"" + authorizeUrl + "\">Click here to sign in to your Navimow account</a>"));
    }

    /**
     * @param redirectUri this servlet's own URL, passed back as the OAuth2 {@code redirect_uri} so
     *            Segway's authorization server redirects the browser back here
     * @return the full URL to Navimow's hosted login page for this bridge
     */
    private String buildAuthorizeUrl(String redirectUri) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("channel", "homeassistant");
        params.put("client_id", OAUTH_CLIENT_ID);
        params.put("response_type", "code");
        params.put("redirect_uri", redirectUri);

        String query = params.entrySet().stream()
                .map(entry -> urlEncode(entry.getKey()) + "=" + urlEncode(entry.getValue()))
                .collect(Collectors.joining("&"));
        return OAUTH_AUTHORIZE_URL + "?" + query;
    }

    /**
     * @param value the raw value to encode
     * @return {@code value}, URL-encoded for use in a query string
     */
    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /**
     * @param title the page title, also used as the visible heading
     * @param bodyHtml the body content, inserted verbatim as HTML - by design, since one caller needs
     *            to insert a real {@code <a href="...">} link. Any untrusted value (e.g. a request
     *            parameter) must be passed through {@link #escapeHtml} by the caller first - this
     *            method itself does not sanitize it.
     * @return a minimal styled HTML page
     */
    private String renderPage(String title, String bodyHtml) {
        return "<html><head><title>" + title + "</title></head><body style=\"font-family: sans-serif; padding: 2em;\">"
                + "<h2>" + title + "</h2><p>" + bodyHtml + "</p></body></html>";
    }

    /**
     * @param value untrusted text to make safe for insertion into an HTML text node
     * @return {@code value} with the standard HTML special characters escaped
     */
    private static String escapeHtml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
