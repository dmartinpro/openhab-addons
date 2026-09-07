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
package org.openhab.binding.diagral.internal.dto;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.junit.jupiter.api.Test;
import org.openhab.core.thing.ThingUID;

/**
 * Unit tests for {@link DiagralAlarm#getId()}, the source of finding C6: it can legitimately return
 * {@code null} for a partial API response, and that value used to reach {@code new ThingUID(...)}
 * unchecked - throwing from the constructor and aborting the entire discovery scan, taking every other
 * device down with the alarm system.
 *
 * @author David Martin - Initial contribution
 */
@NonNullByDefault
public class DiagralAlarmTest {

    /**
     * Builds an alarm whose box carries the given serial.
     *
     * @param serial the box serial number, possibly null
     * @return the alarm
     */
    private static DiagralAlarm alarmWithBoxSerial(@Nullable String serial) {
        DiagralAlarm alarm = new DiagralAlarm();
        DiagralAlarm.Device box = new DiagralAlarm.Device();
        box.serial = serial;
        alarm.box = box;
        return alarm;
    }

    /** The happy path: the id is the serial's first six characters. */
    @Test
    public void idIsDerivedFromTheBoxSerial() {
        assertThat(alarmWithBoxSerial("22309037088F21").getId(), is("223090"));
    }

    /** C6: no box at all - the API can omit it - must yield null rather than throwing. */
    @Test
    public void missingBoxYieldsNoId() {
        assertThat(new DiagralAlarm().getId(), is(nullValue()));
    }

    /** C6: a box with no serial must yield null. */
    @Test
    public void missingSerialYieldsNoId() {
        assertThat(alarmWithBoxSerial(null).getId(), is(nullValue()));
    }

    /** C6: a serial too short to slice must yield null rather than throwing IndexOutOfBounds. */
    @Test
    public void shortSerialYieldsNoId() {
        assertThat(alarmWithBoxSerial("12345").getId(), is(nullValue()));
        assertThat(alarmWithBoxSerial("").getId(), is(nullValue()));
    }

    /** Exactly six characters is the boundary and is usable. */
    @Test
    public void sixCharacterSerialIsUsable() {
        assertThat(alarmWithBoxSerial("123456").getId(), is("123456"));
    }

    /**
     * C6, the other half: {@code ThingUID} rejects a segment containing characters outside letters,
     * digits and underscores. This pins why discovery sanitises every id before building a UID rather
     * than trusting the API to return something acceptable.
     */
    @Test
    public void thingUidRejectsUnsanitisedSegments() {
        assertThat("ThingUID should reject a colon in a segment", rejectsSegment("22:309"), is(true));

        String sanitised = "22:309".replaceAll("[^a-zA-Z0-9_]", "_");
        ThingUID uid = new ThingUID("diagral", "alarm-system", sanitised);
        assertThat(uid.getId(), is("22_309"));
    }

    /**
     * Reports whether building a {@link ThingUID} with the given segment is rejected.
     *
     * @param segment the raw segment to try
     * @return {@code true} if the constructor rejected it
     */
    private static boolean rejectsSegment(String segment) {
        try {
            new ThingUID("diagral", "alarm-system", segment);
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        }
    }
}
