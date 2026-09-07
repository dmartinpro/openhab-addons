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
package org.openhab.binding.diagral.internal.handler;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.diagral.internal.bridge.DiagralBridgeHandler;
import org.openhab.binding.diagral.internal.bridge.DiagralPollSnapshot;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.ThingStatusInfo;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.thing.binding.ThingHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for every non-bridge Diagral thing handler - the alarm system, device groups, and all
 * device types via {@link DiagralSensorHandler}.
 *
 * <p>
 * Holds the behaviour those handlers share and previously each carried their own byte-identical copy of:
 * looking the parent bridge handler up ({@link #getBridgeHandler()}), mirroring the bridge's status onto
 * this thing ({@link #bridgeStatusChanged(ThingStatusInfo)}), and deciding whether this thing can go
 * online at all ({@link #goOnlineIfBridgeAvailable()}).
 * </p>
 *
 * <p>
 * It also owns the rule that channel refreshes triggered by a <em>lifecycle</em> callback run off the
 * calling thread - see {@link #refreshStatusAsync()}. That is the point of this class existing rather
 * than the duplication merely being tidied away: the guard it needs (not touching a disposed handler)
 * has to live somewhere shared, and having one copy of it is what makes the rule enforceable.
 * </p>
 *
 * @author David Martin - Initial contribution
 */
@NonNullByDefault
public abstract class DiagralBaseThingHandler extends BaseThingHandler implements DiagralRefreshableHandler {

    private final Logger baseLogger = LoggerFactory.getLogger(DiagralBaseThingHandler.class);

    /**
     * Set once {@link #dispose()} has run, so a refresh already queued by {@link #refreshStatusAsync()}
     * skips instead of publishing channel states through a handler the framework has already torn down
     * (which {@code BaseThingHandler.updateState} would otherwise log a warning for).
     *
     * <p>
     * Cleared by {@link #goOnlineIfBridgeAvailable()} rather than by {@code initialize()}: the framework
     * reuses handler instances - {@code BaseThingHandler.thingUpdated()} calls {@code dispose()} then
     * {@code initialize()} on this same object when a thing's configuration is edited - so a flag that
     * only ever got set would permanently disable refreshes after the first config change. Resetting it
     * at the single point where a handler becomes active covers every path that can then schedule one.
     * </p>
     */
    private volatile boolean disposed;

    /**
     * Constructs a new handler.
     *
     * @param thing the thing to handle
     */
    protected DiagralBaseThingHandler(Thing thing) {
        super(thing);
    }

    /**
     * Marks this handler as disposed so any queued asynchronous refresh becomes a no-op.
     */
    @Override
    public void dispose() {
        disposed = true;
        super.dispose();
    }

    /**
     * Brings this thing {@code ONLINE} if its bridge exists and is itself online, and reports whether it
     * did.
     *
     * <p>
     * Also clears {@link #disposed}, since this is the single point at which a handler becomes active -
     * see that field's Javadoc for why the reset belongs here rather than in {@code initialize()}.
     * </p>
     *
     * @return {@code true} if this thing is now {@code ONLINE} and may refresh its channels;
     *         {@code false} if it was set {@code OFFLINE} instead (no bridge, or the bridge is offline)
     */
    protected boolean goOnlineIfBridgeAvailable() {
        Bridge bridge = getBridge();
        if (bridge == null) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "No bridge configured");
            return false;
        }

        if (bridge.getStatus() != ThingStatus.ONLINE) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE);
            return false;
        }

        disposed = false;
        updateStatus(ThingStatus.ONLINE);
        return true;
    }

    /**
     * Runs {@link DiagralRefreshableHandler#refreshStatus(DiagralPollSnapshot)} on the handler's
     * scheduler instead of the calling thread, against a snapshot captured there.
     *
     * <p>
     * Every lifecycle entry point must use this rather than calling {@code refreshStatus()} directly.
     * {@code refreshStatus()} reaches the cloud API - a request this binding gives 10 seconds to
     * complete, against an API that regularly uses all of it - and openHAB's guideline (and this bundle's
     * own {@code CLAUDE.md}) requires {@code initialize()} and {@code bridgeStatusChanged()} to return
     * promptly. Calling it inline is what produced the framework's recurring "Initializing handler for
     * thing ... takes more than 5000ms" warnings.
     * </p>
     *
     * <p>
     * <b>What must not use this:</b> {@code DiagralBridgeHandler.refreshChildHandlers()}, which calls
     * {@code refreshStatus()} directly and synchronously. That runs inside one poll cycle, under the
     * bridge's poll lock and against a single shared status snapshot; dispatching those refreshes
     * asynchronously would break both guarantees. Only lifecycle callbacks are offloaded here.
     * </p>
     */
    protected void refreshStatusAsync() {
        scheduler.execute(() -> {
            if (disposed) {
                return;
            }
            DiagralBridgeHandler bridgeHandler = getBridgeHandler();
            if (bridgeHandler == null) {
                return;
            }
            try {
                refreshStatus(bridgeHandler.captureSnapshot());
            } catch (RuntimeException e) {
                // Never let an exception escape into the scheduler, where it would be swallowed silently.
                baseLogger.warn("Failed to refresh thing {}: {}", getThing().getUID(), e.getMessage());
            }
        });
    }

    /**
     * Gets the parent bridge's handler.
     *
     * @return the bridge handler, or {@code null} if this thing has no bridge, the bridge has no handler
     *         yet, or that handler isn't a {@link DiagralBridgeHandler}
     */
    protected @Nullable DiagralBridgeHandler getBridgeHandler() {
        Bridge bridge = getBridge();
        if (bridge == null) {
            return null;
        }

        ThingHandler handler = bridge.getHandler();
        if (handler instanceof DiagralBridgeHandler bridgeHandler) {
            return bridgeHandler;
        }

        return null;
    }

    /**
     * Mirrors this thing's status to the bridge's status: goes {@code ONLINE} (and refreshes off-thread)
     * when the bridge comes online, goes {@code OFFLINE} otherwise.
     *
     * @param bridgeStatusInfo the bridge's new status
     */
    @Override
    public void bridgeStatusChanged(ThingStatusInfo bridgeStatusInfo) {
        if (goOnlineIfBridgeAvailable()) {
            refreshStatusAsync();
        }
    }
}
