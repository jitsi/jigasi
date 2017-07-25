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
    public String getDisplayName()
    {
        return null;
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
