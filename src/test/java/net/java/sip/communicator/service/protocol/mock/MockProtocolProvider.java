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
