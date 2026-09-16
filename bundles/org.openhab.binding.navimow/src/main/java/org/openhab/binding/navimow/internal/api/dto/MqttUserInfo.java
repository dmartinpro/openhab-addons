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
 * {@link MqttUserInfo} is the {@code data} object of the {@code mqtt/userInfo/get/v2} response:
 * broker connection details for the account's MQTT push channel.
 *
 * <p>
 * <b>Live-confirmed 2026-09-15</b>, with all four fields present in a genuine successful response
 * (called in-process from {@code NavimowAccountHandler}, using the account bridge's own
 * already-bound OAuth session - see {@code NavimowBindingConstants.BUSINESS_CODE_OAUTH_INFO_ILLEGAL}
 * for why every earlier external attempt had failed regardless of endpoint). Observed shape:
 * {@code mqttHost} a {@code wss://} URL, {@code mqttUrl} a path of the form {@code /mqtt/<numeric-id>}.
 *
 * <p>
 * Used by {@link org.openhab.binding.navimow.internal.mqtt.NavimowMqttConnection#connect} to open the
 * optional MQTT push connection - see that class's Javadoc for what the connection is actually good
 * for (push latency, not new data).
 *
 * @author David Martin - Initial contribution
 */
public class MqttUserInfo {

    public @Nullable String mqttHost;

    public @Nullable String mqttUrl;

    public @Nullable String userName;

    /**
     * MQTT password. Despite the odd name (taken verbatim from the reverse-engineered SDK), this is a plain credential
     * - never log it.
     */
    public @Nullable String pwdInfo;
}
