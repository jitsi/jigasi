/*
 * Jitsi Videobridge, OpenSource video conferencing.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.jigasi;

import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.protocol.mock.*;
import net.java.sip.communicator.util.*;
import org.jitsi.jigasi.osgi.*;
import org.osgi.framework.*;

import java.util.*;

/**
 * Helper class takes encapsulates OSGi specifics operations.
 *
 * @author Pawel Domas
 */
public class OSGiHandler
{
    /**
     * OSGi bundle context instance.
     */
    public BundleContext bc;

    private BundleActivator bundleActivator;

    private final Object syncRoot = new Object();

    private MockProtocolProvider sipProvider;

    public void init()
        throws InterruptedException
    {

        // Disable SingleCallInProgressPolicy
        System.setProperty(
            "net.java.sip.communicator.impl.protocol.SingleCallInProgressPolicy"
                + ".enabled",
            "false"
        );

        this.bundleActivator = new BundleActivator()
        {
            @Override
            public void start(BundleContext bundleContext)
                throws Exception
            {
                bc = bundleContext;
                synchronized (syncRoot)
                {
                    syncRoot.notify();
                }
            }

            @Override
            public void stop(BundleContext bundleContext)
                throws Exception
            {

            }
        };

        OSGi.setUseMockProtocols(true);

        OSGi.start(bundleActivator);

        if (bc == null)
        {
            synchronized (syncRoot)
            {
                syncRoot.wait(5000);
            }
        }

        if (bc == null)
            throw new RuntimeException("Failed to start OSGI");

        createMockSipProvider();
    }

    public void shutdown()
    {
        OSGi.stop(bundleActivator);
    }

    private void createMockSipProvider()
    {
        MockAccountID mockSipAccount
            = new MockAccountID("sipuser@sipserver.net",
                                new HashMap<String, String>(),
                                ProtocolNames.SIP);

        sipProvider
            = new MockProtocolProvider(mockSipAccount);

        sipProvider.includeBasicTeleOpSet();
        sipProvider.includeJitsiMeetTools();

        bc.registerService(ProtocolProviderService.class, sipProvider, null);
    }

    public MockProtocolProvider getSipProvider()
    {
        return sipProvider;
    }

    public SipGateway getSipGateway()
    {
        return ServiceUtils.getService(bc, SipGateway.class);
    }
}
