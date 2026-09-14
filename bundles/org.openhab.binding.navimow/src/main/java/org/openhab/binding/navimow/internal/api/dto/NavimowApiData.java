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
package org.openhab.binding.navimow.internal.api.dto;

import org.eclipse.jdt.annotation.Nullable;

/**
 * {@link NavimowApiData} is the inner {@code data} object of {@link NavimowApiEnvelope}, itself
 * always wrapping a further {@code payload} object rather than carrying fields directly.
 *
 * @param <T> the endpoint-specific shape of {@code payload}
 * @author David Martin - Initial contribution
 */
public class NavimowApiData<T> {

    public @Nullable T payload;
}
