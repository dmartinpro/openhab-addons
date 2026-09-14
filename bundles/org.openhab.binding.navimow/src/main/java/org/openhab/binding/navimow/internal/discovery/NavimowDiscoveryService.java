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
package org.openhab.binding.navimow.internal.discovery;

import static org.openhab.binding.navimow.internal.NavimowBindingConstants.THING_TYPE_ACCOUNT;
import static org.openhab.binding.navimow.internal.NavimowBindingConstants.THING_TYPE_MOWER;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.navimow.internal.api.dto.NavimowDevice;
import org.openhab.binding.navimow.internal.api.exceptions.NavimowException;
import org.openhab.binding.navimow.internal.handler.NavimowAccountHandler;
import org.openhab.core.config.discovery.AbstractThingHandlerDiscoveryService;
import org.openhab.core.config.discovery.DiscoveryResult;
import org.openhab.core.config.discovery.DiscoveryResultBuilder;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.ThingUID;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ServiceScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link NavimowDiscoveryService} discovers {@code mower} things from the account bridge's
 * cached device list ({@code authList}).
 *
 * @author David Martin - Initial contribution
 */
@Component(scope = ServiceScope.PROTOTYPE, service = NavimowDiscoveryService.class)
@NonNullByDefault
public class NavimowDiscoveryService extends AbstractThingHandlerDiscoveryService<NavimowAccountHandler> {

    private static final int TIMEOUT_SECONDS = 30;

    private final Logger logger = LoggerFactory.getLogger(NavimowDiscoveryService.class);

    public NavimowDiscoveryService() {
        super(NavimowAccountHandler.class, Set.of(THING_TYPE_ACCOUNT), TIMEOUT_SECONDS, false);
    }

    @Override
    public Set<ThingTypeUID> getSupportedThingTypes() {
        return Set.of(THING_TYPE_MOWER);
    }

    @Override
    public void startScan() {
        try {
            List<NavimowDevice> devices = thingHandler.getDevices();
            ThingUID bridgeUID = thingHandler.getThing().getUID();

            for (NavimowDevice device : devices) {
                String id = device.id;
                if (id == null || id.isBlank()) {
                    logger.debug("Skipping device with no id in authList response");
                    continue;
                }

                ThingUID thingUID = new ThingUID(THING_TYPE_MOWER, bridgeUID, id);
                DiscoveryResult result = DiscoveryResultBuilder.create(thingUID).withBridge(bridgeUID)
                        .withProperty("id", id).withRepresentationProperty("id")
                        .withLabel(device.name != null && !device.name.isBlank() ? device.name : "Navimow Mower")
                        .build();
                thingDiscovered(result);
            }
        } catch (NavimowException e) {
            logger.debug("Failed to discover mowers: {}", e.getMessage());
        }
    }

    @Override
    protected synchronized void stopScan() {
        super.stopScan();
        removeOlderResults(getTimestampOfLastScan());
    }

    @Override
    public void dispose() {
        super.dispose();
        removeOlderResults(Instant.now());
    }
}
