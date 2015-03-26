/*
 * Jitsi Videobridge, OpenSource video conferencing.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol.mock.muc;

import net.java.sip.communicator.service.protocol.*;

/**
 * @author Pawel Domas
 */
public class MockRoomMember
    implements ChatRoomMember
{
    private final String name;

    private final MockMultiUserChat room;

    private ChatRoomMemberRole role = ChatRoomMemberRole.MEMBER;

    public MockRoomMember(String name, MockMultiUserChat chatRoom)
    {
        this.name = name;
        this.room = chatRoom;
    }

    @Override
    public ChatRoom getChatRoom()
    {
        return room;
    }

    @Override
    public ProtocolProviderService getProtocolProvider()
    {
        return room.getParentProvider();
    }

    @Override
    public String getContactAddress()
    {
        return name;
    }

    @Override
    public String getName()
    {
        return name;
    }

    @Override
    public byte[] getAvatar()
    {
        return new byte[0];
    }

    @Override
    public Contact getContact()
    {
        return null;
    }

    @Override
    public ChatRoomMemberRole getRole()
    {
        return role;
    }

    @Override
    public void setRole(ChatRoomMemberRole role)
    {
        this.role = role;
    }

    @Override
    public PresenceStatus getPresenceStatus()
    {
        return null;
    }

    @Override
    public String toString()
    {
        return "Member@" + hashCode() + "[" + name + "]";
    }
}
