/*
 * Jitsi Videobridge, OpenSource video conferencing.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
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
        groupedChats.add(chatRoom);

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
        for (MockMultiUserChat chatToNotify : groupedChats)
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
        for (MockMultiUserChat chatToNotify : groupedChats)
        {
            if (chatToNotify != chatRoom)
            {
                chatToNotify.mockLeave(chatRoomMember.getName());
            }
        }
    }
}
