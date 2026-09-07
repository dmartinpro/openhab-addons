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

import java.util.List;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link DiagralAnomalies#getTotalCount()}, which drives the alarm system's
 * {@code anomaly-count} and {@code anomalies-present} channels.
 *
 * <p>
 * The distinction that matters here: a {@code null} category means nothing in that category has an
 * anomaly, not that the category is unknown. Counting it as anything but zero would misreport system
 * health.
 * </p>
 *
 * @author David Martin - Initial contribution
 */
@NonNullByDefault
public class DiagralAnomaliesTest {

    /**
     * Builds an anomaly detail carrying one named anomaly.
     *
     * @param name the anomaly name
     * @return the detail
     */
    private static DiagralAnomalyDetail detail(String name) {
        DiagralAnomalyDetail detail = new DiagralAnomalyDetail();
        DiagralAnomalyName anomalyName = new DiagralAnomalyName();
        anomalyName.name = name;
        detail.anomalyNames = List.of(anomalyName);
        return detail;
    }

    /** A fresh instance - every category null - reports zero, not a null-pointer failure. */
    @Test
    public void emptyAnomaliesCountZero() {
        assertThat(new DiagralAnomalies().getTotalCount(), is(0));
    }

    /** Every category contributes to the total. */
    @Test
    public void everyCategoryIsCounted() {
        DiagralAnomalies anomalies = new DiagralAnomalies();
        anomalies.sensors = List.of(detail("a"), detail("b"));
        anomalies.badges = List.of(detail("c"));
        anomalies.sirens = List.of(detail("d"));
        anomalies.cameras = List.of(detail("e"));
        anomalies.commands = List.of(detail("f"));
        anomalies.transceivers = List.of(detail("g"));
        anomalies.transmitters = List.of(detail("h"));
        anomalies.central = List.of(detail("i"));

        assertThat(anomalies.getTotalCount(), is(9));
    }

    /** A populated category alongside null ones counts only what is there. */
    @Test
    public void nullCategoriesContributeNothing() {
        DiagralAnomalies anomalies = new DiagralAnomalies();
        anomalies.sensors = List.of(detail("powerSupplyAlert"));

        assertThat(anomalies.getTotalCount(), is(1));
    }

    /** An explicitly empty category is also zero. */
    @Test
    public void emptyCategoryContributesNothing() {
        DiagralAnomalies anomalies = new DiagralAnomalies();
        anomalies.sensors = List.of();

        assertThat(anomalies.getTotalCount(), is(0));
    }
}
