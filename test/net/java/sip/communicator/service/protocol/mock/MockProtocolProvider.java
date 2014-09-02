/*
 * Jitsi Videobridge, OpenSource video conferencing.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol.mock;

import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.protocol.event.*;
import net.java.sip.communicator.service.protocol.mock.muc.*;

/**
 *
 * @author Pawel Domas
 */
public class MockProtocolProvider
    extends AbstractProtocolProviderService
{
    private final MockAccountID accountId;

    private RegistrationState registrationState
        = RegistrationState.UNREGISTERED;

    public MockProtocolProvider(MockAccountID accountId)
    {
        this.accountId = accountId;
    }

    @Override
    public void register(SecurityAuthority authority)
        throws OperationFailedException
    {
        setRegistrationState(
            RegistrationState.REGISTERED,
            RegistrationStateChangeEvent.REASON_NOT_SPECIFIED,
            null);
    }

    private void setRegistrationState(RegistrationState newState,
                                      int reasonCode,
                                      String reason)
    {
        RegistrationState oldState = getRegistrationState();

        this.registrationState = newState;

        fireRegistrationStateChanged(
            oldState, newState, reasonCode, reason);
    }

    @Override
    public void unregister()
        throws OperationFailedException
    {
        setRegistrationState(
            RegistrationState.UNREGISTERED,
            RegistrationStateChangeEvent.REASON_NOT_SPECIFIED,
            null);
    }

    @Override
    public RegistrationState getRegistrationState()
    {
        return registrationState;
    }

    @Override
    public String getProtocolName()
    {
        return accountId.getProtocolName();
    }

    @Override
    public ProtocolIcon getProtocolIcon()
    {
        return null;
    }

    @Override
    public void shutdown()
    {

    }

    @Override
    public AccountID getAccountID()
    {
        return accountId;
    }

    @Override
    public boolean isSignalingTransportSecure()
    {
        return false;
    }

    @Override
    public TransportProtocol getTransportProtocol()
    {
        return null;
    }


    public void includeBasicTeleOpSet()
    {
        addSupportedOperationSet(
            OperationSetBasicTelephony.class,
            new MockBasicTeleOpSet(this));
    }

    public void includeMultiUserChatOpSet()
    {
        addSupportedOperationSet(
            OperationSetMultiUserChat.class,
            new MockMultiUserChatOpSet(this));
    }

    public void includeJitsiMeetTools()
    {
        addSupportedOperationSet(
            OperationSetJitsiMeetTools.class,
            new MockJitsiMeetTools(this));
    }

    public MockJitsiMeetTools getJitsiMeetTools()
    {
        return (MockJitsiMeetTools)
            getOperationSet(OperationSetJitsiMeetTools.class);
    }

    public MockBasicTeleOpSet getTelephony()
    {
        return (MockBasicTeleOpSet) getOperationSet(
            OperationSetBasicTelephony.class);
    }
}
