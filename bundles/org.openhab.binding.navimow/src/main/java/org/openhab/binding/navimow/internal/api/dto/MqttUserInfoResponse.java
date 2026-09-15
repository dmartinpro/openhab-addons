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
 * {@link MqttUserInfoResponse} is the top-level response envelope for {@code mqtt/userInfo/get/v2}.
 * Unlike the "smarthome" endpoints, {@code data} here carries the payload fields directly rather
 * than nesting a further {@code payload} object underneath - confirmed from the official
 * {@code navimow-sdk} Python package's {@code async_get_mqtt_user_info}, which returns
 * {@code response.get("data", {})} with no further unwrapping. This is why this endpoint gets its
 * own dedicated envelope instead of reusing {@link NavimowApiEnvelope}.
 *
 * @author David Martin - Initial contribution
 */
public class MqttUserInfoResponse {

    public int code;

    public @Nullable String desc;

    public @Nullable MqttUserInfo data;

    /**
     * @return whether the response indicates success (business code 1), not just an HTTP 2xx
     */
    public boolean isSuccess() {
        return code == 1;
    }
}
