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
package org.jitsi.jigasi;

import net.java.sip.communicator.impl.protocol.jabber.*;
import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.protocol.event.*;
import net.java.sip.communicator.service.protocol.jabber.*;
import net.java.sip.communicator.service.protocol.media.*;
import net.java.sip.communicator.util.*;
import net.java.sip.communicator.util.osgi.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.utils.*;
import org.jitsi.impl.neomedia.*;
import org.jitsi.jigasi.lobby.*;
import org.jitsi.jigasi.sip.*;
import org.jitsi.jigasi.stats.*;
import org.jitsi.jigasi.util.*;
import org.jitsi.jigasi.version.*;
import org.jitsi.jigasi.xmpp.extensions.*;
import org.jitsi.utils.*;
import org.jitsi.utils.logging.Logger;
import org.jitsi.utils.queue.*;
import org.jitsi.xmpp.extensions.*;
import org.jitsi.xmpp.extensions.colibri.*;
import org.jitsi.xmpp.extensions.jitsimeet.*;
import org.jitsi.service.configuration.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.xmpp.extensions.rayo.*;
import org.jitsi.xmpp.extensions.visitors.*;
import org.jivesoftware.smack.*;
import org.jivesoftware.smack.bosh.*;
import org.jivesoftware.smack.filter.*;
import org.jivesoftware.smack.iqrequest.*;
import org.jivesoftware.smack.packet.*;
import org.jivesoftware.smackx.disco.*;
import org.jivesoftware.smackx.disco.packet.*;
import org.jivesoftware.smackx.muc.packet.*;
import org.jivesoftware.smackx.nick.packet.*;
import org.jivesoftware.smackx.xdata.packet.*;
import org.jivesoftware.smackx.xdata.*;
import org.json.simple.*;
import org.json.simple.parser.*;
import org.jxmpp.jid.*;
import org.jxmpp.jid.impl.*;
import org.jxmpp.jid.parts.*;
import org.jxmpp.stringprep.*;
import org.osgi.framework.*;

import java.beans.*;
import java.io.*;
import java.net.*;
import java.util.*;

import static net.java.sip.communicator.service.protocol.event.LocalUserChatRoomPresenceChangeEvent.*;
import static org.jivesoftware.smack.packet.StanzaError.Condition.*;

import static org.jitsi.jigasi.lobby.Lobby.*;
import static org.jitsi.jigasi.TranscriptionGatewaySession.*;
import static org.jitsi.jigasi.util.Util.*;


/**
 * Class takes care of handling Jitsi Videobridge conference. Currently, it waits
 * for the first XMPP provider service to be registered and uses it to join the
 * conference. Once we've joined the focus sends jingle "session-initiate". Next
 * incoming call is accepted which means that we've joined JVB conference.
 * {@link SipGateway} is notified about this fact, and it handles it appropriate.
 *
 * @author Pawel Domas
 * @author Nik Vaessen
 */
public class JvbConference
    implements RegistrationStateChangeListener,
               ServiceListener,
               ChatRoomMemberPresenceListener,
               LocalUserChatRoomPresenceListener,
               CallPeerConferenceListener,
               PropertyChangeListener,
               OperationSetJitsiMeetTools.JitsiMeetRequestListener
{
    /**
     * The logger.
     */
    private final static Logger logger = Logger.getLogger(JvbConference.class);

    /**
     * The name of XMPP feature for Jingle/DTMF feature (XEP-0181).
     */
    public static final String DTMF_FEATURE_NAME
            = "urn:xmpp:jingle:dtmf:0";

    /**
     * The name of the property that is used to define whether the SIP user of
     * the incoming/outgoing SIP URI should be used as the XMPP resource or not.
     */
    private static final String P_NAME_USE_SIP_USER_AS_XMPP_RESOURCE
        = "org.jitsi.jigasi.USE_SIP_USER_AS_XMPP_RESOURCE";

    /**
     * The name of the property that is used to define whether the
     * max occupant limit reach is notified or not.
     */
    private static final String P_NAME_NOTIFY_MAX_OCCUPANTS
        = "org.jitsi.jigasi.NOTIFY_MAX_OCCUPANTS";

    /**
     * The name of the property which enables dropping jigasi calls
     * without anyone on the web.
     */
    public static final String P_NAME_ALLOW_ONLY_JIGASIS_IN_ROOM
        = "org.jitsi.jigasi.ALLOW_ONLY_JIGASIS_IN_ROOM";

    /**
     * The name of the property which configured the local region.
     */
    public static final String LOCAL_REGION_PNAME
        = "org.jitsi.jigasi.LOCAL_REGION";

    /**
     * The name of the (unique) meeting id field in the MUC data form.
     */
    private static final String DATA_FORM_MEETING_ID_FIELD_NAME = "muc#roominfo_meetingId";

    /**
     * The milliseconds to wait before check the jvb side of the call for activity.
     */
    private static final int JVB_ACTIVITY_CHECK_DELAY = 5000;

    /**
     * A timer which will be used to schedule a quick non-blocking check whether there is any activity
     * on the bridge side of the call.
     */
    private static final Timer checkReceivedMediaTimer = new Timer();

    /**
     * Handles all the audio mute/unmute logic.
     */
    private final AudioModeration audioModeration;

    /**
     * The (unique) meeting id of this conference.
     */
    private String meetingId;

    /**
     * A queue used to offload xmpp execution in a new thread to avoid blocking xmpp threads,
     * by executing the tasks in new thread
     */
    public static final PacketQueue<Runnable> xmppInvokeQueue = new PacketQueue<>(
        Integer.MAX_VALUE,
        false,
        "xmpp-invoke-queue",
        r -> {
            // do process and try
            try
            {
                r.run();

                return true;
            }
            catch (Throwable e)
            {
                logger.error("Error processing xmpp queue item", e);

                return false;
            }
        },
        Util.createNewThreadPool("xmpp-executor-pool")
    );

    /**
     * Used for randomizing usernames if needed.
     */
    private static final Random RANDOM = new Random();

    /**
     * Adds the features supported by jigasi to a specific
     * <tt>OperationSetJitsiMeetTools</tt> instance.
     * @return Returns the 'features' extension element that can be added to presence.
     */
    private ExtensionElement addSupportedFeatures(
            OperationSetJitsiMeetToolsJabber meetTools)
    {
        FeaturesExtension features = new FeaturesExtension();

        meetTools.addSupportedFeature(JIGASI_FEATURE_NAME);
        features.addChildExtension(Util.createFeature(JIGASI_FEATURE_NAME));

        if (this.isTranscriber)
        {
            meetTools.addSupportedFeature(TRANSCRIBER_FEATURE_NAME);
            features.addChildExtension(Util.createFeature(TRANSCRIBER_FEATURE_NAME));
        }
        else
        {
            // dtmf is used only when sip calling
            meetTools.addSupportedFeature(DTMF_FEATURE_NAME);
            features.addChildExtension(Util.createFeature(DTMF_FEATURE_NAME));
        }

        ConfigurationService cfg = JigasiBundleActivator.getConfigurationService();

        // Remove ICE support from features list ?
        if (cfg.getBoolean(SipGateway.P_NAME_DISABLE_ICE, false))
        {
            meetTools.removeSupportedFeature("urn:xmpp:jingle:transports:ice-udp:1");

            logger.info("ICE feature will not be advertised");
        }

        ExtensionElement audioMuteFeature = AudioModeration.getSupportedFeatures(meetTools);
        if (audioMuteFeature != null)
        {
            features.addChildExtension(audioMuteFeature);
        }

        return features;
    }

    /**
     * {@link AbstractGatewaySession} that uses this <tt>JvbConference</tt>
     * instance.
     */
    private final AbstractGatewaySession gatewaySession;

    /**
     * Whether Jigasi will join as transcriber.
     */
    private final boolean isTranscriber;

    /**
     * Whether to auto stop when only jigasi are left in the room.
     */
    private final boolean allowOnlyJigasiInRoom;

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
     * Synchronizes the write access to {@code jvbCall}.
     */
    private final Object jvbCallWriteSync = new Object();

    /**
     * Operation set telephony.
     */
    private OperationSetBasicTelephony telephony;

    /**
     * Operation set Jitsi Meet.
     */
    private OperationSetJitsiMeetTools jitsiMeetTools = null;

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
     * If we are left alone in the room we can keep the gw session till someone
     * joins, but still if none joined and no jingle session was initiated
     * for some time we want to drop the call.
     */
    private final JvbConferenceStopTimeout inviteTimeout = new JvbConferenceStopTimeout(
        "JvbInviteTimeout",
        "No invite from conference focus",
        "Did not received session invite"
    );

    /**
     * The last status we sent in the conference using setPresenceStatus.
     */
    private String jvbParticipantStatus = null;

    /**
     * Synchronizes the write access to {@code jvbParticipantStatus}.
     */
    private final Object statusSync = new Object();

    /**
     * Call hangs up reason string that will be sent to the SIP peer.
     */
    private String endReason;

    /**
     * Call hangs up reason code that will be sent to the SIP peer.
     */
    private int endReasonCode;

    /**
     * Whether we had sent indication that connection had failed
     * for this conference.
     */
    private boolean connFailedStatsSent = false;

    /**
     * Whether we had sent indication that XMPP connection terminated and
     * the gateway session waiting for new XMPP calls to be connected.
     */
    private boolean gwSesisonWaitingStatsSent = false;

    /**
     * The lobby room instance for the room if any.
     */
    private Lobby lobby = null;

    /**
     * Keeps the state where lobby is currently enabled in this room.
     */
    private boolean lobbyEnabled = false;

    /**
     * Whether single moderator is enabled and set in room config.
     */
    private boolean singleModeratorEnabled = false;

    /**
     * Listens for room configuration changes and request room config to reflect it locally.
     */
    private final RoomConfigurationChangeListener roomConfigurationListener = new RoomConfigurationChangeListener();

    /**
     * Listens for messages from room metadata component for changes in room metadata.
     */
    private final RoomMetadataListener roomMetadataListener = new RoomMetadataListener();

    /**
     * The features for the current xmpp provider we will use later adding to the room presence we send.
     */
    private ExtensionElement features = null;

    /**
     * Whether we are currently joining as a visitor.
     */
    private boolean isVisitor = false;

    /**
     * Listens for visitor IQs, waiting for promotion responses.
     */
    private VisitorIqHandler visitorIqHandler = null;

    /**
     * Whether to skip inviting focus on the next attempt to join a room.
     */
    private boolean skipFocus = false;

    /**
     * We store bosh connection and room before joining as a visitor, to be able
     * to restore them when promoting back to main room.
     */
    private String oldRoom = null;
    private String oldBosh = null;

    /**
     * Creates new instance of <tt>JvbConference</tt>
     * @param gatewaySession the <tt>AbstractGatewaySession</tt> that will be
     *                       using this <tt>JvbConference</tt>.
     * @param ctx the call context of the current conference
     */
    public JvbConference(AbstractGatewaySession gatewaySession, CallContext ctx)
    {
        this.gatewaySession = gatewaySession;
        this.isTranscriber = this.gatewaySession instanceof TranscriptionGatewaySession;
        this.callContext = ctx;
        this.allowOnlyJigasiInRoom = JigasiBundleActivator.getConfigurationService()
            .getBoolean(P_NAME_ALLOW_ONLY_JIGASIS_IN_ROOM, true);

        if (this.isTranscriber)
        {
            this.audioModeration = null;
        }
        else
        {
            this.audioModeration = new AudioModeration(this, (SipGatewaySession)this.gatewaySession, this.callContext);
        }
    }

    public AudioModeration getAudioModeration()
    {
        return audioModeration;
    }

    public Localpart getResourceIdentifier()
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
            // anything that is not in this regex class A-Za-z0-9- with a dash.

            String resourceIdentBuilder = gatewaySession.getMucDisplayName();
            if (StringUtils.isNotEmpty(resourceIdentBuilder))
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
                    logger.error(this.callContext
                        + " The SIP URI is invalid to use an XMPP"
                        + " resource, identifier will be a random string", e);
                }
            }
            else
            {
                logger.info(this.callContext
                    + " The SIP URI is empty! The XMPP resource "
                    + "identifier will be a random string.");
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
     * Start this JVB conference handler.
     */
    public synchronized void start()
    {
        if (started)
        {
            logger.error(this.callContext + " Already started !");
            return;
        }
        logger.info(this.callContext + " Starting JVB conference room: " + this.callContext.getRoomJid());

        Localpart resourceIdentifier = getResourceIdentifier();

        this.createAndLoadAccount(createAccountPropertiesForCallId(resourceIdentifier.toString()));

        if (this.xmppProvider == null)
        {
            // Listen for XMPP provider to be added
            JigasiBundleActivator.osgiContext.addServiceListener(this);
        }
    }

    private void createAndLoadAccount(Map<String, String> accountProperties)
    {
        this.xmppProviderFactory = ProtocolProviderFactory.getProtocolProviderFactory(
            JigasiBundleActivator.osgiContext, ProtocolNames.JABBER);

        this.xmppAccount = xmppProviderFactory.createAccount(accountProperties);

        xmppProviderFactory.loadAccount(xmppAccount);

        started = true;

        // Look for first XMPP provider
        Collection<ServiceReference<ProtocolProviderService>> providers = ServiceUtils.getServiceReferences(
            JigasiBundleActivator.osgiContext, ProtocolProviderService.class);

        for (ServiceReference<ProtocolProviderService> serviceRef : providers)
        {
            ProtocolProviderService candidate = JigasiBundleActivator.osgiContext.getService(serviceRef);

            if (ProtocolNames.JABBER.equals(candidate.getProtocolName()))
            {
                if (candidate.getAccountID().getAccountUniqueID().equals(xmppAccount.getAccountUniqueID()))
                {
                    setXmppProvider(candidate);

                    if (this.xmppProvider != null)
                    {
                        break;
                    }
                }
            }
        }
    }

    /**
     * Quits current JVB conference if any.
     */
    public synchronized void stop()
    {
        if (!started)
        {
            logger.error(this.callContext + " Already stopped !");
            return;
        }

        started = false;

        JigasiBundleActivator.osgiContext.removeServiceListener(this);

        if (telephony != null)
        {
            telephony.removeCallListener(callListener);
            telephony = null;
        }

        if (this.audioModeration != null)
        {
            this.audioModeration.clean();
            this.audioModeration.cleanXmppProvider();
        }

        if (this.visitorIqHandler != null)
        {
            XMPPConnection connection = this.getConnection();
            if (connection != null)
            {
                connection.unregisterIQRequestHandler(this.visitorIqHandler);
                this.visitorIqHandler = null;
            }
        }

        gatewaySession.onJvbConferenceWillStop(this, endReasonCode, endReason);

        leaveConferenceRoom();

        if (jvbCall != null)
        {
            CallManager.hangupCall(jvbCall, true);
        }

        if (xmppProvider != null)
        {
            xmppProvider.removeRegistrationStateChangeListener(this);

            // in case we were not able to create jvb call, unit tests case
            if (jvbCall == null)
            {
                logger.info(
                    callContext + " Removing account " + xmppAccount);

                xmppProviderFactory.unloadAccount(xmppAccount);
            }

            xmppProviderFactory = null;

            xmppAccount = null;

            xmppProvider = null;
        }

        gatewaySession.onJvbConferenceStopped(this, endReasonCode, endReason);

        setJvbCall(null);
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
        if (this.xmppProvider != null)
            throw new IllegalStateException("unexpected");

        if (!xmppProvider.getAccountID().getAccountUniqueID()
                .equals(xmppAccount.getAccountUniqueID()))
        {

            logger.info(
                this.callContext + " Rejects XMPP provider " + xmppProvider);
            return;
        }

        logger.info(this.callContext + " Using " + xmppProvider);

        this.xmppProvider = xmppProvider;

        // Advertise gateway features before joining and if possible before connecting
        this.features = addSupportedFeatures(xmppProvider.getOperationSet(OperationSetJitsiMeetToolsJabber.class));

        xmppProvider.addRegistrationStateChangeListener(this);

        this.telephony
            = xmppProvider.getOperationSet(OperationSetBasicTelephony.class);

        telephony.addCallListener(callListener);

        if (xmppProvider.isRegistered())
        {
            xmppInvokeQueue.add(this::joinConferenceRoom);
        }
        else
        {
            new RegisterThread(xmppProvider, xmppPassword).start();
        }
    }

    public ProtocolProviderService getXmppProvider()
    {
        return xmppProvider;
    }

    @Override
    public void registrationStateChanged(RegistrationStateChangeEvent evt)
    {
        xmppInvokeQueue.add(() -> registrationStateChangedInternal(evt));
    }

    private synchronized void registrationStateChangedInternal(RegistrationStateChangeEvent evt)
    {
        if (started && mucRoom == null && evt.getNewState() == RegistrationState.REGISTERED)
        {
            discoverComponentAddresses();

            // Join the MUC
            joinConferenceRoom();

            XMPPConnection connection = getConnection();
            if (xmppProvider != null && connection instanceof XMPPBOSHConnection)
            {
                Object sessionId = Util.getConnSessionId(connection);
                if (sessionId != null)
                {
                    logger.error(this.callContext + " Registered bosh sid: "
                        + sessionId);
                }
            }
        }
        else if (evt.getNewState() == RegistrationState.UNREGISTERED)
        {
            logger.error(this.callContext + " Unregistered XMPP:" + evt);
        }
        else if (evt.getNewState() == RegistrationState.REGISTERING)
        {
            logger.info(this.callContext + " Registering XMPP.");
        }
        else if (evt.getNewState() == RegistrationState.CONNECTION_FAILED)
        {
            logger.error(this.callContext + " XMPP Connection failed. " + evt);

            if (!connFailedStatsSent)
            {
                Statistics.incrementTotalCallsWithConnectionFailed();
                connFailedStatsSent = true;
            }

            leaveConferenceRoom();

            // as this is connection failed and provider will reconnect
            // we want to update local resource after leaving the room,
            // so we can eventually join second time before the previous
            // participant been removed due to inactivity (bosh-timeout)
            callContext.updateCallResource();

            // ugly hack to detect wrong xmpp jid used, to drop the call(xmpp and sip one) and give up
            if (evt.getReasonCode() == RegistrationStateChangeEvent.REASON_INTERNAL_ERROR
                && evt.getReason().contains("No supported and enabled SASL Mechanism provided by server"))
            {
                // probably wrong roomName or domain/tenant combination, we will drop the call
                logger.error("Server didn't like our xmpp configs, we are giving up!    ");
                stop();
            }
            else
            {
                // let us hangup this call, a new one will be established once we are back in the room
                CallManager.hangupCall(jvbCall, 502, "Connection failed");
            }
        }
        else
        {
            logger.info(this.callContext + evt.toString());
        }
    }

    /**
     * Disco info the addresses, the query is cached and will be returned from cache
     * once we retrieve it.
     */
    private void discoverComponentAddresses()
    {
        // we are here in the RegisterThread, and it is safe to query and wait
        // Uses disco info to discover the AV moderation address.
        // we need to query the domain part extracted from room jid
        if (this.callContext.getRoomJidDomain() != null)
        {
            try
            {
                long startQuery = System.currentTimeMillis();

                // in case when running unittests
                if (this.getConnection() == null)
                {
                    return;
                }

                DiscoverInfo info = ServiceDiscoveryManager.getInstanceFor(this.getConnection())
                        .discoverInfo(JidCreate.domainBareFrom(this.callContext.getRoomJidDomain()));

                logger.info(String.format(
                        "%s Disco-info took %oms.", this.callContext, System.currentTimeMillis() - startQuery));

                DiscoverInfo.Identity avIdentity = info.getIdentities().stream().
                    filter(di -> di.getCategory().equals("component") && di.getType().equals("av_moderation"))
                        .findFirst().orElse(null);

                if (avIdentity != null && this.getAudioModeration() != null)
                {
                    String avModerationAddress = avIdentity.getName();
                    this.getAudioModeration().setAvModerationAddress(avModerationAddress);
                }

                DiscoverInfo.Identity roomMetadataIdentity = info.getIdentities().stream().
                        filter(di -> di.getCategory().equals("component") && di.getType().equals("room_metadata"))
                        .findFirst().orElse(null);

                // we process room metadata messages only when we are transcribing
                if (roomMetadataIdentity != null && this.isTranscriber)
                {
                    getConnection().addAsyncStanzaListener(roomMetadataListener,
                        new AndFilter(
                            MessageTypeFilter.NORMAL,
                            FromMatchesFilter.create(JidCreate.domainBareFrom(roomMetadataIdentity.getName()))));
                }
            }
            catch(Exception e)
            {
                logger.error("Error querying for av moderation address", e);
            }
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

    /**
     * Indicates whether this conference has been started.
     * @return <tt>true</tt> is this conference is started, false otherwise.
     */
    public boolean isStarted()
    {
        return started;
    }

    /**
     * Indicates whether this conference has been joined as visitor.
     * @return <tt>true</tt> is this conference is joined as visitor, false otherwise.
     */
    public boolean isVisitor()
    {
        return this.isVisitor;
    }

    /**
     * When calling this method, make sure it is not executed in any of the Smack threads.
     */
    public void joinConferenceRoom()
    {
        OperationSetMultiUserChat muc = xmppProvider.getOperationSet(OperationSetMultiUserChat.class);
        muc.addPresenceListener(this);

        OperationSetIncomingDTMF opSet = this.xmppProvider.getOperationSet(OperationSetIncomingDTMF.class);
        if (opSet != null)
        {
            // this executes only sip provider logic
            opSet.addDTMFListener(gatewaySession);
        }

        this.jitsiMeetTools = xmppProvider.getOperationSet(OperationSetJitsiMeetToolsJabber.class);

        if (this.jitsiMeetTools != null)
        {
            this.jitsiMeetTools.addRequestListener(this);
        }

        Localpart lobbyLocalpart = null;

        ChatRoom mucRoom = null;
        try
        {
            String roomName = callContext.getRoomJid().toString();
            String roomPassword = callContext.getRoomPassword();

            logger.info(this.callContext + " Joining JVB conference room: " + roomName);

            mucRoom = muc.findRoom(roomName);

            if (mucRoom instanceof ChatRoomJabberImpl)
            {
                String displayName = gatewaySession.getMucDisplayName();
                if (displayName != null)
                {
                    ((ChatRoomJabberImpl)mucRoom).addPresencePacketExtensions(
                        new Nick(displayName));
                }
                else
                {
                    logger.error(this.callContext
                        + " No display name to use...");
                }

                String region = JigasiBundleActivator.getConfigurationService()
                    .getString(LOCAL_REGION_PNAME);
                if (StringUtils.isNotEmpty(region))
                {
                    JitsiParticipantRegionPacketExtension rpe = new JitsiParticipantRegionPacketExtension();
                    rpe.setRegionId(region);

                    ((ChatRoomJabberImpl)mucRoom)
                        .addPresencePacketExtensions(rpe);
                }

                ((ChatRoomJabberImpl)mucRoom)
                    .addPresencePacketExtensions(
                        new ColibriStatsExtension.Stat(
                            ColibriStatsExtension.VERSION,
                            CurrentVersionImpl.VERSION.getApplicationName()
                                + " " + CurrentVersionImpl.VERSION));

                // creates an extension to hold all headers, as when using
                // addPresencePacketExtensions it requires unique extensions
                // otherwise overrides them
                AbstractPacketExtension initiator = new AbstractPacketExtension(JIGASI_FEATURE_NAME, "initiator"){};

                // let's add all extra headers from the context
                callContext.getExtraHeaders().forEach(
                    (key, value) ->
                    {
                        HeaderExtension he = new HeaderExtension();
                        he.setName(key);
                        he.setValue(value);

                        initiator.addChildExtension(he);
                    });
                if (!initiator.getChildExtensions().isEmpty())
                {
                    ((ChatRoomJabberImpl)mucRoom).addPresencePacketExtensions(initiator);
                }

                ((ChatRoomJabberImpl)mucRoom).addPresencePacketExtensions(this.features);
            }
            else
            {
                logger.error(this.callContext
                    + " Cannot set presence extensions as chatRoom "
                    + "is not an instance of ChatRoomJabberImpl");
            }

            if (this.audioModeration != null)
            {
                this.audioModeration.notifyWillJoinJvbRoom(mucRoom);
            }

            Localpart resourceIdentifier = getResourceIdentifier();

            // we are inviting focus when not a visitor, if we have received a response to join as a visitor
            // we skip sending the request and just join as a visitor
            if (!this.skipFocus)
            {
                // we invite focus and wait for its response
                // to be sure that if it is not in the room, the focus will be the
                // first to join, mimic the web behaviour
                String vnode = inviteFocus(JidCreate.entityBareFrom(mucRoom.getIdentifier()));

                if (vnode != null && !this.isTranscriber && JigasiBundleActivator.isSipVisitorsEnabled())
                {
                    this.isVisitor = true;
                    this.skipFocus = true;
                    this.oldRoom = this.callContext.getRoomJid().toString();
                    String oldDomain = this.callContext.getDomain();
                    String newDomain = vnode + ".meet.jitsi";

                    this.xmppProvider.unregister(true); // let's recreate it after disconnect

                    // remove old first
                    this.xmppProvider.removeRegistrationStateChangeListener(this);

                    logger.info(callContext + " Removing account to prepare visitor " + this.xmppAccount);
                    this.xmppProviderFactory.unloadAccount(this.xmppAccount);

                    this.xmppProvider = null;

                    // update domain with the predefined visitor's domain
                    this.callContext.setRoomName(this.oldRoom.replaceAll(oldDomain, newDomain));

                    // create a new account, it will match the domain for the userID from the room jid we updated
                    Map<String, String> props = createAccountPropertiesForCallId(resourceIdentifier.toString());

                    if (!Boolean.parseBoolean(props.get(JabberAccountID.ANONYMOUS_AUTH)))
                    {
                        // if there is authentication we want to use it but with the visitor's domain
                        props.put(ProtocolProviderFactory.USER_ID,
                            props.get(ProtocolProviderFactory.USER_ID).replace(oldDomain, newDomain));
                    }

                    this.oldBosh = this.xmppAccount.getAccountPropertyString(JabberAccountID.BOSH_URL);
                    props.put(JabberAccountID.BOSH_URL, this.oldBosh + "&vnode=" + vnode);

                    this.createAndLoadAccount(props);

                    return;
                }
            }

            lobbyLocalpart = resourceIdentifier;

            // let's schedule the timeout before joining, before been able
            // to receive any incoming call, will cancel it if we need to
            // jvbCall will be null (no need on any sync as we are still not in
            // the room)
            inviteTimeout.scheduleTimeout();

            mucRoom.addMemberPresenceListener(this);

            if (StringUtils.isEmpty(roomPassword))
            {
                mucRoom.joinAs(resourceIdentifier.toString());
            }
            else
            {
                mucRoom.joinAs(resourceIdentifier.toString(),
                    roomPassword.getBytes());
            }

            this.mucRoom = mucRoom;

            if (gatewaySession.getDefaultInitStatus() != null)
            {
                setPresenceStatus(gatewaySession.getDefaultInitStatus());
            }

            gatewaySession.notifyJvbRoomJoined();

            // let's listen for any future changes in room configuration, whether lobby will be enabled/disabled
            if (mucRoom instanceof ChatRoomJabberImpl)
            {
                getConnection().addAsyncStanzaListener(roomConfigurationListener,
                    new AndFilter(
                        FromMatchesFilter.create(((ChatRoomJabberImpl)this.mucRoom).getIdentifierAsJid()),
                        MessageTypeFilter.GROUPCHAT));

                if (this.isVisitor)
                {
                    this.visitorIqHandler = new VisitorIqHandler();
                    getConnection().registerIQRequestHandler(this.visitorIqHandler);
                }
            }

            // let's check room config
            updateFromRoomConfiguration();

            logger.info(this.callContext + " Joined room: " + roomName + " meetingId:" + this.getMeetingId());

            this.skipFocus = false;
        }
        catch (Exception e)
        {
            if (e instanceof OperationFailedException)
            {
                OperationFailedException opex = (OperationFailedException)e;

                // Thrown when lobby is enabled.
                if (opex.getErrorCode() == OperationFailedException.REGISTRATION_REQUIRED)
                {
                    // Lobby functionality is only supported for <tt>SipGatewaySession</tt>.
                    if (this.gatewaySession != null && this.gatewaySession instanceof SipGatewaySession)
                    {
                        try
                        {
                            this.audioModeration.clean();

                            if (mucRoom != null)
                            {
                                mucRoom.removeMemberPresenceListener(this);
                                mucRoom.leave();
                            }

                           muc.removePresenceListener(this);

                            if (opSet != null)
                            {
                                opSet.removeDTMFListener(this.gatewaySession);
                            }

                            if (this.jitsiMeetTools != null)
                            {
                                this.jitsiMeetTools.removeRequestListener(this);
                            }

                            DataObject dataObject = opex.getDataObject();

                            if (dataObject != null)
                            {
                                Jid lobbyJid = (Jid)dataObject.getData("lobbyroomjid");

                                if (lobbyJid != null)
                                {
                                    EntityFullJid lobbyFullJid =
                                        JidCreate.entityFullFrom(
                                            lobbyJid.asEntityBareJidOrThrow(),
                                            Resourcepart.from(
                                                lobbyLocalpart.toString()));

                                    this.lobby = new Lobby(this.xmppProvider,
                                            this.callContext,
                                            lobbyFullJid,
                                            this.callContext.getRoomJid(),
                                            this,
                                            (SipGatewaySession)this.gatewaySession);

                                    logger.info(
                                        callContext + " Lobby enabled by moderator! Will try to join lobby!");

                                    this.lobby.join();

                                    this.setLobbyEnabled(true);

                                    return;
                                }
                                else
                                {
                                    logger.error(callContext + " No required lobby jid!");
                                }
                            }
                        }
                        catch(Exception ex)
                        {
                            logger.error(callContext + " Failed to join lobby room!", ex);
                        }
                    }
                }
            }

            if (e.getCause() instanceof XMPPException.XMPPErrorException)
            {
                if (JigasiBundleActivator.getConfigurationService()
                        .getBoolean(P_NAME_NOTIFY_MAX_OCCUPANTS, true)
                    && ((XMPPException.XMPPErrorException)e.getCause())
                        .getStanzaError().getCondition() == service_unavailable)
                {
                    gatewaySession.handleMaxOccupantsLimitReached();
                }
            }

            logger.error(this.callContext + " " + e.getMessage(), e);

            // inform that this session had failed
            gatewaySession.getGateway().fireGatewaySessionFailed(gatewaySession);

            stop();
        }
    }

    void setPresenceStatus(String statusMsg)
    {
        synchronized(statusSync)
        {
            if (statusMsg.equals(jvbParticipantStatus))
            {
                return;
            }

            jvbParticipantStatus = statusMsg;
        }

        if (mucRoom != null)
        {
            // Send presence status update
            OperationSetJitsiMeetToolsJabber jitsiMeetTools
                = xmppProvider.getOperationSet(
                OperationSetJitsiMeetToolsJabber.class);

            jitsiMeetTools.setPresenceStatus(mucRoom, statusMsg);
        }
    }

    private void onJvbCallEnded()
    {
        if (jvbCall == null)
        {
            logger.warn(this.callContext + " JVB call already disposed");
            return;
        }

        setJvbCall(null);

        if (started)
        {
            // if leave timeout is 0 or less we will not wait for new invite
            // and let's stop the call
            if (AbstractGateway.getJvbInviteTimeout() <= 0 || !gatewaySession.hasCallResumeSupport())
            {
                stop();
            }
            else
            {
                logger.info(this.callContext
                    + " Proceed with gwSession call on xmpp call hangup.");

                if (!gwSesisonWaitingStatsSent)
                {
                    Statistics.incrementTotalCallsWithSipCallWaiting();
                    gwSesisonWaitingStatsSent = true;
                }

                this.gatewaySession.onJvbCallEnded();
            }
        }
    }

    private void leaveConferenceRoom()
    {
        if (this.jitsiMeetTools != null)
        {
            this.jitsiMeetTools.removeRequestListener(this.gatewaySession);

            this.jitsiMeetTools = null;
        }

        OperationSetIncomingDTMF opSet
            = this.xmppProvider.getOperationSet(OperationSetIncomingDTMF.class);
        if (opSet != null)
            opSet.removeDTMFListener(gatewaySession);

        OperationSetMultiUserChat muc
            = xmppProvider.getOperationSet(OperationSetMultiUserChat.class);
        muc.removePresenceListener(this);

        XMPPConnection connection = getConnection();
        if (connection != null)
        {
            connection.removeAsyncStanzaListener(roomConfigurationListener);
            connection.removeAsyncStanzaListener(roomMetadataListener);
        }

        // remove listener needs to be after leave,
        // to catch all member left events
        // and when focus is leaving we will call again leaveConferenceRoom making mucRoom, so we need another check
        if (mucRoom != null)
        {
            mucRoom.leave();
            mucRoom.removeMemberPresenceListener(this);
            mucRoom = null;
        }

        if (this.lobby != null)
        {
            this.lobby.leave();
        }

        this.lobby = null;
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
        xmppInvokeQueue.add(() -> memberPresenceChangedInternal(evt));
    }

    private void memberPresenceChangedInternal(ChatRoomMemberPresenceChangeEvent evt)
    {
        if (logger.isTraceEnabled())
        {
            logger.trace(this.callContext + " Member presence change: " + evt);
        }

        ChatRoomMember member = evt.getChatRoomMember();
        String eventType = evt.getEventType();

        if (!ChatRoomMemberPresenceChangeEvent.MEMBER_KICKED.equals(eventType)
            && !ChatRoomMemberPresenceChangeEvent.MEMBER_LEFT.equals(eventType)
            && !ChatRoomMemberPresenceChangeEvent.MEMBER_QUIT.equals(eventType))
        {
            if (ChatRoomMemberPresenceChangeEvent.MEMBER_JOINED.equals(eventType))
            {
                gatewaySession.notifyChatRoomMemberJoined(member);
            }
            else if (ChatRoomMemberPresenceChangeEvent.MEMBER_UPDATED
                    .equals(eventType))
            {
                if (member instanceof ChatRoomMemberJabberImpl)
                {
                    gatewaySession.notifyChatRoomMemberUpdated(member,
                        ((ChatRoomMemberJabberImpl) member).getLastPresence());
                }
            }

            return;
        }
        else
        {
            gatewaySession.notifyChatRoomMemberLeft(member);
            logger.info(
                this.callContext + " Member left : " + member.getRole()
                            + " " + member.getContactAddress());

            CallPeer peer;
            if (jvbCall != null && (peer = jvbCall.getCallPeers().next()) instanceof MediaAwareCallPeer)
            {
                MediaAwareCallPeer<?, ?, ?> peerMedia = (MediaAwareCallPeer<?, ?, ?>) peer;
                peerMedia.getConferenceMembers().forEach(confMember ->
                {
                    String address = confMember.getAddress();
                    if (address != null && !address.equals("jvb"))
                    {
                        try
                        {
                            if (JidCreate.from(address).getResourceOrEmpty().equals(member.getName())
                                || address.equals(member.getName()))
                            {
                                peerMedia.removeConferenceMember(confMember);
                            }
                        }
                        catch(Exception e)
                        {
                            logger.error(this.callContext + " Error removing conference member=" + member.getName());
                        }
                    }
                });
            }
        }

        // process member left if it is not focus
        processChatRoomMemberLeft(member);
    }

    /**
     * Extra logic when member left. In case of sip gateway session and lobby is enabled, if only jigasi participants
     * are in the room they should leave as they cannot be moderators. In case of dedicated moderator in the room
     * the jigasi instances can stay.
     *
     * @param member The member that had left
     */
    private void processChatRoomMemberLeft(ChatRoomMember member)
    {
        if (!this.started)
        {
            return;
        }

        // if it is the focus leaving, and we are not in the middle of hangup
        // we leave this here before checking connection to make tests happy
        if (member.getName().equals(gatewaySession.getFocusResourceAddr()))
        {
            logger.info(this.callContext + " Focus left! - stopping the call");
            CallManager.hangupCall(jvbCall, 502, "Focus left");

            return;
        }

        if (getConnection() == null || !getConnection().isConnected())
        {
            // we want to ignore the leave events when stopping the conference,
            // or connection is missing or not connected
            return;
        }

        // if lobby is not enabled or single moderator mode is detected there is nothing to process,
        // but otherwise we will check whether there are jigasi participants
        // and jigasi cannot moderate those from lobby, we need to end the conference by all jigasi leaving it
        if ((this.lobbyEnabled && !this.singleModeratorEnabled) || !this.allowOnlyJigasiInRoom)
        {
            boolean onlyJigasisInRoom = this.mucRoom.getMembers().stream().allMatch(m ->
                m.getName().equals(getResourceIdentifier().toString()) // ignore if it is us
                || m.getName().equals(gatewaySession.getFocusResourceAddr()) // ignore if it is jicofo
                || (Util.isJigasi((ChatRoomMemberJabberImpl) m)
                        && !Util.isTranscriberJigasi((ChatRoomMemberJabberImpl)m)));

            if (onlyJigasisInRoom)
            {
                if (!this.allowOnlyJigasiInRoom)
                {
                    logger.info(this.callContext + " Leaving room without web users and only jigasi participants!");
                    stop();
                    return;
                }

                // there are only jigasi participants in the room with lobby enabled
                logger.info(this.callContext + " Leaving room with lobby enabled and only jigasi participants!");

                // let's play something
                if (this.gatewaySession instanceof SipGatewaySession)
                {
                    // This will hang up the call at the end
                    ((SipGatewaySession) this.gatewaySession)
                        .getSoundNotificationManager().notifyLobbyRoomDestroyed();
                }
                else
                {
                    // transcriber case
                    stop();
                }

                return;
            }
        }

        if (JvbConference.this.isTranscriber)
        {
            // make sure we hangup transcriber if only backend services are in the room - Jibri/Jigasi
            // (maybe a second transcriber if there is some glitch in the system).
            boolean onlyBotsInRoom = this.mucRoom.getMembers().stream().allMatch(m ->
                    m.getName().equals(getResourceIdentifier().toString()) // ignore if it is us
                            || m.getName().equals(gatewaySession.getFocusResourceAddr()) // ignore if it is jicofo
                            || Util.isTranscriberJigasi((ChatRoomMemberJabberImpl)m)
                            || Util.isJibri((ChatRoomMemberJabberImpl)m));

            if (onlyBotsInRoom)
            {
                logger.info(this.callContext + " Leaving room only bots in the room!");
                stop();
            }
        }
    }

    /**
     * Handles when user is kicked or room is destroyed to stop the conference.
     * @param evt the event
     */
    @Override
    public void localUserPresenceChanged(LocalUserChatRoomPresenceChangeEvent evt)
    {
        xmppInvokeQueue.add(() -> localUserPresenceChangedLocal(evt));
    }

    private void localUserPresenceChangedLocal(LocalUserChatRoomPresenceChangeEvent evt)
    {
        try
        {
            if (evt.getChatRoom().equals(JvbConference.this.mucRoom))
            {
                if (Objects.equals(evt.getEventType(), LOCAL_USER_KICKED)
                    || Objects.equals(evt.getEventType(), LOCAL_USER_ROOM_DESTROYED))
                {
                    this.stop();
                }
            }
        }
        catch (Exception ex)
        {
            logger.error(callContext + " " + ex, ex);
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
            OperationSetJitsiMeetToolsJabber jitsiMeetTools
                = xmppProvider.getOperationSet(
                OperationSetJitsiMeetToolsJabber.class);

            jitsiMeetTools.sendPresenceExtension(mucRoom, extension);
        }
    }

    /**
     * @return an <tt>OrderedJsonObject</tt> instance that holds debug
     * information for this instance.
     */
    public OrderedJsonObject getDebugState()
    {
        OrderedJsonObject debugState = new OrderedJsonObject();
        String meetingUrl = getMeetingUrl();
        if (StringUtils.isNotEmpty(meetingUrl))
        {
            debugState.put("meetingUrl", getMeetingUrl());
        }

        String meetingIdCopy = getMeetingId();
        if (StringUtils.isNotEmpty(meetingUrl))
        {
            debugState.put("meetingId", meetingIdCopy);
        }

        if (this.mucRoom != null)
        {
            debugState.put("nick", this.mucRoom.getUserNickname());
        }

        return debugState;
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

    /**
     * Listens for transport replace - migrating to a new bridge.
     * For now, we just leave the room(xmpp call) and join again to be re-invited.
     * @param evt the event for CallPeer change.
     */
    @Override
    public void propertyChange(PropertyChangeEvent evt)
    {
        xmppInvokeQueue.add(() ->
        {
            if (evt.getPropertyName().equals(CallPeerJabberImpl.TRANSPORT_REPLACE_PROPERTY_NAME))
            {
                Statistics.incrementTotalCallsWithJvbMigrate();

                leaveConferenceRoom();

                joinConferenceRoom();
            }
        });
    }

    @Override
    public void onJoinJitsiMeetRequest(Call call, String room, Map<String, String> data)
    {
        xmppInvokeQueue.add(() -> this.gatewaySession.onJoinJitsiMeetRequest(call, room, data));
    }

    @Override
    public void onSessionStartMuted(boolean[] startMutedFlags)
    {
        xmppInvokeQueue.add(() -> this.gatewaySession.onSessionStartMuted(startMutedFlags));
    }

    @Override
    public void onJSONReceived(CallPeer callPeer, JSONObject jsonObject, Map<String, Object> parameters)
    {
        xmppInvokeQueue.add(() -> this.gatewaySession.onJSONReceived(callPeer, jsonObject, parameters));
    }

    private class JvbCallListener
        implements CallListener
    {
        @Override
        public void incomingCallReceived(CallEvent event)
        {
            xmppInvokeQueue.add(() -> incomingCallReceivedInternal(event));
        }

        private void incomingCallReceivedInternal(CallEvent event)
        {
            CallPeer peer = event.getSourceCall().getCallPeers().next();
            String peerAddress;
            if (peer == null || peer.getAddress() == null)
            {
                logger.error(callContext
                    + " Failed to obtain focus peer address");
                peerAddress = null;
            }
            else
            {
                String fullAddress = peer.getAddress();
                peerAddress
                    = fullAddress.substring(
                            fullAddress.indexOf("/") + 1);

                logger.info(callContext
                    + " Got invite from " + peerAddress);
            }

            if (peerAddress == null
                || !peerAddress.equals(gatewaySession.getFocusResourceAddr()))
            {
                if (logger.isTraceEnabled())
                {
                    logger.trace(callContext +
                        " Calls not initiated from focus are not allowed");
                }

                CallManager.hangupCall(event.getSourceCall(),
                    403, "Only calls from focus allowed");
                return;
            }

            if (jvbCall != null)
            {
                logger.error(callContext +
                    " JVB conference call already started ");
                CallManager.hangupCall(event.getSourceCall(),
                    200, "Call completed elsewhere");
                return;
            }

            if (!started || xmppProvider == null)
            {
                logger.error(callContext + " Instance disposed");
                return;
            }

            Call jvbCall = event.getSourceCall();
            setJvbCall(jvbCall);
            jvbCall.setData(CallContext.class, callContext);

            peer.addCallPeerConferenceListener(JvbConference.this);
            peer.addPropertyChangeListener(JvbConference.this);

            peer.addCallPeerListener(new CallPeerAdapter()
            {
                @Override
                public void peerStateChanged(CallPeerChangeEvent evt)
                {
                    CallPeer p = evt.getSourceCallPeer();
                    CallPeerState peerState = p.getState();

                    if (CallPeerState.CONNECTED.equals(peerState))
                    {
                        p.removeCallPeerListener(this);

                        // We set the connected state only for incoming calls
                        // outgoing calls will set it when the sip side connects
                        if (callContext.getDestination() == null && gatewaySession instanceof SipGatewaySession)
                        {
                            setPresenceStatus(peerState.getStateString());
                        }
                    }
                }
            });

            // disable hole punching jvb
            if (peer instanceof MediaAwareCallPeer)
            {
                CallPeerMediaHandler peerMediaHandler = ((MediaAwareCallPeer)peer).getMediaHandler();

                peerMediaHandler.setDisableHolePunching(true);

                if (isVisitor)
                {
                    // no sources when visitor, or we get rejection from jicofo
                    peerMediaHandler.setLocalAudioTransmissionEnabled(false);
                }
            }

            jvbCall.addCallChangeListener(callChangeListener);

            gatewaySession.onConferenceCallInvited(jvbCall);
        }

        @Override
        public void outgoingCallCreated(CallEvent event) {}

        @Override
        public void callEnded(CallEvent event) {}
    }

    private class JvbCallChangeListener
        extends CallChangeAdapter
    {
        @Override
        public void callStateChanged(CallChangeEvent evt)
        {
            xmppInvokeQueue.add(() -> callStateChangedInternal(evt));
        }

        private synchronized void callStateChangedInternal(CallChangeEvent evt)
        {
            if (jvbCall != evt.getSourceCall())
            {
                logger.error(
                    callContext + " Call change event for different call ? "
                        + evt.getSourceCall() + " : " + jvbCall);
                return;
            }

            if (jvbCall.getCallState() == CallState.CALL_IN_PROGRESS)
            {
                logger.info(callContext + " JVB conference call IN_PROGRESS.");
                gatewaySession.onJvbCallEstablished();

                AudioModeration avMod = JvbConference.this.getAudioModeration();
                if (avMod != null)
                {
                    avMod.maybeProcessStartMuted();
                }

                checkReceivedMediaTimer.schedule(new MediaActivityChecker(), JVB_ACTIVITY_CHECK_DELAY);
            }
            else if (jvbCall.getCallState() == CallState.CALL_ENDED)
            {
                onJvbCallEnded();
            }
        }
    }

    /**
     * Generates new account properties.
     */
    private Map<String, String> createAccountPropertiesForCallId(String nodePart)
    {
        HashMap<String, String> properties = new HashMap<>();

        String domain = this.callContext.getRoomJidDomain();

        properties.put(ProtocolProviderFactory.USER_ID, nodePart + "@" + domain);
        properties.put(ProtocolProviderFactory.SERVER_ADDRESS, domain);
        properties.put(ProtocolProviderFactory.SERVER_PORT, "5222");

        properties.put(ProtocolProviderFactory.RESOURCE, nodePart);
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

        if (gw instanceof SipGateway
            && Boolean.parseBoolean(((SipGateway) gw).getSipAccountProperty("PREVENT_AUTH_LOGIN")))
        {
            // if we do not want auth login we need to ignore custom USER_ID,
            //PASS, ANONYMOUS_AUTH, ALLOW_NON_SECURE and leave defaults
            overriddenProps.remove(overridePrefix + "." + ProtocolProviderFactory.USER_ID);
            overriddenProps.remove(overridePrefix + ".PASS");
            overriddenProps.remove(overridePrefix + "." + JabberAccountID.ANONYMOUS_AUTH);
            overriddenProps.remove(overridePrefix + "." + ProtocolProviderFactory.IS_ALLOW_NON_SECURE);
        }

        for (String overridenProp : overriddenProps)
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
                // changing the implementation of the loadAccount method.
                //
                // To avoid having to store the dynamic accounts in the
                // configuration and, consequently, to have to manage them, to
                // have removed them later, etc. (also NOTE that storing an
                // account WRITES the configuration file), we read the password
                // from a custom key (and *not* from the standard password key,
                // otherwise it gets encrypted by the configuration service, see
                // the comment above) and then we feed it (the password) to the
                // new ServerSecurityAuthority that we create when we register
                // the account. The
                //
                //     ServerSecurityAuthority#obtainCredentials
                //
                // method is called when there is no password for a specific
                // account and there we can alter the connection credentials.

                this.xmppPassword = value;
                // add password in props, so it is available
                // for the UIServiceStub and getAccountID().getPassword()
                // used on reconnect
                properties.put(ProtocolProviderFactory.PASSWORD, value);
            }
            else if ("org.jitsi.jigasi.xmpp.acc.BOSH_URL_PATTERN".equals(overridenProp))
            {
                // do not override boshURL with the global setting if
                // we already have a value
                if (StringUtils.isEmpty(this.callContext.getBoshURL()))
                {
                    this.callContext.setBoshURL(value);
                }
            }
            else if ("org.jitsi.jigasi.xmpp.acc.USER_ID".equals(overridenProp)
                && JigasiBundleActivator.getConfigurationService().getBoolean(overridePrefix + ".UNIQUE_USER_ID",
                    false))
            {
                try
                {
                    Jid jid = JidCreate.from(value);
                    long random = RANDOM.nextInt();
                    Jid newJid = JidCreate.entityBareFrom(
                            Localpart.from(jid.getLocalpartOrNull() + "-" + String.format("%x", random)),
                            jid.getDomain());

                    properties.put(ProtocolProviderFactory.USER_ID, newJid.toString());
                }
                catch (XmppStringprepException e)
                {
                    logger.error("Error jid in org.jitsi.jigasi.xmpp.acc.USER_ID config", e);

                    properties.put(ProtocolProviderFactory.USER_ID, value);
                }
            }
            else
            {
                properties.put(key, value);
            }
        }

        String boshUrl = this.callContext.getBoshURL();
        if (StringUtils.isNotEmpty(boshUrl))
        {
            boshUrl = boshUrl.replace("{roomName}", this.callContext.getConferenceName());

            try
            {
                // Make sure we encode the roomName parameter
                URIBuilder encodedUrlBuilder = new URIBuilder(boshUrl);
                encodedUrlBuilder.setParameter("room", this.callContext.getConferenceName());
                boshUrl = encodedUrlBuilder.build().toURL().toString();
            }
            catch (URISyntaxException | MalformedURLException e)
            {
                logger.error(this.callContext + " Cannot encode bosh url param room", e);
            }

            logger.info(this.callContext + " Using bosh url:" + boshUrl);
            properties.put(JabberAccountID.BOSH_URL, boshUrl);

            if (this.callContext.hasAuthToken() &&  this.callContext.getAuthUserId() != null)
            {
                properties.put(ProtocolProviderFactory.USER_ID, this.callContext.getAuthUserId());
            }
        }

        // Necessary when doing authenticated XMPP login, otherwise the dynamic
        // accounts get assigned the same ACCOUNT_UID which leads to problems.
        String accountUID = "Jabber:" + properties.get(ProtocolProviderFactory.USER_ID) + "/" + nodePart;
        properties.put(ProtocolProviderFactory.ACCOUNT_UID, accountUID);

        // Because some AbstractGatewaySessions needs access to the audio,
        // we can't always use translator
        if (!gatewaySession.isTranslatorSupported())
        {
            properties.put(ProtocolProviderFactory.USE_TRANSLATOR_IN_CONFERENCE, "false");
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
     * @return Returns vnode if one exist in focus response.
     */
    private String inviteFocus(final EntityBareJid roomIdentifier)
    {
        if (callContext == null || callContext.getRoomJidDomain() == null)
        {
            logger.error(this.callContext
                + " No domain name info to use for inviting focus! Please set DOMAIN_BASE to the sip account.");
            return null;
        }

        ConferenceIq focusInviteIQ = new ConferenceIq();
        focusInviteIQ.setRoom(roomIdentifier);

        if (JigasiBundleActivator.isSipVisitorsEnabled() && !this.isTranscriber)
        {
            focusInviteIQ.addProperty("visitors-version", "1");
        }

        try
        {
            focusInviteIQ.setType(IQ.Type.set);
            // use the part from the muc jid
            focusInviteIQ.setTo(JidCreate.domainBareFrom(
                gatewaySession.getFocusResourceAddr() + "." + callContext.getRoomJidDomain()));
        }
        catch (XmppStringprepException e)
        {
            logger.error(this.callContext + " Could not create destination address for focus invite", e);
            return null;
        }

        // this check just skips an exception when running tests
        if (xmppProvider instanceof ProtocolProviderServiceJabberImpl)
        {
            StanzaCollector collector = null;
            try
            {
                collector = getConnection().createStanzaCollectorAndSend(focusInviteIQ);
                ConferenceIq res = collector.nextResultOrThrow();

                return res.getVnode();
            }
            catch (SmackException
                | XMPPException.XMPPErrorException
                | InterruptedException e)
            {
                logger.error(this.callContext + " Could not invite the focus to the conference", e);
            }
            finally
            {
                if (collector != null)
                {
                    collector.cancel();
                }
            }
        }

        return null;
    }

    /**
     * Sets new jvbCall and checks whether invite timeout should be scheduled
     * or canceled.
     * @param newJvbCall the new jvbCall.
     */
    private void setJvbCall(Call newJvbCall)
    {
        synchronized(jvbCallWriteSync)
        {
            if (newJvbCall == null)
            {
                // cleanup
                if (this.jvbCall != null)
                {
                    this.jvbCall.removeCallChangeListener(callChangeListener);
                }
            }

            this.jvbCall = newJvbCall;

            inviteTimeout.maybeScheduleInviteTimeout();
        }
    }

    /**
     * Retrieves the connection from ProtocolProviderService if it is the JabberImpl.
     * @return the XMPPConnection.
     */
    public XMPPConnection getConnection()
    {
        if (this.xmppProvider instanceof ProtocolProviderServiceJabberImpl)
        {
            return ((ProtocolProviderServiceJabberImpl) this.xmppProvider)
                .getConnection();
        }

        return null;
    }

    /**
     * Called whenever password is known. In case of lobby, while waiting in the lobby, the user can enter the password
     * and that can be signalled through SIP Info messages, and we can leve the lobby and enter the room with the
     * password received, if the password is wrong we will fail joining and the call will be dropped.
     * This is executed in sip thread.
     *
     * @param pwd <tt>String</tt> room password.
     */
    public void onPasswordReceived(String pwd)
    {
        // Check if conference joined before trying...
        if (this.mucRoom != null)
        {
            logger.warn(this.callContext + " Strange received a password after joining the room");
            return;
        }

        this.callContext.setRoomPassword(pwd);

        // leave lobby room
        if (this.lobby != null)
        {
            this.lobby.leave();
        }

        // join conference room
        joinConferenceRoom();
    }

    /**
     * Changes the value of the flag whether lobby is enabled or not.
     * @param value the new value.
     */
    public void setLobbyEnabled(boolean value)
    {
        lobbyEnabled = value;
    }

    /**
     * @return the (unique) meeting id from the muc configuration.
     */
    private String getMeetingId()
    {
        // in case when running unittests
        if (this.getConnection() == null)
        {
            return null;
        }

        if (this.meetingId == null)
        {
            updateFromRoomConfiguration();
        }

        return this.meetingId;
    }

    /**
     * Discovers the room configuration and checks the values of whether lobby is enabled and whether
     * single moderator is set.
     */
    private void updateFromRoomConfiguration()
    {
        // in case when running unittests
        if (this.getConnection() == null)
        {
            return;
        }

        try
        {
            DiscoverInfo info = ServiceDiscoveryManager.getInstanceFor(getConnection()).
                discoverInfo(((ChatRoomJabberImpl)this.mucRoom).getIdentifierAsJid());

            DataForm df = (DataForm) info.getExtension(DataForm.NAMESPACE);
            boolean lobbyEnabled = df.getField(DATA_FORM_LOBBY_ROOM_FIELD) != null;
            setLobbyEnabled(lobbyEnabled);

            this.singleModeratorEnabled = df.getField(DATA_FORM_SINGLE_MODERATOR_FIELD) != null;

            FormField roomMetadata = df.getField(DATA_FORM_ROOM_METADATA_FIELD);
            if (roomMetadata != null)
            {
                List<String> roomMetadataValues = roomMetadata.getValuesAsString();
                if (roomMetadataValues != null && !roomMetadataValues.isEmpty())
                {
                    // it is supposed to have a single value
                    processRoomMetadataJson(roomMetadataValues.get(0));
                }
            }

            FormField meetingIdField = df.getField(DATA_FORM_MEETING_ID_FIELD_NAME);
            if (meetingIdField != null)
            {
                this.meetingId = meetingIdField.getFirstValue();
            }
        }
        catch(Exception e)
        {
            logger.error(this.callContext + " Error checking room configuration", e);
        }
    }

    private void processRoomMetadataJson(String json)
    {
        if (!this.isTranscriber)
        {
            return;
        }

        try
        {
            Object o = new JSONParser().parse(json);

            if (o instanceof JSONObject)
            {
                JSONObject data = (JSONObject) o;

                if (data.get("type").equals("room_metadata"))
                {
                    JSONObject metadataObj = (JSONObject)data.getOrDefault("metadata", new JSONObject());
                    JSONObject recordingObj = (JSONObject)metadataObj.getOrDefault("recording", new JSONObject());
                    ((TranscriptionGatewaySession)this.gatewaySession).setBackendTranscribingEnabled(
                        (boolean)recordingObj.getOrDefault("isTranscribingEnabled", false));
                }
            }
        }
        catch(Exception e)
        {
            logger.error(callContext + " Error parsing", e);
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

        private final String errorLog;
        private final String endReason;
        private final String name;

        JvbConferenceStopTimeout(String name, String reason, String errorLog)
        {
            this.name = name;
            this.endReason = reason;
            this.errorLog = errorLog;
        }

        /**
         * Schedules a new timeout thread if not already scheduled
         * using default timeout value.
         * If invite timeout setting is 0 or less will do nothing.
         */
        void scheduleTimeout()
        {
            if (AbstractGateway.getJvbInviteTimeout() > 0)
                this.scheduleTimeout(AbstractGateway.getJvbInviteTimeout());
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
                logger.debug(callContext + " Scheduled new " + this);
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
                }
                catch (InterruptedException e)
                {
                    Thread.currentThread().interrupt();
                }
            }

            if (willCauseTimeout)
            {
                logger.error(callContext + " "
                    + errorLog + " (" + timeout + " ms)");

                JvbConference.this.endReason = this.endReason;
                JvbConference.this.endReasonCode
                    = OperationSetBasicTelephony.HANGUP_REASON_TIMEOUT;

                stop();
            }

            timeoutThread = null;
            logger.debug("Timeout thread is done " + this);
        }

        private void cancel()
        {
            synchronized (syncRoot)
            {
                willCauseTimeout = false;

                if (timeoutThread == null)
                {
                    return;
                }

                logger.debug("Trying to cancel " + this);

                syncRoot.notifyAll();
            }

            logger.debug("Canceled " + this);
        }

        /**
         * Checks whether invite timeout should be scheduled or canceled.
         * If there is no jvb call instance we want to schedule invite timeout,
         * so we can close the conference if nobody joins for certain time
         * or if jvbCall is present we want to cancel any pending timeouts.
         */
        void maybeScheduleInviteTimeout()
        {
            synchronized(jvbCallWriteSync)
            {
                if (JvbConference.this.jvbCall == null
                        && JvbConference.this.started
                        && AbstractGateway.getJvbInviteTimeout() > 0)
                {
                    // if no invite comes back we want to hang up the sip call
                    // and disconnect from the conference
                    this.scheduleTimeout(AbstractGateway.getJvbInviteTimeout());
                }
                else
                {
                    this.cancel();
                }
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString()
        {
            return "JvbConferenceStopTimeout[" + callContext
                + ", willCauseTimeout:" + willCauseTimeout + " details:"
                + (willCauseTimeout ? endReason + "," + errorLog: "")
                + "]@"+ hashCode();
        }
    }

    /**
     * When a room config change is detected we update room configuration to check it.
     */
    private class RoomConfigurationChangeListener
        implements StanzaListener
    {
        @Override
        public void processStanza(Stanza stanza)
        {
            xmppInvokeQueue.add(() ->
            {
                MUCUser mucUser = stanza.getExtension(MUCUser.class);

                if (mucUser == null)
                {
                    return;
                }

                if (mucUser.getStatus().contains(MUCUser.Status.create(104)))
                {
                    updateFromRoomConfiguration();
                }
            });
        }
    }

    /**
     * When a room metadata change is received.
     */
    private class RoomMetadataListener
        implements StanzaListener
    {
        @Override
        public void processStanza(Stanza stanza)
        {
            JsonMessageExtension jsonMsg = stanza.getExtension(JsonMessageExtension.class);

            if (jsonMsg == null)
            {
                return;
            }

            processRoomMetadataJson(jsonMsg.getJson());
        }
    }

    /**
     * Handles visitor iq requests received by jigasi.
     * If promoted, we hangup call and remove the registered account.
     * The ServiceListener waits for the xmpp account to be removed, before connecting
     * to the main room.
     */
    private class VisitorIqHandler
        extends AbstractIqRequestHandler
    {
        public VisitorIqHandler()
        {
            super(VisitorsIq.ELEMENT, VisitorsIq.NAMESPACE, IQ.Type.set, Mode.async);
        }

        @Override
        public IQ handleIQRequest(IQ iqRequest)
        {
            VisitorsIq visitorsIq = (VisitorsIq) iqRequest;

            VisitorsPromotionResponseExtension promotionResponse
                    = visitorsIq.getExtension(VisitorsPromotionResponseExtension.class);

            if (promotionResponse != null && promotionResponse.isAllowed())
            {
                if (JvbConference.this.gatewaySession instanceof SipGatewaySession)
                {
                    try
                    {
                        ((SipGatewaySession) JvbConference.this.gatewaySession)
                            .sendJson(SipInfoJsonProtocol.createSIPCallVisitors(false));
                    }
                    catch (OperationFailedException e)
                    {
                        logger.error("Failed to send visitor update via sip", e);
                    }
                }
                // let's reconnect to the main prosody
                try
                {
                    leaveConferenceRoom();

                    // we will wait for the provider unregister event before proceeding with the new connection
                    JigasiBundleActivator.osgiContext.addServiceListener(new ServiceListener()
                    {
                        @Override
                        public void serviceChanged(ServiceEvent serviceEvent)
                        {
                            if (serviceEvent.getType() != ServiceEvent.UNREGISTERING)
                            {
                                return;
                            }

                            ServiceReference<?> ref = serviceEvent.getServiceReference();

                            Object service = JigasiBundleActivator.osgiContext.getService(ref);

                            if (!(service instanceof ProtocolProviderService)
                                    || !JvbConference.this.xmppProvider.equals(service))
                            {
                                return;
                            }

                            JigasiBundleActivator.osgiContext.removeServiceListener(this);

                            JvbConference.this.xmppProvider.removeRegistrationStateChangeListener(JvbConference.this);
                            JvbConference.this.xmppProvider = null;

                            try
                            {
                                JvbConference.this.callContext.setRoomName(JvbConference.this.oldRoom);

                                Localpart resourceIdentifier = getResourceIdentifier();

                                // create a new account, it will match the domain for the userID from the room jid we
                                // just updated
                                Map<String, String> props
                                    = createAccountPropertiesForCallId(resourceIdentifier.toString());

                                props.put(JabberAccountID.BOSH_URL,
                                    JvbConference.this.oldBosh + "&customusername=" + promotionResponse.getUsername());

                                JvbConference.this.oldBosh = null;
                                JvbConference.this.oldRoom = null;
                                JvbConference.this.isVisitor = false;
                                JvbConference.this.skipFocus = true;

                                JvbConference.this.createAndLoadAccount(props);
                            }
                            catch (XmppStringprepException e)
                            {
                                throw new RuntimeException(e);
                            }
                        }
                    });

                    if (jvbCall != null)
                    {
                        CallManager.hangupCall(jvbCall, true);
                    }
                }
                catch (Exception e)
                {
                    throw new RuntimeException(e);
                }
            }
            else
            {
                logger.info(JvbConference.this.callContext
                    + " Missing extension or promotion denied: " + promotionResponse);
            }

            return IQ.createResultIQ(visitorsIq);
        }
    }

    /**
     * Used to check the jvb side of the call for any activity.
     */
    private class MediaActivityChecker
        extends TimerTask
    {
        @Override
        public void run()
        {
            // if the call was stopped before we check ignore
            if (!started || jvbCall == null)
            {
                logger.warn("Media activity checker exiting early as call is not started or jvbCall is stopped!");
                return;
            }

            CallPeer peer = jvbCall.getCallPeers().next();

            if (peer == null)
            {
                dropCall();
            }

            if (peer instanceof MediaAwareCallPeer)
            {
                MediaAwareCallPeer peerMedia = (MediaAwareCallPeer) peer;

                CallPeerMediaHandler mediaHandler = peerMedia.getMediaHandler();
                if (mediaHandler != null)
                {
                    MediaStream stream = mediaHandler.getStream(MediaType.AUDIO);

                    if (stream == null)
                    {
                        dropCall();
                    }

                    if (stream instanceof AudioMediaStreamImpl)
                    {
                        try
                        {
                            // if there is no activity on the audio channel this means there is a problem
                            // establishing the media path with the bridge, so we can just fail the call
                            if (((AudioMediaStreamImpl) stream).getLastInputActivityTime() <= 0)
                            {
                                dropCall();
                            }
                        }
                        catch(IOException e)
                        {
                            logger.error("Error obtaining last activity while checking for media activity", e);
                        }
                    }
                }
                else
                {
                    dropCall();
                }
            }
        }

        /**
         * Drops the current call as there was no media path established.
         */
        private void dropCall()
        {
            Statistics.incrementTotalCallsJvbNoMedia();
            logger.error(callContext + " No activity on JVB conference call will stop");

            stop();
        }
    }
}
