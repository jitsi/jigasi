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
 * {@link StrideIdentityPresenceExtension} and
 * {@link AvatarIdPresenceExtension}
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
     * {@link StrideIdentityPresenceExtension} in the {@link ChatRoomMember},
     * which acts as a {@link net.java.sip.communicator.util.DataObject}
     */
    public final static String STRIDE_IDENTITY_USERID
        = "STRIDE_IDENTITY_USERID";

    /**
     * The key used to save the username in a
     * {@link StrideIdentityPresenceExtension} in the {@link ChatRoomMember},
     * which acts as a {@link net.java.sip.communicator.util.DataObject}
     */
    public final static String STRIDE_IDENTITY_USERNAME
        = "STRIDE_IDENTITY_USERNAME";

    /**
     * The key used to save the avatar-url in a
     * {@link StrideIdentityPresenceExtension} in the {@link ChatRoomMember},
     * which acts as a {@link net.java.sip.communicator.util.DataObject}
     */
    public final static String STRIDE_IDENTITY_AVATAR_URL
        = "STRIDE_IDENTITY_AVATAR_URL";

    /**
     * The key used to save the group-id in a
     * {@link StrideIdentityPresenceExtension} in the {@link ChatRoomMember},
     * which acts as a {@link net.java.sip.communicator.util.DataObject}
     */
    public final static String STRIDE_IDENTITY_GROUPID
        = "STRIDE_IDENTITY_GROUPID";

    /**
     * The key used to save the avatar-id in a
     * {@link AvatarIdPresenceExtension} in the {@link ChatRoomMember},
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

        updateStrideIdentity(p, memberJabber);
        updateAvatarId(p, memberJabber);
    }


    /**
     * Receives a {@link Presence} and stores the data of a
     * {@link StrideIdentityPresenceExtension} if it is present inside the
     * {@link ChatRoomMemberJabberImpl}
     *
     * @param p the presence to check for a
     * {@link StrideIdentityPresenceExtension}
     * @param member the member to store information into
     */
    private static void updateStrideIdentity(Presence p,
                                             ChatRoomMemberJabberImpl member)
    {
        StrideIdentityPresenceExtension strideIdentity
            = p.getExtension(StrideIdentityPresenceExtension.ELEMENT_NAME,
            StrideIdentityPresenceExtension.NAME_SPACE);

        if(strideIdentity != null)
        {
            System.out.println("Stride identity received :)");
            member.setData(STRIDE_IDENTITY_USERID,
                strideIdentity.getUserId());
            member.setData(STRIDE_IDENTITY_USERNAME,
                strideIdentity.getUserName());
            member.setData(STRIDE_IDENTITY_AVATAR_URL,
                strideIdentity.getUserAvatarUrl());
            member.setData(STRIDE_IDENTITY_GROUPID,
                strideIdentity.getGroupId());
        }
        else
        {
            System.out.println("Stride identity is null :(");
        }
    }

    /**
     * Receives a {@link Presence} and stores the data of a
     * {@link AvatarIdPresenceExtension} if it is present inside the
     * {@link ChatRoomMemberJabberImpl}
     *
     * @param p the presence to check for a {@link AvatarIdPresenceExtension}
     * @param member the member to store information into
     */
    private static void updateAvatarId(Presence p,
                                       ChatRoomMemberJabberImpl member)
    {
        AvatarIdPresenceExtension avatarId
            = p.getExtension(AvatarIdPresenceExtension.ELEMENT_NAME,
            AvatarIdPresenceExtension.NAME_SPACE);

        if(avatarId != null)
        {
            member.setData(AVATAR_ID, avatarId.getAvatarId());
        }
    }

}
