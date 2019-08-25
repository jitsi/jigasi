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

import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.protocol.mock.*;
import net.java.sip.communicator.util.*;
import org.jitsi.jigasi.osgi.*;
import org.jitsi.meet.*;
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
        System.setProperty(
            "net.java.sip.communicator.impl.configuration.USE_PROPFILE_CONFIG",
            "true");

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

        JigasiBundleConfig bundles = new JigasiBundleConfig();

        JigasiBundleConfig.setUseMockProtocols(true);

        OSGi.setBundleConfig(bundles);

        bundles.setSystemPropertyDefaults();

        OSGi.setClassLoader(ClassLoader.getSystemClassLoader());

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
        Map accProps = new HashMap<String, String>();
        accProps.put(CallContext.DOMAIN_BASE_ACCOUNT_PROP, "sipserver.net");
        MockAccountID mockSipAccount
            = new MockAccountID("sipuser@sipserver.net",
                                accProps,
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
