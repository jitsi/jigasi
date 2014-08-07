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
 *
 */
public class MockProtocolProviderFactory
    extends ProtocolProviderFactory
{

    /**
     * Creates a new <tt>ProtocolProviderFactory</tt>.
     *
     * @param bundleContext the bundle context reference of the service
     * @param protocolName  the name of the protocol
     */
    public MockProtocolProviderFactory(
        BundleContext bundleContext,
        String protocolName)
    {
        super(bundleContext, protocolName);
    }

    @Override
    public AccountID installAccount(String userID,
                                    Map<String, String> accountProperties)
        throws IllegalArgumentException, IllegalStateException,
               NullPointerException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void modifyAccount(ProtocolProviderService protocolProvider,
                              Map<String, String> accountProperties)
        throws NullPointerException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    protected AccountID createAccountID(String userID,
                                        Map<String, String> accountProperties)
    {
        return new MockAccountID(userID, accountProperties, getProtocolName());
    }

    @Override
    protected ProtocolProviderService createService(String userID,
                                                    AccountID accountID)
    {
        MockProtocolProvider protocolProvider
            = new MockProtocolProvider((MockAccountID) accountID);

        protocolProvider.includeBasicTeleOpSet();

        if (ProtocolNames.JABBER.equals(getProtocolName()))
        {
            protocolProvider.includeMultiUserChatOpSet();
            protocolProvider.includeJitsiMeetTools();
        }

        return protocolProvider;
    }
}
