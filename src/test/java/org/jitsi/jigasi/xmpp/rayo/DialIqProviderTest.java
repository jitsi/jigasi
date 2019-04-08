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

import org.custommonkey.xmlunit.*;
import org.jitsi.xmpp.extensions.rayo.*;
import org.jitsi.xmpp.util.*;
import org.jivesoftware.smack.packet.*;
import org.junit.Test;
import org.junit.runner.*;
import org.junit.runners.*;
import org.jxmpp.jid.impl.*;
import org.jxmpp.stringprep.*;

import static org.custommonkey.xmlunit.XMLAssert.assertXMLEqual;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Tests DialIQ parsing.
 *
 * @author Pawel Domas
 */
@RunWith(JUnit4.class)
public class DialIqProviderTest
{
    @Test
    public void testParseDial()
        throws Exception
    {
        String src = "somesource";
        String dst = "somedestination";
        String iqXml = getDialIqXML(dst, src);

        RayoIqProvider provider = new RayoIqProvider();
        RayoIqProvider.DialIq dialIq
            = (RayoIqProvider.DialIq) IQUtils.parse(iqXml, provider);

        assertEquals(src, dialIq.getSource());
        assertEquals(dst, dialIq.getDestination());

        assertNotNull(IQUtils.parse(getDialIqXML("to", ""), provider));

        // "to" attribute is mandatory for SIP gateway
        // "from" is optional(might be used to select source SIP account)
        // otherwise default one will be used.
        assertEquals(
            null, IQUtils.parse(getDialIqXML("", "from"), provider));

        assertEquals(
            null, IQUtils.parse(getDialIqXML("", ""), provider));

        assertEquals(
            null, IQUtils.parse(getDialIqXML(null, null), provider));
    }

    private String getDialIqXML(String to, String from)
        throws XmppStringprepException
    {
        RayoIqProvider.DialIq iq = RayoIqProvider.DialIq.create(to, from);
        iq.setFrom(JidCreate.from("from@example.com"));
        iq.setTo(JidCreate.from("to@example.org"));
        return iq.toXML().toString();
    }

    @Test
    public void testDialToString() throws Exception
    {
        String src = "from23dfsr";
        String dst = "to123213";

        RayoIqProvider.DialIq dialIq = RayoIqProvider.DialIq.create(dst, src);

        String id = dialIq.getStanzaId();
        String type = dialIq.getType().toString();

        assertXMLEqual(new Diff(
            String.format(
                "<iq id=\"%s\" type=\"%s\">" +
                    "<dial xmlns='urn:xmpp:rayo:1'" +
                    " from='%s' to='%s' />" +
                "</iq>",
                id, type, src, dst),
            dialIq.toXML().toString()),
        true);

        dialIq.setHeader("h1", "v1");

        assertXMLEqual(new Diff(
            String.format(
                "<iq id=\"%s\" type=\"%s\">" +
                    "<dial xmlns='urn:xmpp:rayo:1' from='%s' to='%s' >" +
                        "<header  name='h1' value='v1'/>" +
                    "</dial>" +
                    "</iq>",
                id, type, src, dst),
            dialIq.toXML().toString()),
        true);
    }

    @Test
    public void testParseHeaders()
        throws Exception
    {
        String dialIqXml =
            "<iq id='123' type='set' from='fromJid' to='toJid' >" +
                "<dial xmlns='urn:xmpp:rayo:1' from='source' to='dest'>" +
                    "<header name='h1' value='v1' />" +
                    "<header name='h2' value='v2' />" +
                "</dial>" +
            "</iq>";

        RayoIqProvider.DialIq iq
            = (RayoIqProvider.DialIq) IQUtils.parse(
                    dialIqXml, new RayoIqProvider());

        // IQ
        assertEquals("123", iq.getStanzaId());
        assertEquals(IQ.Type.set, iq.getType());
        assertEquals("fromjid", iq.getFrom().toString());
        assertEquals("tojid", iq.getTo().toString());
        // Dial
        assertEquals("source", iq.getSource());
        assertEquals("dest", iq.getDestination());
        // Header
        assertEquals("v1", iq.getHeader("h1"));
        assertEquals("v2", iq.getHeader("h2"));
    }
}
