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
import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.protocol.mock.*;
import net.java.sip.communicator.util.osgi.*;
import org.jitsi.jigasi.osgi.*;
import org.osgi.framework.*;
import org.osgi.framework.launch.*;

/**
 * Helper class takes encapsulates OSGi specifics operations.
 *
 * @author Pawel Domas
 */
public class OSGiHandler
{
    private BundleContext bc;

    public Framework init()
        throws InterruptedException, BundleException
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

        JigasiBundleConfig.setSystemPropertyDefaults();
        // The bundles start in this order, one after the other, so the mock
        // factories are registered before MockSipActivator starts.
        var fw = Main.start(List.of(
            MockProtocolProviderFactoriesActivator.class,
            MockSipActivator.class));
        bc = fw.getBundleContext();
        return fw;
    }

    /**
     * Registers the mock SIP provider when the bundle starts.
     * <p>
     * This is not a {@code DependentActivator} on
     * {@link ProtocolProviderFactory}: there are two mock factories, and when
     * their registrations are handled at the same time, a
     * {@code DependentActivator} can miss its start and the provider is never
     * registered.
     */
    public static class MockSipActivator
        implements BundleActivator
    {
        @Override
        public void start(BundleContext context)
        {
            Map<String, String> accProps = new HashMap<>();
            accProps.put(CallContext.DOMAIN_BASE_ACCOUNT_PROP, "sipserver.net");
            MockAccountID mockSipAccount
                = new MockAccountID("sipuser@sipserver.net",
                accProps,
                ProtocolNames.SIP);

            var sipProvider = new MockProtocolProvider(mockSipAccount);
            sipProvider.includeBasicTeleOpSet();
            sipProvider.includeJitsiMeetToolsSip();

            var properties = new Hashtable<String, String>();
            properties.put(ProtocolProviderFactory.PROTOCOL, ProtocolNames.SIP);
            properties.put(ProtocolProviderFactory.USER_ID,
                "sipuser@sipserver.net");
            context.registerService(ProtocolProviderService.class, sipProvider,
                properties);
        }

        @Override
        public void stop(BundleContext context)
        {
        }
    }

    public MockProtocolProvider getSipProvider() throws InvalidSyntaxException
    {
        var filter =
            "(&(" + ProtocolProviderFactory.PROTOCOL + "=" + ProtocolNames.SIP
                + ")"
                + "(" + ProtocolProviderFactory.USER_ID
                + "=sipuser@sipserver.net))";
        // MockSipActivator registers the provider while the framework starts, so it is normally found on the
        // first try.
        var deadline = System.currentTimeMillis() + 5000;
        do
        {
            var ref = bc.getServiceReferences(ProtocolProviderService.class, filter)
                .stream()
                .findFirst()
                .orElse(null);
            if (ref != null)
            {
                return (MockProtocolProvider) bc.getService(ref);
            }

            try
            {
                Thread.sleep(50);
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
                break;
            }
        }
        while (System.currentTimeMillis() < deadline);

        throw new NoSuchElementException("SIP provider was not registered");
    }

    public SipGateway getSipGateway()
    {
        return ServiceUtils.getService(bc, SipGateway.class);
    }
}
