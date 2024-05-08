/*
 * Jigasi, the JItsi GAteway to SIP.
 *
 * Copyright @ 2018 - present 8x8, Inc.
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
package org.jitsi.jigasi.xmpp.extensions;

import org.jitsi.xmpp.extensions.*;
import org.jivesoftware.smack.packet.*;

/**
 * Added to presence to raise/lower hand.
 */
public class RaiseHandExtension
    extends AbstractPacketExtension
{
    /**
     * The namespace of this packet extension.
     */
    public static final String NAMESPACE = "jabber:client";

    /**
     * XML element name of this packet extension.
     */
    public static final String ELEMENT_NAME = "jitsi_participant_raisedHand";

    /**
     * Creates a {@link org.jitsi.jigasi.xmpp.extensions.RaiseHandExtension} instance.
     */
    public RaiseHandExtension()
    {
        super(NAMESPACE, ELEMENT_NAME);
    }

    /**
     * Sets user's raise hand status.
     *
     * @param value <tt>true</tt> or <tt>false</tt> which raise hand status of the user.
     */
    public ExtensionElement setRaisedHandValue(Boolean value)
    {
        setText(value ? String.valueOf(System.currentTimeMillis()) : null);

        return this;
    }
}
