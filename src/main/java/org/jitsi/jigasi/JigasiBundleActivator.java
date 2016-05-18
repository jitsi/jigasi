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
package org.jitsi.jigasi;

import java.util.*;

import net.java.sip.communicator.impl.protocol.jabber.*;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jitsimeet.*;
import net.java.sip.communicator.impl.protocol.jabber.extensions.rayo.*;
import net.java.sip.communicator.service.gui.*;
import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.protocol.jabber.*;
import net.java.sip.communicator.util.*;
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

    private UIServiceStub uiServiceStub = new UIServiceStub();

    /**
     * Returns <tt>ConfigurationService</tt> instance.
     * @return <tt>ConfigurationService</tt> instance.
     */
    public static ConfigurationService getConfigurationService()
    {
        return ServiceUtils.getService(
            osgiContext, ConfigurationService.class);
    }

    @Override
    public void start(final BundleContext bundleContext)
        throws Exception
    {
        osgiContext = bundleContext;

        bundleContext.registerService(UIService.class, uiServiceStub, null);

        gateway = new SipGateway();

        osgiContext.registerService(SipGateway.class, gateway, null);

        bundleContext.addServiceListener(this);

        Collection<ServiceReference<ProtocolProviderService>> refs
            = ServiceUtils.getServiceReferences(
                    osgiContext,
                    ProtocolProviderService.class);

        for (ServiceReference<ProtocolProviderService> ref : refs)
        {
            ProtocolProviderService pps = osgiContext.getService(ref);

            if (ProtocolNames.SIP.equals(pps.getProtocolName()))
            {
                gateway.setSipProvider(pps);
            }
        }

        // FIXME: make sure that we're using interoperability layer
        AbstractSmackInteroperabilityLayer.setImplementationClass(
            SmackV3InteroperabilityLayer.class);
    }

    @Override
    public void stop(BundleContext bundleContext)
        throws Exception
    {
        logger.info("Stopping JigasiBundleActivator");

        gateway.stop();
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
