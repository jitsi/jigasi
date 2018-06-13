/*
 * Jigasi, the JItsi GAteway to SIP.
 *
 * Copyright @ 2018 Atlassian Pty Ltd
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
package org.jitsi.jigasi.xmpp;

import net.java.sip.communicator.impl.protocol.jabber.*;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jitsimeet.*;
import net.java.sip.communicator.service.protocol.*;
import org.jitsi.jigasi.util.*;
import org.jitsi.util.*;
import org.jivesoftware.smack.packet.*;

/**
 * This class is able to receive {@link Presence} objects and update data
 * belonging to a
 * {@link org.jitsi.jigasi.transcription.Participant} by checking for
 * {@link IdentityPacketExtension} and
 * {@link AvatarIdPacketExtension}
 * in the presence and storing their values inside a
 * {@link ChatRoomMember}
 *
 * @author Nik Vaessen
 */
public class ChatRoomMemberPresenceExtensionReader
{
    /**
     * The logger of this class
     */
    public final static Logger logger
        = Logger.getLogger(ChatRoomMemberPresenceExtensionReader.class);

    /**
     * The key used to save the user-id in a
     * {@link IdentityPacketExtension} in the {@link ChatRoomMember},
     * which acts as a {@link net.java.sip.communicator.util.DataObject}
     */
    public final static String IDENTITY_USERID
        = "IDENTITY_USERID";

    /**
     * The key used to save the username in a
     * {@link IdentityPacketExtension} in the {@link ChatRoomMember},
     * which acts as a {@link net.java.sip.communicator.util.DataObject}
     */
    public final static String IDENTITY_USERNAME
        = "IDENTITY_USERNAME";

    /**
     * The key used to save the avatar-url in a
     * {@link IdentityPacketExtension} in the {@link ChatRoomMember},
     * which acts as a {@link net.java.sip.communicator.util.DataObject}
     */
    public final static String IDENTITY_AVATAR_URL
        = "IDENTITY_AVATAR_URL";

    /**
     * The key used to save the group-id in a
     * {@link IdentityPacketExtension} in the {@link ChatRoomMember},
     * which acts as a {@link net.java.sip.communicator.util.DataObject}
     */
    public final static String IDENTITY_GROUPID
        = "IDENTITY_GROUPID";

    /**
     * The key used to save the avatar-id in a
     * {@link AvatarIdPacketExtension} in the {@link ChatRoomMember},
     * which acts as a {@link net.java.sip.communicator.util.DataObject}
     */
    public final static String AVATAR_ID = "AVATAR_ID";

    /**
     * Update the data stored in the given {@link ChatRoomMember} by
     * reading the latest stored {@link Presence}. Currently only
     * {@link ChatRoomMemberJabberImpl} exposes its last-received Presence
     *
     * @param member the {@link ChatRoomMemberJabberImpl} to retrieve the
     * presence from
     */
    public static void readLastPresenceAndUpdate(ChatRoomMember member)
    {
        if(!(member instanceof ChatRoomMemberJabberImpl))
        {
            logger.warn("ChatRoomMember was not an instance of " +
                "ChatRoomMemberJabberImpl and could thus not be updated");
            return;
        }

        ChatRoomMemberJabberImpl memberJabber
            = ((ChatRoomMemberJabberImpl) member);
        Presence p = memberJabber.getLastPresence();

        if(logger.isTraceEnabled())
        {
            logger.trace("received presence: \n" +
                Util.prettyFormat(p.toXML().toString(), 4) );
        }

        updateIdentity(p, memberJabber);
        updateAvatarId(p, memberJabber);
    }


    /**
     * Receives a {@link Presence} and stores the data of a
     * {@link IdentityPacketExtension} if it is present inside the
     * {@link ChatRoomMemberJabberImpl}
     *
     * @param p the presence to check for a
     * {@link IdentityPacketExtension}
     * @param member the member to store information into
     */
    private static void updateIdentity(Presence p,
                                       ChatRoomMemberJabberImpl member)
    {
        IdentityPacketExtension strideIdentity
            = p.getExtension(IdentityPacketExtension.ELEMENT_NAME,
            IdentityPacketExtension.NAME_SPACE);

        if(strideIdentity != null)
        {
            member.setData(IDENTITY_USERID,
                strideIdentity.getUserId());
            member.setData(IDENTITY_USERNAME,
                strideIdentity.getUserName());
            member.setData(IDENTITY_AVATAR_URL,
                strideIdentity.getUserAvatarUrl());
            member.setData(IDENTITY_GROUPID,
                strideIdentity.getGroupId());
        }
    }

    /**
     * Receives a {@link Presence} and stores the data of a
     * {@link AvatarIdPacketExtension} if it is present inside the
     * {@link ChatRoomMemberJabberImpl}
     *
     * @param p the presence to check for a {@link AvatarIdPacketExtension}
     * @param member the member to store information into
     */
    private static void updateAvatarId(Presence p,
                                       ChatRoomMemberJabberImpl member)
    {
        AvatarIdPacketExtension avatarId
            = p.getExtension(AvatarIdPacketExtension.ELEMENT_NAME,
            AvatarIdPacketExtension.NAME_SPACE);

        if(avatarId != null)
        {
            member.setData(AVATAR_ID, avatarId.getAvatarId());
        }
    }

}
