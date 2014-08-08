/*
 * Jitsi Videobridge, OpenSource video conferencing.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.jigasi;

import net.java.sip.communicator.impl.protocol.jabber.extensions.jitsimeet.*;
import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.util.*;
import org.jitsi.jigasi.xmpp.rayo.*;
import org.jitsi.service.configuration.*;
import org.jivesoftware.smack.provider.*;
import org.osgi.framework.*;

/**
 * Jigasi bundle activator. Registers {@link SipGateway} and waits for the first
 * SIP protocol provider service to be registered. Once SIP providers is
 * retrieved it is used by the gateway for establishing SIP calls.
 *
 * @author Pawel Domas
 */
public class JigasiBundleActivator
    implements BundleActivator,
               ServiceListener
{

    /**
     * The logger
     */
    private final static Logger logger
        = Logger.getLogger(JigasiBundleActivator.class);

    public static BundleContext osgiContext;

    private SipGateway gateway;

    /**
     * Returns <tt>ConfigurationService</tt> instance.
     * @return <tt>ConfigurationService</tt> instance.
     */
    public static ConfigurationService getConfigurationservice()
    {
        return ServiceUtils.getService(
            osgiContext, ConfigurationService.class);
    }

    @Override
    public void start(final BundleContext bundleContext)
        throws Exception
    {
        osgiContext = bundleContext;

        gateway = new SipGateway();

        osgiContext.registerService(SipGateway.class, gateway, null);

        bundleContext.addServiceListener(this);

        ServiceReference[] refs =
        ServiceUtils.getServiceReferences(
            osgiContext, ProtocolProviderService.class);

        for (ServiceReference ref : refs)
        {
            ProtocolProviderService pps
                = (ProtocolProviderService) osgiContext.getService(ref);

            if (ProtocolNames.SIP.equals(pps.getProtocolName()))
            {
                gateway.setSipProvider(pps);
            }
        }
    }

    @Override
    public void stop(BundleContext bundleContext)
        throws Exception
    {

    }

    @Override
    public void serviceChanged(ServiceEvent serviceEvent)
    {
        if (serviceEvent.getType() != ServiceEvent.REGISTERED)
            return;

        ServiceReference ref = serviceEvent.getServiceReference();

        Object service = osgiContext.getService(ref);

        if (!(service instanceof ProtocolProviderService))
            return;

        // FIXME: not sure where to put this...
        ProviderManager providerManager = ProviderManager.getInstance();

        // Register Jitsi Meet media presence extension.
        MediaPresenceExtension.registerExtensions(providerManager);
        // Register Rayo IQs
        new RayoIqProvider().registerRayoIQs(providerManager);

        ProtocolProviderService pps = (ProtocolProviderService) service;

        if (gateway.getSipProvider() == null &&
            ProtocolNames.SIP.equals(pps.getProtocolName()))
        {
            gateway.setSipProvider(pps);
        }
    }
}
