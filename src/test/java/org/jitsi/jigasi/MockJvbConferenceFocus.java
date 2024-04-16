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
import net.java.sip.communicator.service.protocol.event.*;
import net.java.sip.communicator.service.protocol.mock.*;
import net.java.sip.communicator.service.protocol.mock.muc.*;
import org.jitsi.utils.logging.Logger;
import org.osgi.framework.*;

/**
 * Mock JVB focus used for unit tests purposes.
 *
 * @author Pawel Domas
 */
public class MockJvbConferenceFocus
    implements ChatRoomMemberPresenceListener,
               ServiceListener
{
    /**
     * The logger.
     */
    private final static Logger logger
        = Logger.getLogger(MockJvbConferenceFocus.class);

    private final String roomName;

    private MockCall xmppCall;

    private MockMultiUserChat chatRoom;

    private String myName;

    private MockRoomMember myMember;

    private boolean leaveRoomAfterInvite;

    public MockJvbConferenceFocus(String roomName)
    {
        this.roomName = roomName;

        // we now filter and do not allow invites from non focus user
        myName = "focus";
    }

    public void setup()
        throws OperationFailedException, OperationNotSupportedException
    {
        JigasiBundleActivator.osgiContext.addServiceListener(this);
    }

    @Override
    public void serviceChanged(ServiceEvent serviceEvent)
    {
        if (serviceEvent.getType() == ServiceEvent.REGISTERED)
        {
            ServiceReference<?> ref = serviceEvent.getServiceReference();

            Object service = JigasiBundleActivator.osgiContext.getService(ref);
            if (service instanceof ProtocolProviderService)
            {
                ProtocolProviderService protocol
                    = (ProtocolProviderService) service;

                if (ProtocolNames.JABBER.equals(protocol.getProtocolName()))
                {
                    try
                    {
                        logger.info(
                            myName + " registers for provider " + protocol);

                        setXmppProvider(protocol);
                    }
                    catch (OperationFailedException e)
                    {
                        logger.error(e, e);
                    }
                    catch (OperationNotSupportedException e)
                    {
                        logger.error(e, e);
                    }
                }
            }
            JigasiBundleActivator.osgiContext.removeServiceListener(this);
        }
    }

    public void tearDown()
    {
        JigasiBundleActivator.osgiContext.removeServiceListener(this);

        if (chatRoom != null)
        {
            chatRoom.removeMemberPresenceListener(this);

            chatRoom.mockLeave(myMember.getName());

            myMember = null;

            chatRoom = null;
        }
    }

    public Call getCall()
    {
        return xmppCall;
    }

    public MockMultiUserChat getChatRoom()
    {
        return chatRoom;
    }

    private void inviteToConference(ChatRoomMember member)
    {
        MockBasicTeleOpSet xmppTele
            = (MockBasicTeleOpSet) member.getProtocolProvider()
                    .getOperationSet(OperationSetBasicTelephony.class);

        xmppCall
            = xmppTele.createIncomingCall(myMember.getName());

        logger.info(
            myName + " is inviting " + member.getName()
                + " to join conference in room " + roomName);

        if (leaveRoomAfterInvite)
        {
            logger.info(myName + " invited peer will leave the room");

            // let's leave after the xmpp call is in progress, as ended and connected will race for the call
            new Thread(new Runnable()
            {
                @Override
                public void run()
                {
                    try
                    {
                        logger.info("waiting for in progress on " + xmppCall);
                        CallStateListener callStateWatch = new CallStateListener();
                        callStateWatch.waitForState(xmppCall, CallState.CALL_IN_PROGRESS, 2000);
                        logger.info("done waiting for in progress on " + xmppCall);
                    }
                    catch (InterruptedException e)
                    {
                        throw new RuntimeException(e);
                    }

                    logger.info(myName + " leaving the room");
                    tearDown();
                }
            }).start();
        }
    }

    @Override
    public void memberPresenceChanged(ChatRoomMemberPresenceChangeEvent evt)
    {
        if (ChatRoomMemberPresenceChangeEvent.MEMBER_JOINED
            .equals(evt.getEventType()))
        {
            // Establish call session with new participant
            inviteToConference(evt.getChatRoomMember());
        }
    }

    private void setXmppProvider(ProtocolProviderService xmppProvider)
        throws OperationFailedException, OperationNotSupportedException
    {
        MockMultiUserChatOpSet xmppMucOpSet
            = (MockMultiUserChatOpSet) xmppProvider
                .getOperationSet(OperationSetMultiUserChat.class);

        this.chatRoom
            = (MockMultiUserChat) xmppMucOpSet.findRoom(roomName);

        if (chatRoom == null)
        {
            chatRoom = (MockMultiUserChat) xmppMucOpSet
                .createChatRoom(roomName, null);
        }

        logger.info(myName + " created room " + roomName);

        this.myMember = chatRoom.mockOwnerJoin(myName);

        if (chatRoom.getMembersCount() > 0)
        {
            // Invite immediately all people in the room
            for (ChatRoomMember member : chatRoom.getMembers())
            {
                if (member != myMember)
                {
                    inviteToConference(member);
                }
            }
        }
        // Listen for any new participants
        chatRoom.addMemberPresenceListener(this);
    }

    public String getRoomName()
    {
        return roomName;
    }

    public void setLeaveRoomAfterInvite(boolean leaveRoomAfterInvite)
    {
        this.leaveRoomAfterInvite = leaveRoomAfterInvite;
    }
}
