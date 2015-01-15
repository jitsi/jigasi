/*
 * Jitsi Videobridge, OpenSource video conferencing.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.jigasi;

import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.protocol.event.*;
import net.java.sip.communicator.service.protocol.jabber.*;
import net.java.sip.communicator.service.protocol.media.*;
import net.java.sip.communicator.util.*;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jitsimeet.*;
import net.java.sip.communicator.util.Logger;
import org.jitsi.service.neomedia.*;
import org.jitsi.util.*;
import org.jivesoftware.smack.packet.*;
import org.jivesoftware.smackx.packet.*;
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
 */
public class JvbConference
    implements RegistrationStateChangeListener,
               ServiceListener,
               ChatRoomMemberPresenceListener
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
     * Default status of our participant before we get any state from
     * the <tt>CallPeer</tt>.
     */
    private static final String INIT_STATUS_NAME = "Initializing Call";

    /**
     * {@link GatewaySession} that uses this <tt>JvbConference</tt> instance.
     */
    private final GatewaySession gatewaySession;

    /**
     * Optional secret for password protected MUC rooms.
     */
    private final String roomPassword;

    /**
     * The XMPP account used for the call handled by this instance.
     */
    private AccountID xmppAccount;

    /**
     * The XMPP provider used to join JVB conference.
     */
    private ProtocolProviderService xmppProvider;

    /**
     * Name of MUC chat room hosting JVB conference.
     */
    private final String roomName;

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
    private JvbInviteTimeout inviteTimeout = new JvbInviteTimeout();

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
    private String focusResourceAddr;

    /**
     * Creates new instance of <tt>JvbConference</tt>
     * @param gatewaySession the <tt>GatewaySession</tt> that will be using this
     *                       <tt>JvbConference</tt>.
     * @param roomName name of MUC room that hosts the conference
     * @param roomPassword optional secret for password protected MUC room.
     */
    public JvbConference(GatewaySession gatewaySession, String roomName,
                         String roomPassword)
    {
        this.gatewaySession = gatewaySession;
        this.roomName = roomName;
        this.roomPassword = roomPassword;
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

    private String getDisplayName()
    {
        String mucDisplayName = null;

        String sipDestination = gatewaySession.getDestination();
        Call sipCall = gatewaySession.getSipCall();

        if (sipDestination != null)
        {
            mucDisplayName = sipDestination;
        }
        else if (sipCall != null)
        {
            CallPeer firstPeer = sipCall.getCallPeers().next();
            if (firstPeer != null)
            {
                mucDisplayName = firstPeer.getDisplayName();
            }
        }

        return mucDisplayName;
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
     * Returns XMPP provider used by this <tt>JvbConference</tt>.
     * @return XMPP provider used by this <tt>JvbConference</tt>.
     */
    public ProtocolProviderService getXmppProvider()
    {
        return xmppProvider;
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

        // FIXME: consider using some utility method instead ?
        String callId = gatewaySession.getCallsControl()
            .extractCallIdFromResource(gatewaySession.getCallResource());

        this.xmppProviderFactory
            = ProtocolProviderFactory.getProtocolProviderFactory(
                JigasiBundleActivator.osgiContext,
                ProtocolNames.JABBER);

        this.xmppAccount
            = xmppProviderFactory.createAccount(
                createAccountPropertiesForCallId(
                    gatewaySession.getXmppServerName(), callId));

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
                gatewaySession.getCallResource()
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

        String callResource = gatewaySession.getCallResource();

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
            new RegisterThread(xmppProvider).start();
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
                             + gatewaySession.getCallResource());
        }
        else
        {
            logger.info(
                "XMPP (" + gatewaySession.getCallResource()
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
        OperationSetJitsiMeetTools meetTools
            = xmppProvider.getOperationSet(OperationSetJitsiMeetTools.class);

        meetTools.addSupportedFeature(SIP_GATEWAY_FEATURE_NAME);

        // Remove ICE support from features list ?
        if (JigasiBundleActivator.getConfigurationService()
                .getBoolean(SipGateway.P_NAME_DISABLE_ICE, false))
        {
            meetTools.removeSupportedFeature(
                    "urn:xmpp:jingle:transports:ice-udp:1");

            logger.info("ICE feature will not be advertised");
        }

        OperationSetMultiUserChat muc
            = xmppProvider.getOperationSet(OperationSetMultiUserChat.class);

        try
        {
            logger.info("Joining JVB conference room: " + roomName);

            ChatRoom mucRoom = muc.findRoom(roomName);

            /*
            FIXME: !!!
            if (mucRoom.getMembersCount() == 0)
            {
                logger.error("No focus in the room!");
                stop();
                return;
            }*/

            String callId
                = gatewaySession.getCallsControl()
                        .extractCallIdFromResource(
                                gatewaySession.getCallResource());

            if (StringUtils.isNullOrEmpty(roomPassword))
            {
                mucRoom.joinAs(callId);
            }
            else
            {
                mucRoom.joinAs(callId, roomPassword.getBytes());
            }

            this.mucRoom = mucRoom;

            mucRoom.addMemberPresenceListener(this);

            String displayName = getDisplayName();
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

            setPresenceStatus(INIT_STATUS_NAME);

            gatewaySession.notifyJvbRoomJoined();

            inviteTimeout.scheduleTimeout(
                SipGateway.getJvbInviteTimeout());
        }
        catch (Exception e)
        {
            logger.error(e, e);

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

        jvbCall = null;

        if(started)
        {
            stop();
        }
    }

    private void onJvbCallStarted()
    {
        logger.info("JVB conference call IN_PROGRESS " + roomName);

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

        mucRoom.removeMemberPresenceListener(this);

        mucRoom.leave();

        mucRoom = null;
    }

    @Override
    public void serviceChanged(ServiceEvent serviceEvent)
    {
        if (serviceEvent.getType() != ServiceEvent.REGISTERED)
            return;

        ServiceReference ref = serviceEvent.getServiceReference();

        Object service = JigasiBundleActivator.osgiContext.getService(ref);

        if (!(service instanceof ProtocolProviderService))
            return;

        ProtocolProviderService pps = (ProtocolProviderService) service;

        if (getXmppProvider() == null &&
            ProtocolNames.JABBER.equals(pps.getProtocolName()))
        {
            setXmppProvider(pps);
        }
    }

    @Override
    public void memberPresenceChanged(ChatRoomMemberPresenceChangeEvent evt)
    {
        logger.info("Member presence change: "+evt);

        String eventType = evt.getEventType();

        if (!ChatRoomMemberPresenceChangeEvent.MEMBER_KICKED.equals(eventType)
            && !ChatRoomMemberPresenceChangeEvent.MEMBER_LEFT.equals(eventType)
            && !ChatRoomMemberPresenceChangeEvent.MEMBER_QUIT.equals(eventType))
        {
            return;
        }

        ChatRoomMember member = evt.getChatRoomMember();

        logger.info(
            "Member left : " + member.getRole()
                + " " + member.getContactAddress());

        if (ChatRoomMemberRole.OWNER.equals(member.getRole()) ||
            member.getContactAddress().equals(focusResourceAddr))
        {
            logger.info("Focus left! - stopping");

            stop();
        }
    }

    /**
     * Sends given <tt>extension</tt> in MUC presence update packet.
     * @param extension the packet extension to be included in MUC presence.
     */
    void sendPresenceExtension(PacketExtension extension)
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
        return roomName;
    }

    private class JvbCallListener
        implements CallListener
    {
        @Override
        public void incomingCallReceived(CallEvent event)
        {

            CallPeer focus = event.getSourceCall().getCallPeers().next();
            if (focus == null || focus.getAddress() == null)
            {
                logger.error("Failed to obtain focus peer address");
            }
            else
            {
                String fullAddress = focus.getAddress();
                focusResourceAddr
                    = fullAddress.substring(
                            fullAddress.indexOf("/") + 1);

                logger.info("Got invite from " + focusResourceAddr);
            }

            if (jvbCall != null)
            {
                logger.error(
                    "JVB conference call already started " + hashCode());
                return;
            }

            if (!started || xmppProvider == null)
            {
                logger.error("Instance disposed");
                return;
            }

            inviteTimeout.cancel();

            jvbCall = event.getSourceCall();

            CallPeer peer = jvbCall.getCallPeers().next();
            peer.addCallPeerListener(new CallPeerAdapter()
            {
                @Override
                public void peerStateChanged(CallPeerChangeEvent evt)
                {
                    CallPeer peer = evt.getSourceCallPeer();
                    CallPeerState peerState = peer.getState();
                    logger.info(
                        gatewaySession.getCallResource()
                        + " JVB peer state: " + peerState);

                    if (CallPeerState.CONNECTED.equals(peerState))
                    {
                        advertisePeerSSRCs(peer);
                    }
                }
            });

            jvbCall.addCallChangeListener(callChangeListener);

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
    private static Map<String, String> createAccountPropertiesForCallId(
            String domain,
            String resourceName)
    {
        HashMap<String, String> properties = new HashMap<String, String>();

        String userID = resourceName + "@" + domain;

        properties.put(ProtocolProviderFactory.USER_ID, userID);
        properties.put(ProtocolProviderFactory.SERVER_ADDRESS, domain);
        properties.put(ProtocolProviderFactory.SERVER_PORT, "5222");

        properties.put(ProtocolProviderFactory.RESOURCE, resourceName);
        properties.put(ProtocolProviderFactory.RESOURCE_PRIORITY, "30");

        properties.put(JabberAccountID.ANONYMOUS_AUTH, "true");
        properties.put(ProtocolProviderFactory.IS_CARBON_DISABLED, "true");
        properties.put(ProtocolProviderFactory.DEFAULT_ENCRYPTION, "true");
        properties.put(ProtocolProviderFactory.DEFAULT_SIPZRTP_ATTRIBUTE, "true");
        properties.put(ProtocolProviderFactory.IS_USE_ICE, "true");
        properties.put(ProtocolProviderFactory.IS_USE_GOOGLE_ICE, "true");
        properties.put(ProtocolProviderFactory.IS_ACCOUNT_DISABLED, "false");
        properties.put(ProtocolProviderFactory.IS_PREFERRED_PROTOCOL, "false");
        properties.put(ProtocolProviderFactory.IS_SERVER_OVERRIDDEN, "false");
        properties.put(ProtocolProviderFactory.AUTO_DISCOVER_JINGLE_NODES, "true");
        properties.put(ProtocolProviderFactory.PROTOCOL, ProtocolNames.JABBER);
        properties.put(ProtocolProviderFactory.IS_USE_UPNP, "false");
        properties.put(ProtocolProviderFactory.USE_DEFAULT_STUN_SERVER, "true");

        String overridePrefix = "org.jitsi.jigasi.xmpp.acc";
        List<String> overriddenProps =
            JigasiBundleActivator.getConfigurationService()
                .getPropertyNamesByPrefix(overridePrefix, false);
        for(String prop : overriddenProps)
        {
            properties.put(
                prop.replace(overridePrefix + ".", ""),
                JigasiBundleActivator.getConfigurationService()
                    .getString(prop));
        }

        return properties;
    }

    /**
     * Threads handles the timeout for the waiting for conference call invite
     * sent by the focus.
     */
    class JvbInviteTimeout
        implements Runnable
    {
        private final Object syncRoot = new Object();

        private boolean willCauseTimeout = true;

        private long timeout;

        Thread timeoutThread;

        public void scheduleTimeout(long timeout)
        {
            synchronized (syncRoot)
            {
                this.timeout = timeout;

                if (timeoutThread != null)
                    throw new IllegalStateException("already scheduled");

                timeoutThread = new Thread(this, "JvbInviteTimeout");

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
                        logger.error(
                            "Did not received session invite within "
                                + timeout + " ms");

                        endReason
                            = "No invite from conference focus";
                        endReasonCode
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

        public void cancel()
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
