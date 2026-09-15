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
 * {@link NavimowApiResponseBase} is the {@code code}/{@code desc} pair common to every response
 * envelope this binding parses, and the single definition of what counts as success (business code
 * {@code 1}). Shared by {@link NavimowApiEnvelope} and {@link MqttUserInfoResponse}, which otherwise
 * differ only in how they nest their payload under {@code data} - keeping the shared part in one
 * place means {@link org.openhab.binding.navimow.internal.api.NavimowApiClient}'s business-error
 * classification only needs to exist once, for every endpoint.
 *
 * @author David Martin - Initial contribution
 */
public abstract class NavimowApiResponseBase {

    public int code;

    public @Nullable String desc;

    /**
     * @return whether the response indicates success (business code 1), not just an HTTP 2xx
     */
    public boolean isSuccess() {
        return code == 1;
    }
}
