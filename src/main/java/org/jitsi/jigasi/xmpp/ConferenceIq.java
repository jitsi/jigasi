/*
 * Jigasi, the JItsi GAteway to SIP.
 *
 * Copyright @ 2015 Atlassian Pty Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jitsi.jigasi.xmpp;

import net.java.sip.communicator.impl.protocol.jabber.extensions.*;
import org.jivesoftware.smack.packet.*;

import java.util.*;

/**
 * A conference IQ to sent to focus, so jicofo to be able to join the room.
 */
public class ConferenceIq
    extends IQ
{
    /**
     * Focus namespace.
     */
    public final static String NAMESPACE = "http://jitsi.org/protocol/focus";

    /**
     * XML element name for the <tt>ConferenceIq</tt>.
     */
    public static final String ELEMENT_NAME = "conference";

    /**
     * The name of the attribute that stores the name of multi user chat room
     * that is hosting Jitsi Meet conference.
     */
    public static final String ROOM_ATTR_NAME = "room";

    /**
     * The name of the attribute that holds machine unique identifier used to
     * distinguish session for the same user on different machines.
     */
    public static final String MACHINE_UID_ATTR_NAME = "machine-uid";

    /**
     * MUC room name hosting Jitsi Meet conference.
     */
    private String room;

    /**
     * Create conference IQ.
     * @param room the room name.
     */
    public ConferenceIq(String room)
    {
        this.room = room;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getChildElementXML()
    {
        StringBuilder xml = new StringBuilder();

        xml.append("<").append(ELEMENT_NAME);
        xml.append(" xmlns='").append(NAMESPACE).append("' ");

        xml.append(MACHINE_UID_ATTR_NAME).append("='")
            .append(UUID.randomUUID().toString()).append("' ");
        xml.append(ROOM_ATTR_NAME).append("='")
            .append(room).append("' ");

        if (getExtensions().size() == 0)
        {
            xml.append("/>");
        }
        else
        {
            xml.append(">");

            Collection<PacketExtension> children = getExtensions();
            for (PacketExtension ext : children)
            {
                xml.append(ext.toXML());
            }

            xml.append("</").append(ELEMENT_NAME).append(">");
        }
        return xml.toString();

    }

    /**
     * Adds property to this conference IQ.
     * @param name the property name
     * @param value the property value
     */
    public void addProperty(String name, String value)
    {
        addExtension(new Property(name, value));
    }

    /**
     * Packet extension for configuration properties.
     */
    public static class Property extends AbstractPacketExtension
    {
        /**
         * The name of property XML element.
         */
        public static final String ELEMENT_NAME = "property";

        /**
         * The name of 'name' property attribute.
         */
        public static final String NAME_ATTR_NAME = "name";

        /**
         * The name of 'value' property attribute.
         */
        public static final String VALUE_ATTR_NAME = "value";

        /**
         * Creates new empty <tt>Property</tt> instance.
         */
        public Property()
        {
            super(null, ELEMENT_NAME);
        }

        /**
         * Creates new <tt>Property</tt> instance initialized with given
         * <tt>name</tt> and <tt>value</tt> values.
         *
         * @param name a string that will be the name of new property.
         * @param value a string value for new property.
         */
        public Property(String name, String value)
        {
            this();

            setName(name);
            setValue(value);
        }

        /**
         * Sets the name of this property.
         * @param name a string that will be the name of this property.
         */
        public void setName(String name)
        {
            setAttribute(NAME_ATTR_NAME, name);
        }

        /**
         * Returns the name of this property.
         */
        public String getName()
        {
            return getAttributeAsString(NAME_ATTR_NAME);
        }

        /**
         * Sets the value of this property.
         * @param value a string value for new property.
         */
        public void setValue(String value)
        {
            setAttribute(VALUE_ATTR_NAME, value);
        }

        /**
         * Returns the value of this property.
         */
        public String getValue()
        {
            return getAttributeAsString(VALUE_ATTR_NAME);
        }
    }
}
