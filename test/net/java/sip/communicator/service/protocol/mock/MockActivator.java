/*
 * Jitsi Videobridge, OpenSource video conferencing.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol.mock;

import net.java.sip.communicator.service.protocol.*;

import org.osgi.framework.*;

import java.util.*;

/**
 * Registers mock protocol provider factories for SIP and XMPP.
 *
 * @author Pawel Domas
 */
public class MockActivator
    implements BundleActivator
{
    private ServiceRegistration<?> xmppRegistration;

    private ServiceRegistration<?> sipRegistration;

    @Override
    public void start(BundleContext bundleContext)
        throws Exception
    {
        MockProtocolProviderFactory sipFactory
            = new MockProtocolProviderFactory(
                    bundleContext, ProtocolNames.SIP);

        MockProtocolProviderFactory xmppFactory
            = new MockProtocolProviderFactory(
                    bundleContext, ProtocolNames.JABBER);

        Hashtable<String, String> hashtable = new Hashtable<String, String>();

        // Register XMPP
        hashtable.put(ProtocolProviderFactory.PROTOCOL, ProtocolNames.JABBER);

        xmppRegistration = bundleContext.registerService(
            ProtocolProviderFactory.class.getName(),
            xmppFactory,
            hashtable);

        // Register SIP
        hashtable.put(ProtocolProviderFactory.PROTOCOL, ProtocolNames.SIP);

        sipRegistration = bundleContext.registerService(
            ProtocolProviderFactory.class.getName(),
            sipFactory,
            hashtable);
    }

    @Override
    public void stop(BundleContext bundleContext)
        throws Exception
    {
        if (xmppRegistration != null)
            xmppRegistration.unregister();

        if (sipRegistration != null)
            sipRegistration.unregister();
    }
}
