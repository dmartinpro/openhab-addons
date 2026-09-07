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
package org.openhab.binding.diagral.internal.bridge;

import java.util.function.Supplier;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.diagral.internal.dto.DiagralAnomalies;
import org.openhab.binding.diagral.internal.dto.DiagralSystemConfiguration;
import org.openhab.binding.diagral.internal.dto.DiagralSystemStatus;

/**
 * One consistent view of the Diagral system, shared by every thing handler refreshed in the same cycle.
 *
 * <p>
 * Replaces the arrangement where each handler independently asked the bridge for status, configuration
 * and anomalies while a short-lived cache tried to make those calls agree. That did not hold: one slow
 * handler earlier in the loop could let the cache lapse for every handler after it (live-observed, an
 * {@code /anomalies} call taking over six seconds), so handlers in a single cycle could act on different
 * views of the system - a group reading as armed while the alarm system already read as disarmed. The
 * bridge now builds one snapshot per cycle and hands the same instance to everyone, which makes that
 * class of inconsistency structurally impossible rather than merely unlikely.
 * </p>
 *
 * <p>
 * {@link #status()} and {@link #configuration()} are resolved when the snapshot is created - the bridge
 * has just fetched or cached both. {@link #anomalies()} is resolved on first use and then memoised,
 * because it is an uncached HTTP call that only the alarm-system handler reads: resolving it eagerly
 * would make every sensor and group refresh pay for a response it never looks at.
 * </p>
 *
 * @author David Martin - Initial contribution
 */
@NonNullByDefault
public final class DiagralPollSnapshot {

    private final @Nullable DiagralSystemStatus status;
    private final @Nullable DiagralSystemConfiguration configuration;
    private final Supplier<@Nullable DiagralAnomalies> anomaliesSupplier;

    private volatile boolean anomaliesResolved;
    private volatile @Nullable DiagralAnomalies anomalies;

    /**
     * Creates a snapshot.
     *
     * @param status the system status for this cycle, or {@code null} if it could not be fetched
     * @param configuration the system configuration for this cycle, or {@code null} if unavailable
     * @param anomaliesSupplier resolves the anomalies on first use; called at most once
     */
    public DiagralPollSnapshot(@Nullable DiagralSystemStatus status, @Nullable DiagralSystemConfiguration configuration,
            Supplier<@Nullable DiagralAnomalies> anomaliesSupplier) {
        this.status = status;
        this.configuration = configuration;
        this.anomaliesSupplier = anomaliesSupplier;
    }

    /**
     * Gets the system status this cycle is working from.
     *
     * @return the status, or {@code null} if it could not be fetched for this cycle
     */
    public @Nullable DiagralSystemStatus status() {
        return status;
    }

    /**
     * Gets the system configuration this cycle is working from.
     *
     * @return the configuration, or {@code null} if none is available yet
     */
    public @Nullable DiagralSystemConfiguration configuration() {
        return configuration;
    }

    /**
     * Gets the anomalies for this cycle, fetching them on first call and reusing that result afterwards.
     *
     * <p>
     * Double-checked against {@link #anomaliesResolved} so the underlying HTTP call happens at most once
     * per snapshot even when several handlers ask concurrently - which they can, since lifecycle-driven
     * refreshes run on the scheduler.
     * </p>
     *
     * @return the anomalies, or {@code null} if they could not be fetched
     */
    public @Nullable DiagralAnomalies anomalies() {
        if (!anomaliesResolved) {
            synchronized (this) {
                if (!anomaliesResolved) {
                    anomalies = anomaliesSupplier.get();
                    anomaliesResolved = true;
                }
            }
        }
        return anomalies;
    }
}
