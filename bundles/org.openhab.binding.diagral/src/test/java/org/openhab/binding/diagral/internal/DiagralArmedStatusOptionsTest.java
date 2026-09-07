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
package org.openhab.binding.diagral.internal;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.Test;

/**
 * Guards finding C7: every {@code status} value the API can report must be declared as an option on the
 * read-only {@code armed-status} channel, or it reaches the user as a raw unlabelled string.
 *
 * <p>
 * The underlying failure was drift - the option list declared only the five sendable named modes while the
 * API also reports four transitional/resting values (and, found while implementing, a fifth: {@code
 * LEARNING_MODE}). These tests tie {@code thing-types.xml} and the i18n bundle back to
 * {@link DiagralBindingConstants#ALL_SYSTEM_STATUSES}, so adding a newly-observed status without declaring
 * it fails the build rather than quietly degrading the UI.
 * </p>
 *
 * @author David Martin - Initial contribution
 */
@NonNullByDefault
public class DiagralArmedStatusOptionsTest {

    private static final Path THING_TYPES = Path.of("src/main/resources/OH-INF/thing/thing-types.xml");
    private static final Path I18N = Path.of("src/main/resources/OH-INF/i18n/diagral.properties");

    /**
     * Extracts the option values declared on one channel-type.
     *
     * @param channelTypeId the channel-type id to read
     * @return the declared option values, in document order
     */
    private static Set<String> declaredOptions(String channelTypeId) throws IOException {
        String xml = Files.readString(THING_TYPES);
        Matcher block = Pattern
                .compile("<channel-type id=\"" + channelTypeId + "\">(.*?)</channel-type>", Pattern.DOTALL)
                .matcher(xml);
        assertThat("channel-type " + channelTypeId + " not found", block.find(), is(true));

        Set<String> values = new LinkedHashSet<>();
        Matcher option = Pattern.compile("<option value=\"([^\"]+)\">").matcher(block.group(1));
        while (option.find()) {
            values.add(option.group(1));
        }
        return values;
    }

    /**
     * C7: armed-status must declare every status the binding can publish - the five named modes plus the
     * transitional and resting values the API also reports.
     */
    @Test
    public void armedStatusDeclaresEveryObservableStatus() throws IOException {
        Set<String> declared = declaredOptions("armed-status");

        assertThat(declared, hasItems(DiagralBindingConstants.ALL_SYSTEM_STATUSES.toArray(new String[0])));
        assertThat("an undeclared status renders as a raw string in the UI", declared,
                hasSize(DiagralBindingConstants.ALL_SYSTEM_STATUSES.size()));
    }

    /**
     * The reverse direction: armed-status must not declare a value the binding can never publish, which
     * would advertise a state that does not exist.
     */
    @Test
    public void armedStatusDeclaresNothingUnknown() throws IOException {
        assertThat(declaredOptions("armed-status"), everyItem(is(in(DiagralBindingConstants.ALL_SYSTEM_STATUSES))));
    }

    /**
     * mode-control is the commandable channel and must stay limited to the five sendable named modes.
     * {@code GROUP}, {@code LEARNING_MODE} and the {@code TEMPO_*} values are observed-only - offering
     * them as commands would present the user with arming modes the API cannot accept.
     */
    @Test
    public void modeControlOffersOnlyTheSendableNamedModes() throws IOException {
        assertThat(declaredOptions("mode-control"), is(DiagralBindingConstants.NAMED_SYSTEM_MODES));
    }

    /** Every declared option needs a translated label, or the i18n bundle silently falls out of date. */
    @Test
    public void everyArmedStatusOptionHasAnI18nLabel() throws IOException {
        String properties = Files.readString(I18N);

        for (String value : declaredOptions("armed-status")) {
            assertThat("missing i18n label for " + value, properties,
                    containsString("channel-type.diagral.armed-status.state.option." + value + " = "));
        }
    }
}
