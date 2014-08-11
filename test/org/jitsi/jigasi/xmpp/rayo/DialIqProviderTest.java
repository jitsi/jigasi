/*
 * Jitsi Videobridge, OpenSource video conferencing.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.jigasi.xmpp.rayo;

import net.java.sip.communicator.impl.protocol.jabber.extensions.rayo.*;
import org.jitsi.jigasi.xmpp.*;
import org.jivesoftware.smack.packet.IQ;
import org.junit.Test;
import org.junit.runner.*;
import org.junit.runners.*;

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
    {
        return RayoIqProvider.DialIq.create(to, from).toXML();
    }

    @Test
    public void testDialToString()
    {
        String src = "from23dfsr";
        String dst = "to123213";

        RayoIqProvider.DialIq dialIq = RayoIqProvider.DialIq.create(dst, src);

        String id = dialIq.getPacketID();
        String type = dialIq.getType().toString();

        assertEquals(
            String.format(
                "<iq id=\"%s\" type=\"%s\">" +
                    "<dial xmlns='urn:xmpp:rayo:1'" +
                    " from='%s' to='%s' />" +
                "</iq>",
                id, type, src, dst),
            dialIq.toXML()
        );

        dialIq.setHeader("h1", "v1");

        assertEquals(
            String.format(
                "<iq id=\"%s\" type=\"%s\">" +
                    "<dial xmlns='urn:xmpp:rayo:1' from='%s' to='%s' >" +
                        "<header  name='h1' value='v1'/>" +
                    "</dial>" +
                    "</iq>",
                id, type, src, dst),
            dialIq.toXML()
        );
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
        assertEquals("123", iq.getPacketID());
        assertEquals(IQ.Type.SET, iq.getType());
        assertEquals("fromJid", iq.getFrom());
        assertEquals("toJid", iq.getTo());
        // Dial
        assertEquals("source", iq.getSource());
        assertEquals("dest", iq.getDestination());
        // Header
        assertEquals("v1", iq.getHeader("h1"));
        assertEquals("v2", iq.getHeader("h2"));
    }
}
