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
 * broker connection details for the account's MQTT push channel. Field names are taken from the
 * official {@code navimow-sdk} Python package ({@code mqttHost}, {@code mqttUrl}, {@code userName},
 * {@code pwdInfo}). See {@code NavimowApiClientMqttUserInfoLiveTest} for this shape's live
 * confirmation status against a real response.
 *
 * <p>
 * Not currently used anywhere in this binding - it exists to make the endpoint testable in
 * isolation, ahead of any actual MQTT support (this binding is REST-only for now).
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
