/*
 * Jitsi Videobridge, OpenSource video conferencing.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.jigasi.xmpp.rayo;

import net.java.sip.communicator.impl.protocol.jabber.extensions.rayo.*;
import org.jitsi.jigasi.xmpp.*;
import org.junit.*;
import org.junit.runner.*;
import org.junit.runners.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Tests parsing of RefIQs.
 *
 * @author Pawel Domas
 */
@RunWith(JUnit4.class)
public class RefIqProviderTest
{
    @Test
    public void testParseRef()
        throws Exception
    {
        String uri = "someUri@fsjdo-54.trh56.4";
        String iqXml = getRefIqXML(uri);

        RayoIqProvider provider = new RayoIqProvider();
        RayoIqProvider.RefIq dialIq
            = (RayoIqProvider.RefIq) IQUtils.parse(iqXml, provider);

        assertEquals(uri, dialIq.getUri());

        assertNotNull(IQUtils.parse(getRefIqXML("someUri"), provider));

        assertEquals(
            null, IQUtils.parse(getRefIqXML(""), provider));

        assertEquals(
            null, IQUtils.parse(getRefIqXML(null), provider));
    }

    private String getRefIqXML(String uri)
    {
        return RayoIqProvider.RefIq.create(uri).toXML();
    }

    @Test
    public void testRefToString()
    {
        String uri = "from23dfsr";

        RayoIqProvider.RefIq refIq = RayoIqProvider.RefIq.create(uri);

        String id = refIq.getPacketID();
        String type = refIq.getType().toString();

        assertEquals(
            String.format(
                "<iq id=\"%s\" type=\"%s\">" +
                    "<ref xmlns='urn:xmpp:rayo:1'" +
                    " uri='%s' />" +
                    "</iq>",
                id, type, uri),
            refIq.toXML()
        );
    }
}
