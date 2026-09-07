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
package org.openhab.binding.diagral.internal.discovery;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.openhab.binding.diagral.internal.DiagralBindingConstants.*;

import java.util.Locale;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.junit.jupiter.api.Test;
import org.openhab.binding.diagral.internal.dto.DiagralDevice;
import org.openhab.core.thing.ThingTypeUID;

/**
 * Unit tests for the per-device thing-type resolution in {@link DiagralDiscoveryService}, which decides
 * whether a device in the API's {@code sensors} list becomes a motion or contact sensor, and whether a
 * {@code transmitters} entry becomes a plug or a generic transmitter.
 *
 * <p>
 * This logic had no coverage, and it is what decides which handler - and therefore which channels - a
 * discovered device ends up with. It is also the part the D2 refactor moved.
 * </p>
 *
 * @author David Martin - Initial contribution
 */
@NonNullByDefault
public class DiagralDeviceClassificationTest {

    /**
     * Builds a device with the given type and reference codes.
     *
     * @param type the API {@code type} code
     * @param refCode the API {@code refCode}
     * @return the device
     */
    private static DiagralDevice device(@Nullable String type, @Nullable String refCode) {
        DiagralDevice device = new DiagralDevice();
        device.type = type;
        device.refCode = refCode;
        return device;
    }

    /**
     * Classifies a device.
     *
     * @param device the device to classify
     * @return the resolved thing type, or null
     */
    private static @Nullable ThingTypeUID thingTypeFor(DiagralDevice device) {
        return DiagralDiscoveryService.getThingTypeForDevice(device);
    }

    /** The three known volumetric reference codes are motion sensors. */
    @Test
    public void knownVolumetricCodesBecomeMotionSensors() {
        for (String refCode : new String[] { DEVICE_DIAG20AVK_CODE, DEVICE_DIAG21AVK_CODE, DEVICE_DIAG36APX_CODE }) {
            assertThat("refCode " + refCode, thingTypeFor(device(DEVICE_SENSOR_TYPE, refCode)),
                    is(THING_TYPE_MOTION_SENSOR));
        }
    }

    /** The opening detector is a contact sensor. */
    @Test
    public void openingDetectorBecomesAContactSensor() {
        assertThat(thingTypeFor(device(DEVICE_SENSOR_TYPE, DEVICE_DIAG30APK_CODE)), is(THING_TYPE_CONTACT_SENSOR));
    }

    /** refCode matching ignores case, since the API's casing is not guaranteed. */
    @Test
    public void refCodeMatchingIsCaseInsensitive() {
        assertThat(thingTypeFor(device(DEVICE_SENSOR_TYPE, DEVICE_DIAG30APK_CODE.toLowerCase(Locale.ROOT))),
                is(THING_TYPE_CONTACT_SENSOR));
    }

    /**
     * An unrecognized refCode still becomes a motion sensor. That default is deliberate: an unknown
     * sensor is more useful discovered with the wrong sub-type than not discovered at all.
     */
    @Test
    public void unknownRefCodeDefaultsToMotionSensor() {
        assertThat(thingTypeFor(device(DEVICE_SENSOR_TYPE, "9999")), is(THING_TYPE_MOTION_SENSOR));
        assertThat(thingTypeFor(device(DEVICE_SENSOR_TYPE, null)), is(THING_TYPE_MOTION_SENSOR));
    }

    /** A device with no type at all cannot be classified and is skipped. */
    @Test
    public void missingTypeIsNotClassified() {
        assertThat(thingTypeFor(device(null, DEVICE_DIAG30APK_CODE)), is(nullValue()));
    }

    /** A type code the sensors list is not expected to carry is skipped rather than guessed at. */
    @Test
    public void unexpectedTypeIsNotClassified() {
        assertThat(thingTypeFor(device("7", DEVICE_DIAG30APK_CODE)), is(nullValue()));
    }
}
