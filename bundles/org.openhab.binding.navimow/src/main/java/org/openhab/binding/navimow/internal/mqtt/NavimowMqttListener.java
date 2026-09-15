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
package org.openhab.binding.navimow.internal.mqtt;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.navimow.internal.mqtt.dto.MqttVehicleState;

/**
 * {@link NavimowMqttListener} receives parsed push updates from {@link NavimowMqttConnection}, one
 * call per MQTT message. Implemented by {@code NavimowAccountHandler} so it can route each update to
 * the right {@code NavimowMowerHandler} by device id, the same way {@code poll()} already does for
 * REST status snapshots.
 *
 * <p>
 * Callbacks arrive on the MQTT client's own internal thread, never on the openHAB scheduler - the
 * implementation must not block.
 *
 * @author David Martin - Initial contribution
 */
@NonNullByDefault
public interface NavimowMqttListener {

    /**
     * Called once per {@code state} topic message received for a device.
     *
     * @param deviceId the mower this update is for
     * @param state the parsed message payload
     */
    void onVehicleState(String deviceId, MqttVehicleState state);
}
