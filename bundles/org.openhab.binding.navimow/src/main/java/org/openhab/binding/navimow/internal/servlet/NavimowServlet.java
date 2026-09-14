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

import static org.openhab.binding.navimow.internal.NavimowBindingConstants.BINDING_ID;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.navimow.internal.handler.NavimowAccountHandler;
import org.osgi.service.http.HttpService;
import org.osgi.service.http.NamespaceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link NavimowServlet} is the ancestor class for Navimow servlets, registering itself with
 * the OSGi {@link HttpService} at a path scoped to both the binding and the owning bridge Thing, so
 * multiple Navimow account bridges never collide on the same URL.
 *
 * @author David Martin - Initial contribution
 */
@NonNullByDefault
public abstract class NavimowServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private final Logger logger = LoggerFactory.getLogger(NavimowServlet.class);
    private final HttpService httpService;
    private final String path;

    protected final NavimowAccountHandler handler;

    protected NavimowServlet(NavimowAccountHandler handler, HttpService httpService, String localPath) {
        this.handler = handler;
        this.httpService = httpService;
        this.path = "/" + BINDING_ID + "/" + localPath + "/" + handler.getThing().getUID().getAsString();
    }

    /**
     * Registers this servlet with the OSGi HTTP service so it starts receiving requests.
     */
    public void startListening() {
        try {
            httpService.registerServlet(path, this, null, httpService.createDefaultHttpContext());
            logger.debug("Registered Navimow servlet at '{}'", path);
        } catch (NamespaceException | ServletException e) {
            logger.warn("Registering Navimow servlet failed: {}", e.getMessage());
        }
    }

    /**
     * Unregisters this servlet and releases its resources. Safe to call more than once.
     */
    public void dispose() {
        httpService.unregister(path);
        this.destroy();
    }

    public String getPath() {
        return path;
    }
}
