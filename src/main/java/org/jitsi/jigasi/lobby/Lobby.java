/*
 * Jigasi, the JItsi GAteway to SIP.
 *
 * Copyright @ 2018 - present 8x8, Inc.
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
package org.jitsi.jigasi.lobby;

import net.java.sip.communicator.service.protocol.event.*;
import org.jitsi.jigasi.*;

import org.jitsi.jigasi.sounds.*;
import org.jitsi.utils.logging.Logger;
import org.jivesoftware.smackx.nick.packet.*;
import org.jxmpp.jid.*;
import org.jxmpp.jid.parts.*;
import net.java.sip.communicator.impl.protocol.jabber.*;
import net.java.sip.communicator.service.protocol.*;
import org.jxmpp.jid.impl.*;
import org.jxmpp.stringprep.*;

import static net.java.sip.communicator.service.protocol.event.LocalUserChatRoomPresenceChangeEvent.*;

/**
 * Class used to join and leave the lobby room and provides a way to handle lobby events.
 * If lobby is enabled JvbConference will fail join registration and Lobby will be used
 * to confirm join in the initial JvbConference.
 *
 * @author Cristian Florin Ghita
 * @author Damian Minkov
 */
public class Lobby
    implements ChatRoomInvitationListener,
               LocalUserChatRoomPresenceListener
{
    /**
     * The logger.
     */
    private final static Logger logger = Logger.getLogger(Lobby.class);

    /**
     * The data form field added when lobby is enabled.
     */
    public static final String DATA_FORM_LOBBY_ROOM_FIELD = "muc#roominfo_lobbyroom";

    /**
     * The data form field added when single moderator is enabled for the room.
     */
    public static final String DATA_FORM_SINGLE_MODERATOR_FIELD = "muc#roominfo_moderator_identity";

    /**
     * The XMPP provider used to join JVB conference.
     */
    private final ProtocolProviderService xmppProvider;

    /**
     * Room full Jid.
     */
    private final EntityFullJid roomJid;

    /**
     * Main room Jid.
     */
    private final Jid mainRoomJid;

    /**
     * Helper call context.
     */
    private final CallContext callContext;

    /**
     * <tt>ChatRoom</tt> instance that hosts the conference(not null if joined).
     */
    private ChatRoom mucRoom = null;

    /**
     * <tt>JvbConference</tt> Handles JVB conference events and connections.
     */
    private final JvbConference jvbConference;

    /**
     * <tt>SipGatewaySession</tt> Handles SIP events and connections.
     */
    private final SipGatewaySession sipGatewaySession;

    /**
     * Creates a new instance of <tt>Lobby</tt>
     *
     * @param protocolProviderService <tt>ProtocolProviderService</tt> registered protocol service to be used.
     * @param context <tt>CallContext</tt> to be used.
     * @param lobbyJid <tt>EntityFullJid</tt> for the lobby room to join.
     */
    public Lobby(ProtocolProviderService protocolProviderService,
                 CallContext context,
                 EntityFullJid lobbyJid,
                 Jid roomJid,
                 JvbConference jvbConference,
                 SipGatewaySession sipGateway)
    {
        super();

        this.xmppProvider = protocolProviderService;

        this.roomJid = lobbyJid;

        this.callContext = context;

        this.mainRoomJid = roomJid;

        this.jvbConference = jvbConference;

        this.sipGatewaySession = sipGateway;
    }

    /**
     * Used to join the lobby room.
     *
     * @throws OperationFailedException
     * @throws OperationNotSupportedException
     */
    public void join()
            throws OperationFailedException,
            OperationNotSupportedException
    {
        joinRoom(getRoomJid());

        this.sipGatewaySession.notifyOnLobbyWaitReview(this.mucRoom);
    }

    /**
     * Called by join can be overridden.
     *
     * @param roomJid The lobby room jid to use to join.
     * @throws OperationFailedException
     * @throws OperationNotSupportedException
     */
    protected void joinRoom(Jid roomJid) throws OperationFailedException, OperationNotSupportedException
    {
        OperationSetMultiUserChat muc
                = this.xmppProvider.getOperationSet(OperationSetMultiUserChat.class);

        muc.addInvitationListener(this);

        muc.addPresenceListener(this);

        ChatRoom mucRoom = muc.findRoom(roomJid.toString());

        setupChatRoom(mucRoom);

        mucRoom.joinAs(getResourceIdentifier().toString());

        this.mucRoom = mucRoom;
    }

    /**
     * Used to leave the lobby room.
     */
    public void leave()
    {
        leaveRoom();
    }

    /**
     * Called by leave can be overridden.
     */
    protected void leaveRoom()
    {
        OperationSetMultiUserChat muc = this.xmppProvider.getOperationSet(OperationSetMultiUserChat.class);

        muc.removeInvitationListener(this);

        muc.removePresenceListener(this);

        if (mucRoom == null)
        {
            logger.warn(getCallContext() + " MUC room is null");
            return;
        }

        mucRoom.leave();

        mucRoom = null;
    }

    /**
     * Used to get <tt>ChatRoomInvitationListener</tt> events. After participant is allowed to join this method will
     * be called.
     *
     * @param chatRoomInvitationReceivedEvent <tt>ChatRoomInvitationReceivedEvent</tt> contains invitation info.
     */
    @Override
    public void invitationReceived(ChatRoomInvitationReceivedEvent chatRoomInvitationReceivedEvent)
    {
        try
        {
            byte[] pass = chatRoomInvitationReceivedEvent.getInvitation().getChatRoomPassword();
            if (pass != null)
            {
                callContext.setRoomPassword(new String(pass));
            }

            this.notifyAccessGranted();

            if (this.jvbConference != null)
            {
                this.jvbConference.joinConferenceRoom();
            }
            else
            {
                logger.error(getCallContext() + " No JVB conference!!!");
            }

            leave();
        }
        catch (Exception ex)
        {
            logger.error(getCallContext() + " " + ex, ex);
        }
    }

    /**
     * Access granted, notifies sound manager and sip gw session.
     */
    private void notifyAccessGranted()
    {
        this.sipGatewaySession.getSoundNotificationManager()
            .notifyLobbyAccessGranted();

        this.sipGatewaySession.notifyLobbyAllowedJoin();
        this.sipGatewaySession.notifyLobbyLeft();
    }

    /**
     * Participant is kicked if rejected on join and this method handles the lobby rejection and lobby room destruction.
     * Participant receives LOCAL_USER_ROOM_DESTROYED if lobby is disabled.
     *
     * @param evt <tt>LocalUserChatRoomPresenceChangeEvent</tt> contains reason.
     */
    @Override
    public void localUserPresenceChanged(LocalUserChatRoomPresenceChangeEvent evt)
    {
        try
        {
            if (evt.getChatRoom().equals(this.mucRoom))
            {
                SoundNotificationManager soundManager = this.sipGatewaySession.getSoundNotificationManager();
                if (evt.getEventType().equals(LOCAL_USER_KICKED))
                {

                    // Lobby access denied.
                    soundManager.notifyLobbyAccessDenied();

                    sipGatewaySession.notifyLobbyRejectedJoin();

                    leave();

                    return;
                }

                if (evt.getEventType().equals(LOCAL_USER_LEFT))
                {
                    // Lobby access granted.
                    String alternateAddress = evt.getAlternateAddress();

                    if (alternateAddress != null)
                    {
                        accessGranted(alternateAddress);
                    }

                    return;
                }

                if (evt.getEventType().equals(LOCAL_USER_JOIN_FAILED))
                {

                    //If join has failed playback the meeting ended notification.
                    logger.error("Failed to join lobby!");

                    return;
                }

                if (evt.getEventType().equals(LOCAL_USER_ROOM_DESTROYED))
                {
                    String alternateAddress = evt.getAlternateAddress();

                    if (alternateAddress == null)
                    {
                        soundManager.notifyLobbyRoomDestroyed();
                    }
                    else
                    {
                        // Lobby access granted by disabling the lobby.
                        accessGranted(alternateAddress);
                    }
                }
            }
        }
        catch (Exception ex)
        {
            logger.error(getCallContext() + " " + ex, ex);
        }
    }

    /**
     * Access is granted.
     * @param alternateAddress
     * @throws XmppStringprepException
     */
    private void accessGranted(String alternateAddress)
        throws XmppStringprepException
    {
        Jid alternateJid = JidCreate.entityBareFrom(alternateAddress);

        if (!alternateJid.equals(this.mainRoomJid))
        {
            logger.warn(getCallContext() + " Alternate Jid(" + alternateJid
                + ") not the same as main room Jid(" + this.mainRoomJid + ")!");
            return;
        }

        try
        {
            // we may receive destroy and user leave with alternate address one after another
            // in case of lobby disabled, leaving early the lobby will remove listeners and
            // one of them will not be delivered here
            leave();
        }
        catch(Exception e)
        {
            logger.error(getCallContext() + " Error leaving lobby", e);
        }

        this.notifyAccessGranted();

        // The left event is used here in case the lobby is disabled.
        if (this.jvbConference != null)
        {
            this.jvbConference.setLobbyEnabled(false);
            this.jvbConference.joinConferenceRoom();
        }
        else
        {
            logger.error(getCallContext() + " No JVB conference!!!");
        }
    }

    /**
     * Holds call information.
     *
     * @return <tt>CallContext</tt>
     */
    public CallContext getCallContext()
    {
        return this.callContext;
    }

    /**
     * Gets the lobby jid.
     *
     * @return <tt>Jid</tt>
     */
    public Jid getRoomJid()
    {
        return this.roomJid;
    }

    /**
     * Used to which joined.
     *
     * @return <tt>Localpart</tt> identifier.
     */
    public Resourcepart getResourceIdentifier()
    {
        return this.roomJid.getResourceOrNull();
    }

    /**
     * Override this to setup the lobby room before join.
     *
     * @param mucRoom <tt>ChatRoom</tt> lobby to join.
     */
    void setupChatRoom(ChatRoom mucRoom)
    {
        if (mucRoom instanceof ChatRoomJabberImpl)
        {
            String displayName = this.sipGatewaySession.getMucDisplayName();
            if (displayName != null)
            {
                ((ChatRoomJabberImpl)mucRoom).addPresencePacketExtensions(
                        new Nick(displayName));
            }
            else
            {
                logger.error(this.callContext + " No display name to use...");
            }
        }
    }
}
