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
package net.java.sip.communicator.service.protocol.mock.muc;

import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.protocol.event.*;
import net.java.sip.communicator.util.*;

import java.util.*;

/**
 * The purpose of this class is to simulate mock room joined by all
 * {@link net.java.sip.communicator.service.protocol.mock.MockProtocolProvider}s
 * only if they share the same room name.
 *
 * @author Pawel Domas
 */
public class MockMucShare
    implements ChatRoomMemberPresenceListener
{
    private final static Logger logger = Logger.getLogger(MockMucShare.class);

    private final String roomName;

    private List<MockMultiUserChat> groupedChats
        = new ArrayList<MockMultiUserChat>();

    public MockMucShare(String roomName)
    {
        this.roomName = roomName;
    }

    public void nextRoomCreated(MockMultiUserChat chatRoom)
    {
        synchronized(groupedChats)
        {
            groupedChats.add(chatRoom);
        }

        chatRoom.addMemberPresenceListener(this);

        // Copy existing members if any
        for (ChatRoomMember member : chatRoom.getMembers())
        {
            broadcastMemberJoined(chatRoom, member);
        }
    }

    @Override
    public void memberPresenceChanged(ChatRoomMemberPresenceChangeEvent evt)
    {
        String eventType = evt.getEventType();

        if (ChatRoomMemberPresenceChangeEvent.MEMBER_JOINED.equals(eventType))
        {
            broadcastMemberJoined(evt.getChatRoom(), evt.getChatRoomMember());
        }
        else if(
            ChatRoomMemberPresenceChangeEvent.MEMBER_KICKED.equals(eventType)
            || ChatRoomMemberPresenceChangeEvent.MEMBER_LEFT.equals(eventType)
            || ChatRoomMemberPresenceChangeEvent.MEMBER_QUIT.equals(eventType) )
        {
            broadcastMemberLeft(evt.getChatRoom(), evt.getChatRoomMember());
        }
        else
        {
            logger.warn("Unsupported event type: " + eventType);
        }
    }

    private void broadcastMemberJoined(ChatRoom       chatRoom,
                                       ChatRoomMember chatRoomMember)
    {
        List<MockMultiUserChat> listeners;
        synchronized(groupedChats)
        {
            listeners = new ArrayList<>(groupedChats);
        }

        for (MockMultiUserChat chatToNotify : listeners)
        {
            if (chatToNotify != chatRoom)
            {
                chatToNotify.removeMemberPresenceListener(this);

                chatToNotify.mockJoin((MockRoomMember) chatRoomMember);

                chatToNotify.addMemberPresenceListener(this);
            }
        }
    }

    private void broadcastMemberLeft(ChatRoom       chatRoom,
                                     ChatRoomMember chatRoomMember)
    {
        List<MockMultiUserChat> listeners;
        synchronized(groupedChats)
        {
            listeners = new ArrayList<>(groupedChats);
        }

        for (MockMultiUserChat chatToNotify : listeners)
        {
            if (chatToNotify != chatRoom)
            {
                chatToNotify.mockLeave(chatRoomMember.getName());
            }
        }
    }
}
