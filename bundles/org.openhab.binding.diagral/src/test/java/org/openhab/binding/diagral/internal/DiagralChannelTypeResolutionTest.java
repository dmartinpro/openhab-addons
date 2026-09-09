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

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.Test;
import org.openhab.core.thing.DefaultSystemChannelTypeProvider;
import org.openhab.core.thing.type.ChannelTypeUID;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Guards the class of bug found in {@code contact-sensor}: a {@code <channel typeId="...">} reference that
 * looks plausible but does not resolve to any real channel type, so the framework silently drops the
 * channel at runtime instead of failing loudly.
 *
 * <p>
 * {@code contact-sensor} declared {@code typeId="system.contact"}, but core's
 * {@link DefaultSystemChannelTypeProvider} (confirmed here by reflecting its actual
 * {@code SYSTEM_CHANNEL_TYPE_UID_*} constants, not by assumption) has never defined a {@code contact}
 * system channel type - only {@code motion} and others. Every {@code system.*} reference is checked against
 * that real set, and every non-{@code system.*} reference is checked against the channel-types this bundle
 * itself declares in {@code thing-types.xml}, so a typo or a reference to a channel type that does not
 * exist anywhere fails the build instead of quietly producing a Thing with a missing channel.
 * </p>
 *
 * @author David Martin - Initial contribution
 */
@NonNullByDefault
public class DiagralChannelTypeResolutionTest {

    private static final File THING_TYPES = new File("src/main/resources/OH-INF/thing/thing-types.xml");

    /**
     * Reflects every real system channel-type id from core's {@link DefaultSystemChannelTypeProvider}, by
     * reading its {@code public static final ChannelTypeUID SYSTEM_CHANNEL_TYPE_UID_*} constants - the
     * same technique used to originally confirm {@code system.contact} does not exist.
     *
     * @return the set of ids (without the {@code system.} prefix) that core actually defines
     */
    private static Set<String> realSystemChannelTypeIds() throws Exception {
        Set<String> ids = new HashSet<>();
        for (Field field : DefaultSystemChannelTypeProvider.class.getFields()) {
            if (Modifier.isStatic(field.getModifiers()) && ChannelTypeUID.class.isAssignableFrom(field.getType())) {
                ChannelTypeUID uid = (ChannelTypeUID) field.get(null);
                ids.add(uid.getId());
            }
        }
        return ids;
    }

    /**
     * Parses {@code thing-types.xml} and returns every {@code <channel typeId="...">} reference, keyed by
     * "{@code thing-type-id/channel-id}", plus the set of {@code <channel-type id="...">} this bundle
     * itself declares.
     */
    private static Document parseThingTypes() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(THING_TYPES);
    }

    private static Set<String> declaredChannelTypeIds(Document doc) {
        Set<String> ids = new HashSet<>();
        NodeList channelTypes = doc.getElementsByTagName("channel-type");
        for (int i = 0; i < channelTypes.getLength(); i++) {
            Element element = (Element) channelTypes.item(i);
            ids.add(element.getAttribute("id"));
        }
        return ids;
    }

    private static Map<String, String> channelTypeReferences(Document doc) {
        Map<String, String> references = new LinkedHashMap<>();
        NodeList thingTypes = doc.getElementsByTagName("thing-type");
        for (int i = 0; i < thingTypes.getLength(); i++) {
            Element thingType = (Element) thingTypes.item(i);
            String thingTypeId = thingType.getAttribute("id");
            NodeList channels = thingType.getElementsByTagName("channel");
            for (int j = 0; j < channels.getLength(); j++) {
                Element channel = (Element) channels.item(j);
                references.put(thingTypeId + "/" + channel.getAttribute("id"), channel.getAttribute("typeId"));
            }
        }
        return references;
    }

    /**
     * Every {@code system.*} channel typeId referenced anywhere in {@code thing-types.xml} must correspond
     * to a real system channel type. This is the exact regression that let {@code system.contact} through
     * silently.
     */
    @Test
    public void everySystemChannelTypeReferenceIsReal() throws Exception {
        Set<String> realIds = realSystemChannelTypeIds();
        Map<String, String> references = channelTypeReferences(parseThingTypes());

        for (Map.Entry<String, String> entry : references.entrySet()) {
            String typeId = entry.getValue();
            if (typeId.startsWith("system.")) {
                String systemId = typeId.substring("system.".length());
                assertThat("channel " + entry.getKey() + " references non-existent system channel type " + typeId,
                        systemId, is(in(realIds)));
            }
        }
    }

    /**
     * Every non-{@code system.*} channel typeId must resolve to a {@code <channel-type>} declared in this
     * same file, or the framework drops the channel with no build-time warning.
     */
    @Test
    public void everyBindingChannelTypeReferenceIsDeclared() throws Exception {
        Document doc = parseThingTypes();
        Set<String> declared = declaredChannelTypeIds(doc);
        Map<String, String> references = channelTypeReferences(doc);

        for (Map.Entry<String, String> entry : references.entrySet()) {
            String typeId = entry.getValue();
            if (!typeId.startsWith("system.")) {
                assertThat("channel " + entry.getKey() + " references undeclared channel type " + typeId, typeId,
                        is(in(declared)));
            }
        }
    }

    /**
     * Regression guard for the specific bug: {@code contact-sensor}'s {@code contact} channel must be
     * binding-owned, item-type {@code Contact}, and must never again point at the non-existent
     * {@code system.contact}.
     */
    @Test
    public void contactSensorChannelIsBindingOwnedContactType() throws Exception {
        Document doc = parseThingTypes();
        Map<String, String> references = channelTypeReferences(doc);

        String typeId = references.get("contact-sensor/contact");
        assertThat("contact-sensor's contact channel must exist", typeId, is(notNullValue()));
        assertThat("must not reference the non-existent system.contact channel type", typeId,
                is(not("system.contact")));
        assertThat(typeId, is("contact"));

        NodeList channelTypes = doc.getElementsByTagName("channel-type");
        Element contactType = null;
        for (int i = 0; i < channelTypes.getLength(); i++) {
            Element element = (Element) channelTypes.item(i);
            if ("contact".equals(element.getAttribute("id"))) {
                contactType = element;
                break;
            }
        }
        assertThat("channel-type 'contact' must be declared", contactType, is(notNullValue()));

        NodeList itemTypes = contactType.getElementsByTagName("item-type");
        assertThat(itemTypes.getLength(), is(1));
        assertThat(itemTypes.item(0).getTextContent(), is("Contact"));

        NodeList states = contactType.getElementsByTagName("state");
        assertThat(states.getLength(), is(1));
        assertThat(((Element) states.item(0)).getAttribute("readOnly"), is("true"));
    }
}
