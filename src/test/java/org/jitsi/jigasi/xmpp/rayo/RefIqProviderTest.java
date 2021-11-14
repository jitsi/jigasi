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
package org.jitsi.jigasi.xmpp.rayo;

import static org.custommonkey.xmlunit.XMLAssert.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.custommonkey.xmlunit.*;
import org.jitsi.xmpp.extensions.rayo.*;
import org.jitsi.xmpp.util.*;
import org.junit.jupiter.api.*;
import org.jxmpp.jid.impl.*;
import org.jxmpp.stringprep.*;

/**
 * Tests parsing of RefIQs.
 *
 * @author Pawel Domas
 */
public class RefIqProviderTest
{
    @Test
    public void testParseRef()
        throws Exception
    {
        String uri = "someUri@fsjdo-54.trh56.4";
        String iqXml = getRefIqXML(uri);

        RayoIqProvider provider = new RayoIqProvider();
        RefIq dialIq
            = (RefIq) IQUtils.parse(iqXml, provider);

        assertEquals(uri, dialIq.getUri());

        assertNotNull(IQUtils.parse(getRefIqXML("someUri"), provider));

        assertNull(IQUtils.parse(getRefIqXML(""), provider));

        assertNull(IQUtils.parse(getRefIqXML(null), provider));
    }

    private String getRefIqXML(String uri) throws XmppStringprepException
    {
        RefIq iq = RefIq.create(uri);
        iq.setFrom(JidCreate.from("from@example.org"));
        iq.setTo(JidCreate.from("to@example.org"));
        return iq.toXML().toString();
    }

    @Test
    public void testRefToString() throws Exception
    {
        String uri = "from23dfsr";

        RefIq refIq = RefIq.create(uri);

        String id = refIq.getStanzaId();
        String type = refIq.getType().toString();

        assertXMLEqual(new Diff(
            String.format(
                "<iq id=\"%s\" type=\"%s\" xmlns=\"jabber:client\">" +
                    "<ref xmlns='urn:xmpp:rayo:1'" +
                    " uri='%s' />" +
                    "</iq>",
                id, type, uri),
            refIq.toXML().toString()), true);
    }
}
