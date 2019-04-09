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

import net.java.sip.communicator.impl.protocol.jabber.*;
import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.protocol.event.*;
import net.java.sip.communicator.service.protocol.jabber.*;
import net.java.sip.communicator.service.protocol.media.*;
import net.java.sip.communicator.util.Logger;
import net.java.sip.communicator.util.*;
import org.jitsi.jigasi.stats.*;
import org.jitsi.jigasi.util.*;
import org.jitsi.xmpp.extensions.jibri.*;
import org.jitsi.xmpp.extensions.jitsimeet.*;
import org.jitsi.service.configuration.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.utils.*;
import org.jivesoftware.smack.*;
import org.jivesoftware.smack.packet.*;
import org.jivesoftware.smackx.nick.packet.*;
import org.jxmpp.jid.*;
import org.jxmpp.jid.impl.*;
import org.jxmpp.jid.parts.*;
import org.jxmpp.stringprep.*;
import org.osgi.framework.*;

import java.util.*;

/**
 * Class takes care of handling Jitsi Videobridge conference. Currently it waits
 * for the first XMPP provider service to be registered and uses it to join the
 * conference. Once we've joined the focus sends jingle "session-initiate". Next
 * incoming call is accepted which means that we've joined JVB conference.
 * {@link SipGateway} is notified about this fact and it handles it appropriate.
 *
 * @author Pawel Domas
 * @author Nik Vaessen
 */
public class JvbConference
    implements RegistrationStateChangeListener,
               ServiceListener,
               ChatRoomMemberPresenceListener,
               LocalUserChatRoomPresenceListener,
               CallPeerConferenceListener
{
    /**
     * The logger.
     */
    private final static Logger logger = Logger.getLogger(JvbConference.class);

    /**
     * The name of XMPP feature which states for Jigasi SIP Gateway and can be
     * used to recognize gateway client.
     */
    public static final String SIP_GATEWAY_FEATURE_NAME
        = "http://jitsi.org/protocol/jigasi";

    /**
     * The name of XMPP feature for Jingle/DTMF feature (XEP-0181).
     */
    public static final String DTMF_FEATURE_NAME
            = "urn:xmpp:jingle:dtmf:0";

    /**
     * The name of the XMPP feature for rtcpmux.
     */
    public static final String RTCPMUX_FEATURE_NAME
            = "urn:ietf:rfc:5761";

    /**
     * The name of the XMPP feature for bundle.
     */
    public static final String BUNDLE_FEATURE_NAME
            = "urn:ietf:rfc:5888";

    /**
     * The name of the property which can be used to disable advertising of
     * rtcpmux support.
     */
    public static final String P_NAME_DISABLE_RTCPMUX
            = "org.jitsi.jigasi.DISABLE_RTCPMUX";

    /**
     * The name of the property that is used to define whether the SIP user of
     * the incoming/outgoing SIP URI should be used as the XMPP resource or not.
     */
    private static final String P_NAME_USE_SIP_USER_AS_XMPP_RESOURCE
        = "org.jitsi.jigasi.USE_SIP_USER_AS_XMPP_RESOURCE";

    /**
     * The name of the property that is used to define the MUC service address.
     * There are cases when authentication is used the authenticated user is
     * using domain auth.main.domain and the muc service is under
     * conference.main.domain. Then when joining a room without specifying
     * the full address we will try searching using disco info for muc service
     * under the domain auth.main.domain which will fail.
     * We will use this property to fix those cases by manually configuring
     * the address.
     */
    private static final String P_NAME_MUC_SERVICE_ADDRESS
        = "org.jitsi.jigasi.MUC_SERVICE_ADDRESS";

    /**
     * The default bridge id to use.
     */
    public static final String DEFAULT_BRIDGE_ID = "jitsi";

    /**
     * Adds the features supported by jigasi to a specific
     * <tt>OperationSetJitsiMeetTools</tt> instance.
     */
    private static void addSupportedFeatures(
            OperationSetJitsiMeetTools meetTools)
    {
        meetTools.addSupportedFeature(SIP_GATEWAY_FEATURE_NAME);
        meetTools.addSupportedFeature(DTMF_FEATURE_NAME);

        ConfigurationService cfg
                = JigasiBundleActivator.getConfigurationService();

        if (!cfg.getBoolean(P_NAME_DISABLE_RTCPMUX, false))
        {
            // We need to advertise both rtcp-mux and bundle for jicofo to
            // use rtcpmux.
            meetTools.addSupportedFeature(RTCPMUX_FEATURE_NAME);
            meetTools.addSupportedFeature(BUNDLE_FEATURE_NAME);
        }

        // Remove ICE support from features list ?
        if (cfg.getBoolean(SipGateway.P_NAME_DISABLE_ICE, false))
        {
            meetTools.removeSupportedFeature(
                    "urn:xmpp:jingle:transports:ice-udp:1");

            logger.info("ICE feature will not be advertised");
        }
    }

    /**
     * {@link SipGatewaySession} that uses this <tt>JvbConference</tt> instance.
     */
    private final AbstractGatewaySession gatewaySession;

    /**
     * The XMPP account used for the call handled by this instance.
     */
    private AccountID xmppAccount;

    /**
     * The XMPP password used for the call handled by this instance.
     */
    private String xmppPassword;

    /**
     * The XMPP provider used to join JVB conference.
     */
    private ProtocolProviderService xmppProvider;

    /**
     * The call context used to create this conference, contains info as
     * room name and room password and other optional parameters.
     */
    private final CallContext callContext;

    /**
     * <tt>ChatRoom</tt> instance that hosts the conference(not null if joined).
     */
    private ChatRoom mucRoom;

    /**
     * Indicates whether this instance has been started.
     */
    private boolean started;

    /**
     * The call established with JVB conference.
     */
    private Call jvbCall;

    /**
     * Operation set telephony.
     */
    private OperationSetBasicTelephony telephony;

    /**
     * Object listens for incoming calls.
     */
    private final JvbCallListener callListener
        = new JvbCallListener();

    /**
     * Object listens for call state changes.
     */
    private final JvbCallChangeListener callChangeListener
        = new JvbCallChangeListener();

    /**
     * <tt>ProtocolProviderFactory</tt> instance used to manage XMPP accounts.
     */
    private ProtocolProviderFactory xmppProviderFactory;

    /**
     * Handles timeout for the waiting for JVB conference call invite sent by
     * the focus.
     */
    private JvbConferenceStopTimeout
        inviteTimeout = new JvbConferenceStopTimeout(
            "JvbInviteTimeout",
            "No invite from conference focus",
            "Did not received session invite"
        );

    /**
     * Handles timeout for the waiting some participant to join.
     */
    private JvbConferenceStopTimeout
        leaveTimeout = new JvbConferenceStopTimeout(
            "JvbLeaveTimeout",
            "No participant joined for long time",
            "No participant joined for long time"
        );

    /**
     * Call hang up reason string that will be sent to the SIP peer.
     */
    private String endReason;

    /**
     * Call hang up reason code that will be sent to the SIP peer.
     */
    private int endReasonCode;

    /**
     * Address of the focus member that has invited us to the conference.
     * Used to identify the focus user and dispose the session when it leaves
     * the room.
     */
    private final String focusResourceAddr;

    /**
     * Configuration property to change the resource used by focus.
     */
    private static final String FOCUSE_RESOURCE_PROP
        = "org.jitsi.jigasi.FOCUS_RESOURCE";

    /**
     * The stats handler that handles statistics on the jvb side.
     */
    private StatsHandler statsHandler = null;

    /**
     * Creates new instance of <tt>JvbConference</tt>
     * @param gatewaySession the <tt>SipGatewaySession</tt> that will be using
     *                       this <tt>JvbConference</tt>.
     * @param ctx the call context of the current conference
     */
    public JvbConference(AbstractGatewaySession gatewaySession, CallContext ctx)
    {
        this.gatewaySession = gatewaySession;
        this.callContext = ctx;

        focusResourceAddr = JigasiBundleActivator.getConfigurationService()
            .getString(FOCUSE_RESOURCE_PROP, "focus");
    }

    /**
     * Includes info about given <tt>peer</tt> media SSRCs in MUC presence.
     * @param peer the <tt>CallPeer</tt> whose media SSRCs will be advertised.
     */
    private void advertisePeerSSRCs(CallPeer peer)
    {
        String audioSSRC = getPeerSSRCforMedia(peer,
                                               MediaType.AUDIO);
        String videoSSRC = getPeerSSRCforMedia(peer,
                                               MediaType.VIDEO);
        logger.info(
            "Peer " + peer.getState()
                + " SSRCs audio: " + audioSSRC
                + " video: " + videoSSRC);

        MediaPresenceExtension mediaPresence
            = new MediaPresenceExtension();

        if (audioSSRC != null)
        {
            MediaPresenceExtension.Source ssrc
                = new MediaPresenceExtension.Source();

            ssrc.setMediaType(MediaType.AUDIO.toString());
            ssrc.setSSRC(audioSSRC);

            mediaPresence.addChildExtension(ssrc);
        }

        if (videoSSRC != null)
        {
            MediaPresenceExtension.Source ssrc
                = new MediaPresenceExtension.Source();

            ssrc.setMediaType(MediaType.VIDEO.toString());
            ssrc.setSSRC(videoSSRC);

            mediaPresence.addChildExtension(ssrc);
        }

        sendPresenceExtension(mediaPresence);
    }


    private Localpart getResourceIdentifier()
    {
        Localpart resourceIdentifier = null;
        if (JigasiBundleActivator.getConfigurationService()
            .getBoolean(P_NAME_USE_SIP_USER_AS_XMPP_RESOURCE, false))
        {
            // A SIP address or SIP URI is a Uniform Resource Identifier written
            // in user@domain.tld format (semantically, much like an e-mail
            // address). It addresses a specific telephone extension on a voice
            // over IP system (such as a private branch exchange) or an E.164
            // telephone number dialled through a specific gateway.
            //
            // The SIP and SIPS URI schemes are described in RFC 3261, which
            // defines the Session Initiation Protocol.
            //
            // The XMPP RFC isn't clear as to the syntax of the resource
            // identifier string. It states that a resource identifier MUST be
            // formatted such that the Resourceprep profile of [STRINGPREP] can
            // be applied without failing.
            //
            // Given the above uncertainty, we made the decision to replace
            // anything that is not in the this regex class A-Za-z0-9- with a
            // dash.

            String resourceIdentBuilder = gatewaySession.getMucDisplayName();
            if (!StringUtils.isNullOrEmpty(resourceIdentBuilder))
            {
                int idx = resourceIdentBuilder.indexOf('@');
                if (idx != -1)
                {
                    // keep only the user part of the SIP URI.
                    resourceIdentBuilder = resourceIdentBuilder.substring(0, idx);
                }

                // clean it up for resource usage.
                try
                {
                    resourceIdentifier
                        = Localpart.from(
                            resourceIdentBuilder.replace("[^A-Za-z0-9]", "-"));
                }
                catch (XmppStringprepException e)
                {
                    logger.error("The SIP URI is invalid to use an XMPP"
                        + " resource, identifier will be a random string", e);
                }
            }
            else
            {
                logger.info("The SIP URI is empty! The XMPP resource " +
                    "identifier will be a random string.");
            }
        }

        if (resourceIdentifier == null)
        {
            resourceIdentifier =
                callContext.getCallResource().getLocalpartOrNull();
        }

        return resourceIdentifier;
    }

    /**
     * Returns the <tt>ChatRoom</tt> instance that holds the JVB conference
     * handled by this instance or <tt>null</tt> otherwise.
     */
    public ChatRoom getJvbRoom()
    {
        return mucRoom;
    }

    /**
     * Returns local SSRC of media stream sent towards given <tt>peer</tt>.
     * @param peer the peer to whom media is sent.
     * @param mediaType type of media sent.
     */
    private String getPeerSSRCforMedia(CallPeer peer, MediaType mediaType)
    {
        if (!(peer instanceof MediaAwareCallPeer))
            return null;

        MediaAwareCallPeer peerMedia = (MediaAwareCallPeer) peer;

        CallPeerMediaHandler mediaHandler
            = peerMedia.getMediaHandler();
        if (mediaHandler == null)
            return null;

        MediaStream stream = mediaHandler.getStream(mediaType);
        if (stream == null)
            return null;

        return Long.toString(stream.getLocalSourceID());
    }

    /**
     * Start this JVB conference handler.
     */
    public synchronized void start()
    {
        if (started)
        {
            logger.error("Already started !");
            return;
        }

        Localpart resourceIdentifier = getResourceIdentifier();

        this.xmppProviderFactory
            = ProtocolProviderFactory.getProtocolProviderFactory(
                JigasiBundleActivator.osgiContext,
                ProtocolNames.JABBER);

        this.xmppAccount
            = xmppProviderFactory.createAccount(
                    createAccountPropertiesForCallId(
                            callContext,
                            resourceIdentifier.toString()));

        xmppProviderFactory.loadAccount(xmppAccount);

        started = true;

        // Look for first XMPP provider
        Collection<ServiceReference<ProtocolProviderService>> providers
            = ServiceUtils.getServiceReferences(
                    JigasiBundleActivator.osgiContext,
                    ProtocolProviderService.class);

        for (ServiceReference<ProtocolProviderService> serviceRef : providers)
        {
            ProtocolProviderService candidate
                = JigasiBundleActivator.osgiContext.getService(serviceRef);

            if (ProtocolNames.JABBER.equals(candidate.getProtocolName()))
            {
                if (candidate.getAccountID()
                    .getAccountUniqueID()
                    .equals(xmppAccount.getAccountUniqueID()))
                {
                    setXmppProvider(candidate);

                    if (this.xmppProvider != null)
                    {
                        break;
                    }
                }
            }
        }

        if (this.xmppProvider == null)
        {
            // Listen for XMPP provider to be added
            JigasiBundleActivator.osgiContext.addServiceListener(this);
        }
    }

    /**
     * Quits current JVB conference if any.
     */
    public synchronized void stop()
    {
        if (!started)
        {
            logger.error("Already stopped !");
            return;
        }

        started = false;

        JigasiBundleActivator.osgiContext.removeServiceListener(this);

        if (telephony != null)
        {
            telephony.removeCallListener(callListener);
            telephony = null;
        }

        gatewaySession.onJvbConferenceWillStop(this, endReasonCode, endReason);

        if(mucRoom != null)
        {
            leaveConferenceRoom();
        }

        if (jvbCall != null)
        {
            CallManager.hangupCall(jvbCall);
        }

        if (xmppProvider != null)
        {
            xmppProvider.removeRegistrationStateChangeListener(this);

            logger.info(
                callContext.getCallResource()
                    + " is removing account " + xmppAccount);

            xmppProviderFactory.unloadAccount(xmppAccount);

            xmppProviderFactory = null;

            xmppAccount = null;

            xmppProvider = null;
        }

        gatewaySession.onJvbConferenceStopped(this, endReasonCode, endReason);
    }

    /**
     * Sets XMPP provider that will be used by this instance to join JVB
     * conference. It can be set only once. Once set joining conference process
     * is being started.
     * @param xmppProvider XMPP provider that will be used by this instance to
     *                     join JVB conference.
     */
    private synchronized void setXmppProvider(
            ProtocolProviderService xmppProvider)
    {
        if(this.xmppProvider != null)
            throw new IllegalStateException("unexpected");

        Jid callResource = callContext.getCallResource();

        if (!xmppProvider.getAccountID().getAccountUniqueID()
                .equals(xmppAccount.getAccountUniqueID()))
        {

            logger.info(
                callResource + " rejects XMPP provider " + xmppProvider);
            return;
        }

        logger.info(callResource + " will use " + xmppProvider);

        this.xmppProvider = xmppProvider;

        xmppProvider.addRegistrationStateChangeListener(this);

        this.telephony
            = xmppProvider.getOperationSet(OperationSetBasicTelephony.class);

        telephony.addCallListener(callListener);

        if (xmppProvider.isRegistered())
        {
            joinConferenceRoom();
        }
        else
        {
            new RegisterThread(xmppProvider, xmppPassword).start();
        }
    }

    @Override
    public synchronized void registrationStateChanged(
            RegistrationStateChangeEvent evt)
    {
        if (started
            && mucRoom == null
            && evt.getNewState() == RegistrationState.REGISTERED)
        {
            // Join the MUC
            joinConferenceRoom();
        }
        else if (evt.getNewState() == RegistrationState.UNREGISTERED)
        {
            logger.error("Unregistered XMPP on "
                        + callContext.getCallResource());
        }
        else if (evt.getNewState() == RegistrationState.CONNECTION_FAILED)
        {
            logger.error("Connection failed XMPP on "
                        + callContext.getCallResource());
            stop();
        }
        else
        {
            logger.info(
                "XMPP (" + callContext.getCallResource()
                         + "): " + evt.toString());
        }
    }

    /**
     * Returns <tt>true</tt> if we are currently in JVB conference room.
     * @return <tt>true</tt> if we are currently in JVB conference room.
     */
    public boolean isInTheRoom()
    {
        return mucRoom != null && mucRoom.isJoined();
    }

    private void joinConferenceRoom()
    {
        // Advertise gateway feature before joining
        addSupportedFeatures(
                xmppProvider.getOperationSet(OperationSetJitsiMeetTools.class));

        OperationSetMultiUserChat muc
            = xmppProvider.getOperationSet(OperationSetMultiUserChat.class);
        muc.addPresenceListener(this);

        try
        {
            String roomName = callContext.getRoomName();
            if (!roomName.contains("@"))
            {
                // we check for optional muc service
                String mucService
                    = JigasiBundleActivator.getConfigurationService()
                        .getString(P_NAME_MUC_SERVICE_ADDRESS, null);
                if (!StringUtils.isNullOrEmpty(mucService))
                {
                    roomName = roomName + "@" + mucService;
                }
            }
            String roomPassword = callContext.getRoomPassword();

            logger.info("Joining JVB conference room: " + roomName);

            ChatRoom mucRoom = muc.findRoom(roomName);

            // we invite focus and wait for its response
            // to be sure that if it is not in the room, the focus will be the
            // first to join, mimic the web behaviour
            inviteFocus(JidCreate.entityBareFrom(mucRoom.getIdentifier()));

            Localpart resourceIdentifier = getResourceIdentifier();

            if (StringUtils.isNullOrEmpty(roomPassword))
            {
                mucRoom.joinAs(resourceIdentifier.toString());
            }
            else
            {
                mucRoom.joinAs(resourceIdentifier.toString(),
                    roomPassword.getBytes());
            }

            this.mucRoom = mucRoom;

            mucRoom.addMemberPresenceListener(this);

            String displayName = gatewaySession.getMucDisplayName();
            if (displayName != null)
            {
                Nick nick = new Nick(displayName);
                sendPresenceExtension(nick);
            }
            else
            {
                logger.error("No display name to use...");
            }

            // Announce that we're connecting to JVB conference
            // (waiting for invite)
            //sendPresenceExtension(
              //  gatewaySession.createPresenceExtension(
                //    SipGatewayExtension.STATE_CONNECTING_JVB, null));

            if(gatewaySession.getDefaultInitStatus() != null)
            {
                setPresenceStatus(gatewaySession.getDefaultInitStatus());
            }

            gatewaySession.notifyJvbRoomJoined();

            inviteTimeout.scheduleTimeout(
                AbstractGateway.getJvbInviteTimeout());
        }
        catch (Exception e)
        {
            logger.error(e, e);

            // inform that this session had failed
            gatewaySession.getGateway()
                .fireGatewaySessionFailed(gatewaySession);

            stop();
        }
    }

    void setPresenceStatus(String statusMsg)
    {
        if (mucRoom != null)
        {
            // Send presence status update
            OperationSetJitsiMeetTools jitsiMeetTools
                = xmppProvider.getOperationSet(
                    OperationSetJitsiMeetTools.class);

            jitsiMeetTools.setPresenceStatus(mucRoom, statusMsg);
        }
    }

    private void onJvbCallEnded()
    {
        if (jvbCall == null)
        {
            logger.warn("Jvb call already disposed");
            return;
        }

        jvbCall.removeCallChangeListener(callChangeListener);
        jvbCall.removeCallChangeListener(statsHandler);
        statsHandler = null;

        jvbCall = null;

        if(started)
        {
            stop();
        }
    }

    private void onJvbCallStarted()
    {
        logger.info("JVB conference call IN_PROGRESS "
            + callContext.getRoomName());

        OperationSetIncomingDTMF opSet
            = this.xmppProvider.getOperationSet(OperationSetIncomingDTMF.class);
        if (opSet != null)
            opSet.addDTMFListener(gatewaySession);

        Exception error = gatewaySession.onConferenceCallStarted(jvbCall);

        if (error != null)
        {
            logger.error(error, error);
        }
    }

    private void leaveConferenceRoom()
    {
        if (mucRoom == null)
        {
            logger.warn("MUC room is null");
            return;
        }

        OperationSetIncomingDTMF opSet
            = this.xmppProvider.getOperationSet(OperationSetIncomingDTMF.class);
        if (opSet != null)
            opSet.removeDTMFListener(gatewaySession);

        OperationSetMultiUserChat muc
            = xmppProvider.getOperationSet(OperationSetMultiUserChat.class);
        muc.removePresenceListener(this);

        mucRoom.removeMemberPresenceListener(this);

        mucRoom.leave();

        mucRoom = null;
    }

    @Override
    public void serviceChanged(ServiceEvent serviceEvent)
    {
        if (serviceEvent.getType() != ServiceEvent.REGISTERED)
            return;

        ServiceReference<?> ref = serviceEvent.getServiceReference();

        Object service = JigasiBundleActivator.osgiContext.getService(ref);

        if (!(service instanceof ProtocolProviderService))
            return;

        ProtocolProviderService pps = (ProtocolProviderService) service;

        if (xmppProvider == null &&
            ProtocolNames.JABBER.equals(pps.getProtocolName()))
        {
            setXmppProvider(pps);
        }
    }

    @Override
    public void memberPresenceChanged(ChatRoomMemberPresenceChangeEvent evt)
    {
        if (logger.isTraceEnabled())
        {
            logger.trace("Member presence change: " + evt);
        }

        ChatRoomMember member = evt.getChatRoomMember();
        String eventType = evt.getEventType();

        if (!ChatRoomMemberPresenceChangeEvent.MEMBER_KICKED.equals(eventType)
            && !ChatRoomMemberPresenceChangeEvent.MEMBER_LEFT.equals(eventType)
            && !ChatRoomMemberPresenceChangeEvent.MEMBER_QUIT.equals(eventType))
        {
            if (ChatRoomMemberPresenceChangeEvent.MEMBER_JOINED
                    .equals(eventType))
            {
                leaveTimeout.cancel();

                gatewaySession.notifyChatRoomMemberJoined(member);
            }
            else if(ChatRoomMemberPresenceChangeEvent.MEMBER_UPDATED
                    .equals(eventType))
            {
                if (member instanceof ChatRoomMemberJabberImpl)
                {
                    Presence presence
                        = ((ChatRoomMemberJabberImpl) member).getLastPresence();

                    gatewaySession.notifyChatRoomMemberUpdated(member, presence);

                    RecordingStatus rs = presence.getExtension(
                        RecordingStatus.ELEMENT_NAME,
                        RecordingStatus.NAMESPACE);

                    if (rs != null
                        && focusResourceAddr.equals(
                            presence.getFrom().getResourceOrEmpty().toString()))
                    {
                        gatewaySession.notifyRecordingStatusChanged(
                            rs.getRecordingMode(), rs.getStatus());
                    }
                }


            }

            return;
        }
        else
        {
            gatewaySession.notifyChatRoomMemberLeft(member);
            logger.info(
                    "Member left : " + member.getRole()
                            + " " + member.getContactAddress());
        }

        // if it is the focus leaving
        if (member.getName().equals(focusResourceAddr))
        {
            logger.info("Focus left! - stopping");
            stop();

            return;
        }

        // if it is us and the focus
        if (evt.getChatRoom().getMembersCount() == 2)
        {
            long leaveTime = AbstractGateway.getLeaveTimeout();
            if (leaveTime > 0)
            {
                leaveTimeout.scheduleTimeout(leaveTime);
            }
            else
            {
                stop();
            }
        }
    }

    /**
     * Handles when user is kicked to stop the conference.
     * @param evt the event
     */
    @Override
    public void localUserPresenceChanged(
        LocalUserChatRoomPresenceChangeEvent evt)
    {
        if (evt.getChatRoom().equals(JvbConference.this.mucRoom)
            && Objects.equals(evt.getEventType(),
                    LocalUserChatRoomPresenceChangeEvent.LOCAL_USER_KICKED))
        {
            this.stop();
        }
    }

    /**
     * Sends given <tt>extension</tt> in MUC presence update packet.
     * @param extension the packet extension to be included in MUC presence.
     */
    void sendPresenceExtension(ExtensionElement extension)
    {
        if (mucRoom != null)
        {
            // Send presence update
            OperationSetJitsiMeetTools jitsiMeetTools
                = xmppProvider.getOperationSet(
                    OperationSetJitsiMeetTools.class);

            jitsiMeetTools.sendPresenceExtension(mucRoom, extension);
        }
    }

    /**
     * Returns the name of the chat room that holds JVB conference in which this
     * instance is participating.
     * @return the name of the chat room that holds JVB conference in which this
     * instance is participating.
     */
    public String getRoomName()
    {
        return callContext.getRoomName();
    }

    /**
     * Returns the URL of the meeting
     *
     * @return the URL of the meeting
     */
    public String getMeetingUrl()
    {

        return callContext.getMeetingUrl();
    }

    private class JvbCallListener
        implements CallListener
    {
        @Override
        public void incomingCallReceived(CallEvent event)
        {
            CallPeer peer = event.getSourceCall().getCallPeers().next();
            String peerAddress;
            if (peer == null || peer.getAddress() == null)
            {
                logger.error("Failed to obtain focus peer address");
                peerAddress = null;
            }
            else
            {
                String fullAddress = peer.getAddress();
                peerAddress
                    = fullAddress.substring(
                            fullAddress.indexOf("/") + 1);

                logger.info("Got invite from " + peerAddress);
            }

            if (peerAddress == null || !peerAddress.equals(focusResourceAddr))
            {
                logger.warn("Calls not initiated from focus are not allowed");

                CallManager.hangupCall(event.getSourceCall(),
                    403, "Only calls from focus allowed");
                return;
            }

            if (jvbCall != null)
            {
                logger.error(
                    "JVB conference call already started " + hashCode());
                CallManager.hangupCall(event.getSourceCall(),
                    200, "Call completed elsewhere");
                return;
            }

            if (!started || xmppProvider == null)
            {
                logger.error("Instance disposed");
                return;
            }

            inviteTimeout.cancel();

            jvbCall = event.getSourceCall();
            jvbCall.setData(CallContext.class, callContext);

            if(jvbCall != null)
            {
                CallPeer peerToAdd = jvbCall.getCallPeers().next();
                if (peerToAdd != null)
                {
                    peerToAdd.addCallPeerConferenceListener(JvbConference.this);
                }
                else
                {
                    logger.warn("Could not add JvbConference as " +
                        "CallPeerConferenceListener because CallPeer is" +
                        " null");
                }
            }
            else
            {
                logger.warn("Could not add JvbConference as " +
                    "CallPeerConferenceListener because jvbCall is" +
                    " null");            }

            // disable hole punching jvb
            if (peer instanceof MediaAwareCallPeer)
            {
                ((MediaAwareCallPeer)peer).getMediaHandler()
                    .setDisableHolePunching(true);
            }

            peer.addCallPeerListener(new CallPeerAdapter()
            {
                @Override
                public void peerStateChanged(CallPeerChangeEvent evt)
                {
                    CallPeer peer = evt.getSourceCallPeer();
                    CallPeerState peerState = peer.getState();
                    logger.info(
                        callContext.getCallResource()
                        + " JVB peer state: " + peerState);

                    if (CallPeerState.CONNECTED.equals(peerState))
                    {
                        advertisePeerSSRCs(peer);
                    }
                }
            });

            jvbCall.addCallChangeListener(callChangeListener);

            if (statsHandler == null)
            {
                statsHandler = new StatsHandler(DEFAULT_BRIDGE_ID);
            }
            jvbCall.addCallChangeListener(statsHandler);

            gatewaySession.onConferenceCallInvited(jvbCall);

            // Accept incoming jingle call
            CallManager.acceptCall(jvbCall);
        }

        @Override
        public void outgoingCallCreated(CallEvent event) { }

        @Override
        public void callEnded(CallEvent event) { }
    }

    private class JvbCallChangeListener
        extends CallChangeAdapter
    {
        @Override
        public synchronized void callStateChanged(CallChangeEvent evt)
        {
            if (jvbCall != evt.getSourceCall())
            {
                logger.error(
                    "Call change event for different call ? "
                        + evt.getSourceCall() + " : " + jvbCall);
                return;
            }

            // Once call is started notify SIP gateway
            if (jvbCall.getCallState() == CallState.CALL_IN_PROGRESS)
            {
                onJvbCallStarted();
            }
            else if(jvbCall.getCallState() == CallState.CALL_ENDED)
            {
                onJvbCallEnded();
            }
        }
    }

    /**
     * FIXME: temporary
     */
    private Map<String, String> createAccountPropertiesForCallId(
            CallContext ctx,
            String resourceName)
    {
        HashMap<String, String> properties = new HashMap<>();

        String userID = resourceName + "@" + ctx.getDomain();

        properties.put(ProtocolProviderFactory.USER_ID, userID);
        properties.put(ProtocolProviderFactory.SERVER_ADDRESS, ctx.getDomain());
        properties.put(ProtocolProviderFactory.SERVER_PORT, "5222");

        properties.put(ProtocolProviderFactory.RESOURCE, resourceName);
        properties.put(ProtocolProviderFactory.AUTO_GENERATE_RESOURCE, "false");
        properties.put(ProtocolProviderFactory.RESOURCE_PRIORITY, "30");

        // XXX(gp) we rely on the very useful "override" mechanism (see bellow)
        // to "implement" login authentication.
        properties.put(JabberAccountID.ANONYMOUS_AUTH, "true");
        properties.put(ProtocolProviderFactory.IS_CARBON_DISABLED, "true");
        properties.put(ProtocolProviderFactory.DEFAULT_ENCRYPTION, "true");
        properties.put(ProtocolProviderFactory.DEFAULT_SIPZRTP_ATTRIBUTE,
            "false");
        properties.put(ProtocolProviderFactory.IS_USE_ICE, "true");
        properties.put(ProtocolProviderFactory.IS_ACCOUNT_DISABLED, "false");
        properties.put(ProtocolProviderFactory.IS_PREFERRED_PROTOCOL, "false");
        properties.put(ProtocolProviderFactory.IS_SERVER_OVERRIDDEN, "false");
        properties.put(ProtocolProviderFactory.AUTO_DISCOVER_JINGLE_NODES,
            "false");
        properties.put(ProtocolProviderFactory.PROTOCOL, ProtocolNames.JABBER);
        properties.put(ProtocolProviderFactory.IS_USE_UPNP, "false");
        properties.put(ProtocolProviderFactory.USE_DEFAULT_STUN_SERVER, "true");
        properties.put(ProtocolProviderFactory.ENCRYPTION_PROTOCOL
            + ".DTLS-SRTP", "0");
        properties.put(ProtocolProviderFactory.ENCRYPTION_PROTOCOL_STATUS
            + ".DTLS-SRTP", "true");

        AbstractGateway gw = gatewaySession.getGateway();
        String overridePrefix = "org.jitsi.jigasi.xmpp.acc";
        List<String> overriddenProps =
            JigasiBundleActivator.getConfigurationService()
                .getPropertyNamesByPrefix(overridePrefix, false);

        if(gw instanceof SipGateway
            && Boolean.valueOf(
                ((SipGateway) gw).getSipAccountProperty("PREVENT_AUTH_LOGIN")))
        {
            // if we do not want auth login we need to ignore custom USER_ID,
            //PASS, ANONYMOUS_AUTH, ALLOW_NON_SECURE and leave defaults
            overriddenProps.remove(overridePrefix
                + "." + ProtocolProviderFactory.USER_ID);
            overriddenProps.remove(overridePrefix + ".PASS");
            overriddenProps.remove(overridePrefix
                + "." + JabberAccountID.ANONYMOUS_AUTH);
            overriddenProps.remove(overridePrefix
                + "." + ProtocolProviderFactory.IS_ALLOW_NON_SECURE);
        }

        for(String overridenProp : overriddenProps)
        {
            String key = overridenProp.replace(overridePrefix + ".", "");
            String value = JigasiBundleActivator.getConfigurationService()
                .getString(overridenProp);

            // The key for the password field can't end in PASSWORD, otherwise
            // it is encrypted by our configuration service implementation.
            if ("org.jitsi.jigasi.xmpp.acc.PASS".equals(overridenProp))
            {
                // The password is fully managed (i.e. stored/retrieved) by the
                // configuration service and credentials storage service. See
                // the
                //
                //     ProtocolProviderFactory#loadPassword()
                //
                // method. The problem with dynamic XMPP accounts is that they
                // *don't* exist in the configuration, unless we explicitly
                // store them using the
                //
                //     ProtocolProviderFactory#storeAccount()
                //
                // method. Simply loading an account using the
                //
                //     ProtocolProviderFactory#loadAccount()
                //
                // method can't (and doesn't) work, at least not without
                // changing the implementation of the loadAccount method..
                //
                // To avoid to have to store the dynamic accounts in the
                // configuration and, consequently, to have to manage them, to
                // have remove them later, etc. (also NOTE that storing an
                // account WRITES the configuration file), we read the password
                // from a custom key (and *not* from the standard password key,
                // otherwise it gets encrypted by the configuration service, see
                // the comment above) and then we feed it (the password) to the
                // new ServerSecurityAuthority that we create when we register
                // the account. The
                //
                //     ServerSecurityAuthority#obtainCredentials
                //
                // method is called when there no password for a specific
                // account and there we can alter the connection credentials.

                this.xmppPassword = value;
            }
            else if ("org.jitsi.jigasi.xmpp.acc.BOSH_URL_PATTERN"
                        .equals(overridenProp))
            {
                // do not override boshURL with the global setting if
                // we already have a value
                if (StringUtils.isNullOrEmpty(ctx.getBoshURL()))
                    ctx.setBoshURL(value);
            }
            else
            {
                properties.put(key, value);
            }
        }

        String boshUrl = ctx.getBoshURL();
        if (!StringUtils.isNullOrEmpty(boshUrl))
        {
            String roomName = callContext.getRoomName();
            boshUrl = boshUrl.replace(
                "{roomName}",
                // if room name contains @ part, make sure we remove it
                roomName.contains("@") ?
                    Util.extractCallIdFromResource(roomName)
                    : roomName);
            properties.put(JabberAccountID.BOSH_URL, boshUrl);
        }

        // Necessary when doing authenticated XMPP login, otherwise the dynamic
        // accounts get assigned the same ACCOUNT_UID which leads to problems.
        String accountUID = "Jabber:" + userID + "/" + resourceName;
        properties.put(ProtocolProviderFactory.ACCOUNT_UID, accountUID);

        // Because some AbstractGatewaySessions needs access to the audio,
        // we can't always use translator
        if(!gatewaySession.isTranslatorSupported())
        {
            properties.put(ProtocolProviderFactory.USE_TRANSLATOR_IN_CONFERENCE,
                "false");
        }

        return properties;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void conferenceFocusChanged(CallPeerConferenceEvent conferenceEvent)
    {
        //we don't care?
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void conferenceMemberAdded(CallPeerConferenceEvent conferenceEvent)
    {
        ConferenceMember conferenceMember
            = conferenceEvent.getConferenceMember();

        this.gatewaySession.notifyConferenceMemberJoined(conferenceMember);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void conferenceMemberErrorReceived(CallPeerConferenceEvent conferenceEvent)
    {
        //we don't care?
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void conferenceMemberRemoved(CallPeerConferenceEvent conferenceEvent)
    {
        ConferenceMember conferenceMember
            = conferenceEvent.getConferenceMember();

        this.gatewaySession.notifyConferenceMemberLeft(conferenceMember);
    }

    /**
     * Sends invite to jicofo to join a room.
     *
     * @param roomIdentifier the room to join
     */
    private void inviteFocus(final EntityBareJid roomIdentifier)
    {
        if (callContext == null || callContext.getDomain() == null)
        {
            logger.error("No domain name info to use for inviting focus!"
                + " Please set DOMAIN_BASE to the sip account.");
            return;
        }

        ConferenceIq focusInviteIQ = new ConferenceIq();
        focusInviteIQ.setRoom(roomIdentifier);

        // FIXME: uses hardcoded values that are currently used in production
        // we need to configure them or retrieve them in the future
        focusInviteIQ.addProperty("channelLastN", "-1");
        focusInviteIQ.addProperty("disableRtx", "false");
        focusInviteIQ.addProperty("startBitrate", "800");
        focusInviteIQ.addProperty("openSctp", "true");

        try
        {
            focusInviteIQ.setType(IQ.Type.set);
            focusInviteIQ.setTo(JidCreate.domainBareFrom(
                focusResourceAddr + "." + callContext.getDomain()));
        }
        catch (XmppStringprepException e)
        {
            logger.error(
                "Could not create destination address for focus invite", e);
            return;
        }

        // this check just skips an exception when running tests
        if (xmppProvider instanceof ProtocolProviderServiceJabberImpl)
        {
            StanzaCollector collector = null;
            try
            {
                collector = ((ProtocolProviderServiceJabberImpl) xmppProvider)
                    .getConnection()
                    .createStanzaCollectorAndSend(focusInviteIQ);
                collector.nextResultOrThrow();
            }
            catch (SmackException
                | XMPPException.XMPPErrorException
                | InterruptedException e)
            {
                logger.error(
                    "Could not invite the focus to the conference", e);
            }
            finally
            {
                if (collector != null)
                {
                    collector.cancel();
                }
            }
        }
    }

    /**
     * Threads handles the timeout for stopping the conference.
     * For waiting for conference call invite sent by the focus or for waiting
     * another participant to joins.
     */
    class JvbConferenceStopTimeout
        implements Runnable
    {
        private final Object syncRoot = new Object();

        private boolean willCauseTimeout = true;

        private long timeout;

        Thread timeoutThread;

        private String errorLog = null;
        private final String endReason;
        private final String name;

        JvbConferenceStopTimeout(String name, String reason, String errorLog)
        {
            this.name = name;
            this.endReason = reason;
            this.errorLog = errorLog;
        }

        /**
         * Schedules a new timeout thread if not already scheduled.
         *
         * @param timeout the milliseconds to wait before we stop the conference
         * if not canceled.
         */
        void scheduleTimeout(long timeout)
        {
            synchronized (syncRoot)
            {
                if (timeoutThread != null)
                {
                    return;
                }

                this.timeout = timeout;

                timeoutThread = new Thread(this, name);
                willCauseTimeout = true;
                timeoutThread.start();
            }
        }

        @Override
        public void run()
        {
            synchronized (syncRoot)
            {
                try
                {
                    syncRoot.wait(timeout);

                    if (willCauseTimeout)
                    {
                        logger.error(errorLog + " (" + timeout + " ms)");

                        JvbConference.this.endReason = this.endReason;
                        JvbConference.this.endReasonCode
                            = OperationSetBasicTelephony.HANGUP_REASON_TIMEOUT;

                        stop();
                    }
                }
                catch (InterruptedException e)
                {
                    Thread.currentThread().interrupt();
                }
            }
        }

        void cancel()
        {
            synchronized (syncRoot)
            {
                willCauseTimeout = false;

                if (timeoutThread == null)
                    return;

                syncRoot.notifyAll();
            }

            try
            {
                timeoutThread.join();

                timeoutThread = null;
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
            }
        }
    }
}
