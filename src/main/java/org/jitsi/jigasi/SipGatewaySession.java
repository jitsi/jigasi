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

import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.protocol.event.*;
import net.java.sip.communicator.service.protocol.media.*;
import net.java.sip.communicator.util.Logger;
import org.jitsi.impl.neomedia.*;
import org.jitsi.jigasi.sip.*;
import org.jitsi.jigasi.stats.*;
import org.jitsi.jigasi.util.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.utils.concurrent.*;
import org.jitsi.utils.*;
import org.jivesoftware.smack.packet.*;
import org.json.simple.*;

import java.io.*;
import java.text.*;
import java.util.*;
import java.util.concurrent.atomic.*;

/**
 * Class represents gateway session which manages single SIP call instance
 * (outgoing or incoming).
 *
 * @author Pawel Domas
 * @author Nik Vaessen
 */
public class SipGatewaySession
    extends AbstractGatewaySession
{
    /**
     * The logger.
     */
    private final static Logger logger = Logger.getLogger(
            SipGatewaySession.class);

    /**
     * The name of the room password header to check in headers for a room
     * password to use when joining the Jitsi Meet conference.
     */
    private final String roomPassHeaderName;

    /**
     * Default value of extra INVITE header which specifies password required
     * to enter MUC room that is hosting the Jitsi Meet conference.
     */
    public static final String JITSI_MEET_ROOM_PASS_HEADER_DEFAULT
        = "Jitsi-Conference-Room-Pass";

    /**
     * Name of extra INVITE header which specifies password required to enter
     * MUC room that is hosting the Jitsi Meet conference.
     */
    private static final String JITSI_MEET_ROOM_PASS_HEADER_PROPERTY
        = "JITSI_MEET_ROOM_PASS_HEADER_NAME";

    /**
     * Account property name of custom name for extra INVITE header which
     * specifies name of MUC room that is hosting the Jitsi Meet conference.
     */
    private static final String JITSI_MEET_ROOM_HEADER_PROPERTY
        = "JITSI_MEET_ROOM_HEADER_NAME";

    /**
     * The name of the header to search in the INVITE headers for base domain
     * to be used to extract the subdomain from the roomname in order
     * to construct custom bosh URL to enter MUC room that is hosting
     * the Jitsi Meet conference.
     */
    private final String domainBaseHeaderName;

    /**
     * Defult value optional INVITE header which specifies the base domain
     * to be used to extract the subdomain from the roomname in order
     * to construct custom bosh URL to enter MUC room that is hosting
     * the Jitsi Meet conference.
     */
    public static final String JITSI_MEET_DOMAIN_BASE_HEADER_DEFAULT
        = "Jitsi-Conference-Domain-Base";

    /**
     * The account property to use to set custom header name for domain base.
     */
    private static final String JITSI_MEET_DOMAIN_BASE_HEADER_PROPERTY
        = "JITSI_MEET_DOMAIN_BASE_HEADER_NAME";

    /**
     * Default status of our participant before we get any state from
     * the <tt>CallPeer</tt>.
     */
    private static final String INIT_STATUS_NAME = "Initializing Call";

    /**
     * The name of the property that is used to enable detection of
     * incoming sip RTP drop. Specifying the time with no media that
     * we consider that the call had gone bad and we log an error or hang it up.
     */
    private static final String P_NAME_MEDIA_DROPPED_THRESHOLD_MS
        = "org.jitsi.jigasi.SIP_MEDIA_DROPPED_THRESHOLD_MS";

    /**
     * By default we consider sip call bad if there is no RTP for 10 seconds.
     */
    private static final int DEFAULT_MEDIA_DROPPED_THRESHOLD = 10*1000;

    /**
     * The name of the property that is used to indicate whether we will hangup
     * sip calls with no RTP after some timeout.
     */
    private static final String P_NAME_HANGUP_SIP_ON_MEDIA_DROPPED
        = "org.jitsi.jigasi.HANGUP_SIP_ON_MEDIA_DROPPED";

    /**
     * The threshold configured for detecting dropped media.
     */
    private static final int mediaDroppedThresholdMs
        = JigasiBundleActivator.getConfigurationService().getInt(
            P_NAME_MEDIA_DROPPED_THRESHOLD_MS,
            DEFAULT_MEDIA_DROPPED_THRESHOLD);

    /**
     * The executor which periodically calls {@link ExpireMediaStream}.
     */
    private static final RecurringRunnableExecutor EXECUTOR
        = new RecurringRunnableExecutor(ExpireMediaStream.class.getName());

    /**
     * Manages all sound notifications that are sent to the sip side.
     */
    private final SoundNotificationManager soundNotificationManager
        = new SoundNotificationManager(this);

    /**
     * The runnable responsible for checking sip call incoming RTP and detecting
     * if media stop.
     */
    private ExpireMediaStream expireMediaStream;

    /**
     * The {@link OperationSetJitsiMeetTools} for SIP leg.
     */
    private final OperationSetJitsiMeetTools jitsiMeetTools;

    /**
     * The SIP call instance if any SIP call is active.
     */
    private Call sipCall;

    /**
     * Stores JVB call instance that will be merged into single conference with
     * SIP call.
     */
    private Call jvbConferenceCall;

    /**
     * Object listens for SIP call state changes.
     */
    private final SipCallStateListener callStateListener
        = new SipCallStateListener();

    /**
     * Peers state listener that publishes peer state in MUC presence status.
     */
    private CallPeerListener peerStateListener;

    /**
     * IF we work in outgoing connection mode then this field contains the SIP
     * number to dial.
     */
    private String destination;

    /**
     * SIP protocol provider instance.
     */
    private ProtocolProviderService sipProvider;

    /**
     * FIXME: to be removed ?
     */
    private final Object waitLock = new Object();

    /**
     * FIXME: JVB room name property is not available at the moment when call
     *        is created, because header is not parsed yet
     */
    private WaitForJvbRoomNameThread waitThread;

    /**
     * The stats handler that handles statistics on the sip side.
     */
    private StatsHandler statsHandler = null;

    /**
     * The default remote endpoint id to use for statistics.
     */
    public static final String DEFAULT_STATS_REMOTE_ID = "sip";

    /**
     * A transformer that monitors RTP and RTCP traffic going and coming
     * from the sip direction. Skips forwarding RTCP traffic which is not
     * intended for that direction (particularly we had seen RTCP.BYE for
     * to cause media to stop (even when ssrc is not matching)).
     */
    private SipCallKeepAliveTransformer transformerMonitor;

    /**
     * Whether we had send indication that XMPP connection terminated and
     * the gateway session was connected to a new XMPP call.
     */
    private boolean callReconnectedStatsSent = false;

    /**
     * True if call should start muted, false otherwise.
     */
    private boolean startAudioMuted = false;

    /**
     * The sip info protocol used to through pstn.
     */
    private final SipInfoJsonProtocol sipInfoJsonProtocol;

    /**
     * Creates new <tt>SipGatewaySession</tt> for given <tt>callResource</tt>
     * and <tt>sipCall</tt>. We already have SIP call instance, so this session
     * can be considered "incoming" SIP session(was created after incoming call
     * had been received).
     *
     * @param gateway the <tt>SipGateway</tt> instance that will control this
     *                session.
     * @param callContext the call context that identifies this session.
     * @param sipCall the incoming SIP call instance which will be handled by
     *                this session.
     */
    public SipGatewaySession(SipGateway gateway,
                             CallContext callContext,
                             Call       sipCall)
    {
        this(gateway, callContext);
        this.sipCall = sipCall;
    }

    /**
     * Creates new <tt>SipGatewaySession</tt> that can be used to initiate outgoing
     * SIP gateway session by using
     * {@link #createOutgoingCall()}
     * method.
     *
     * @param gateway the {@link SipGateway} the <tt>SipGateway</tt> instance
     *                that will control this session.
     * @param callContext the call context that identifies this session.
     */
    public SipGatewaySession(SipGateway gateway, CallContext callContext)
    {
        super(gateway, callContext);
        this.sipProvider = gateway.getSipProvider();
        this.jitsiMeetTools
            = sipProvider.getOperationSet(
                    OperationSetJitsiMeetTools.class);

        // check for custom header name for room pass header
        roomPassHeaderName = sipProvider.getAccountID()
            .getAccountPropertyString(
                JITSI_MEET_ROOM_PASS_HEADER_PROPERTY,
                JITSI_MEET_ROOM_PASS_HEADER_DEFAULT);

        // check for custom header name for domain base header
        domainBaseHeaderName = sipProvider.getAccountID()
            .getAccountPropertyString(
                JITSI_MEET_DOMAIN_BASE_HEADER_PROPERTY,
                JITSI_MEET_DOMAIN_BASE_HEADER_DEFAULT);

        this.sipInfoJsonProtocol = new SipInfoJsonProtocol(jitsiMeetTools);
    }

    private void allCallsEnded()
    {
        CallContext ctx = super.callContext;

        super.gateway.notifyCallEnded(ctx);

        // clear call context after notifying that session ended as
        // listeners to still be able to check the values from context
        destination = null;
        callContext = null;
    }

    private void cancelWaitThread()
    {
        if (waitThread != null)
        {
            waitThread.cancel();
        }
    }

    /**
     * Starts new outgoing session by dialing given SIP number and joining JVB
     * conference held in given MUC room.
     */
    public void createOutgoingCall()
    {
        if (sipCall != null)
        {
            throw new IllegalStateException("SIP call in progress");
        }

        this.destination = callContext.getDestination();

        // connect to muc
        super.createOutgoingCall();
    }

    /**
     * Returns the instance of SIP call if any is currently in progress.

     * @return the instance of SIP call if any is currently in progress.
     */
    public Call getSipCall()
    {
        return sipCall;
    }

    public void hangUp()
    {
        hangUp(-1, null);
    }

    private void hangUp(int reasonCode, String reason)
    {
        super.hangUp(); // to leave JvbConference
        hangUpSipCall(reasonCode, reason);
    }

    /**
     * Cancels current session by canceling sip call
     */
    private void hangUpSipCall(int reasonCode, String reason)
    {
        cancelWaitThread();

        if (sipCall != null)
        {
            if (reasonCode != -1)
                CallManager.hangupCall(sipCall, reasonCode, reason);
            else
                CallManager.hangupCall(sipCall);
        }
    }

    /**
     * Starts a JvbConference with the call context identifying this session.
     * @param ctx the call context of current session.
     */
    private void joinJvbConference(CallContext ctx)
    {
        cancelWaitThread();

        jvbConference = new JvbConference(this, ctx);

        jvbConference.start();
    }

    /*private void joinSipWithJvbCalls()
    {
        List<Call> calls = new ArrayList<Call>();
        calls.add(call);
        calls.add(jvbConferenceCall);

        CallManager.mergeExistingCalls(
            jvbConferenceCall.getConference(), calls);

        sendPresenceExtension(
            createPresenceExtension(
                SipGatewayExtension.STATE_IN_PROGRESS, null));

        jvbConference.setPresenceStatus(
            SipGatewayExtension.STATE_IN_PROGRESS);
    }*/

    void onConferenceCallInvited(Call incomingCall)
    {
        // Incoming SIP connection mode sets common conference here
        if (destination == null)
        {
            incomingCall.setConference(sipCall.getConference());

            boolean useTranslator = incomingCall.getProtocolProvider()
                .getAccountID().getAccountPropertyBoolean(
                    ProtocolProviderFactory.USE_TRANSLATOR_IN_CONFERENCE,
                    false);
            CallPeer peer = incomingCall.getCallPeers().next();
            // if use translator is enabled add a ssrc rewriter
            if (useTranslator && !addSsrcRewriter(peer))
            {
                peer.addCallPeerListener(new CallPeerAdapter()
                {
                    @Override
                    public void peerStateChanged(CallPeerChangeEvent evt)
                    {
                        CallPeer peer = evt.getSourceCallPeer();
                        CallPeerState peerState = peer.getState();

                        if (CallPeerState.CONNECTED.equals(peerState))
                        {
                            peer.removeCallPeerListener(this);
                            addSsrcRewriter(peer);
                        }
                    }
                });
            }
        }

        Exception error = this.onConferenceCallStarted(incomingCall);

        if (error != null)
        {
            logger.error(this.callContext + " " + error, error);

            if (error instanceof OperationFailedException
                && !CallManager.isHealthy())
            {
                OperationFailedException ex = (OperationFailedException)error;
                // call manager is not healthy so call will not succeed
                // let's drop it
                hangUpSipCall(ex.getErrorCode(), ex.getMessage());
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    Exception onConferenceCallStarted(Call jvbConferenceCall)
    {
        this.jvbConferenceCall = jvbConferenceCall;

        if (destination == null)
        {
            try
            {
                CallManager.acceptCall(sipCall);
            }
            catch(OperationFailedException e)
            {
                hangUpSipCall(e.getErrorCode(), "Cannot answer call");

                return e;
            }
        }
        else
        {
            if (this.sipCall != null)
            {
                logger.info(
                    this.callContext +
                    " Connecting existing sip call to incoming xmpp call "
                        + this);

                jvbConferenceCall.setConference(sipCall.getConference());

                try
                {
                    CallManager.acceptCall(jvbConferenceCall);
                }
                catch(OperationFailedException e)
                {
                    return e;
                }

                if (!callReconnectedStatsSent)
                {
                    Statistics.incrementTotalCallsWithSipCallReconnected();
                    callReconnectedStatsSent = true;
                }

                return null;
            }

            //sendPresenceExtension(
              //  createPresenceExtension(
                //    SipGatewayExtension.STATE_RINGING, null));

            //if (jvbConference != null)
            //{
              //  jvbConference.setPresenceStatus(
                //    SipGatewayExtension.STATE_RINGING);
            //}

            // Make an outgoing call
            final OperationSetBasicTelephony tele
                = sipProvider.getOperationSet(
                        OperationSetBasicTelephony.class);
            // add listener to detect call creation, and add extra headers
            // before inviting, and remove the listener when job is done
            tele.addCallListener(new CallListener()
            {
                @Override
                public void incomingCallReceived(CallEvent callEvent)
                {}

                @Override
                public void outgoingCallCreated(CallEvent callEvent)
                {
                    String roomName = getJvbRoomName();
                    if(roomName != null)
                    {
                        Call call = callEvent.getSourceCall();
                        AtomicInteger headerCount = new AtomicInteger(0);
                        call.setData(
                            "EXTRA_HEADER_NAME." + headerCount.addAndGet(1),
                            sipProvider.getAccountID()
                                .getAccountPropertyString(
                                    JITSI_MEET_ROOM_HEADER_PROPERTY,
                                    "Jitsi-Conference-Room"));
                        call.setData(
                            "EXTRA_HEADER_VALUE." + headerCount.get(),
                            roomName);

                        // passes all extra headers to the outgoing call
                        callContext.getExtraHeaders().forEach(
                            (key, value) ->
                            {
                                call.setData(
                                    "EXTRA_HEADER_NAME."
                                        + headerCount.addAndGet(1),
                                    key);
                                call.setData(
                                    "EXTRA_HEADER_VALUE."
                                        + headerCount.get(),
                                    value);
                            });
                    }

                    tele.removeCallListener(this);
                }

                @Override
                public void callEnded(CallEvent callEvent)
                {
                    tele.removeCallListener(this);
                }
            });
            try
            {
                this.sipCall = tele.createCall(destination);
                this.initSipCall();

                // Outgoing SIP connection mode sets common conference object
                // just after the call has been created
                jvbConferenceCall.setConference(sipCall.getConference());

                logger.info(
                    this.callContext + " Created outgoing call to " + this);

                //FIXME: It might be already in progress or ended ?!
                if (!CallState.CALL_INITIALIZATION.equals(sipCall.getCallState()))
                {
                    callStateListener.handleCallState(sipCall, null);
                }
            }
            catch (OperationFailedException | ParseException e)
            {
                return e;
            }
        }

        try
        {
            CallManager.acceptCall(jvbConferenceCall);
        }
        catch(OperationFailedException e)
        {
            return e;
        }

        return null;
    }

    /**
     * {@inheritDoc}
     */
    void onJvbConferenceStopped(JvbConference jvbConference,
                                int reasonCode, String reason)
    {
        this.jvbConference = null;

        if (sipCall != null)
        {
            hangUp(reasonCode, reason);
        }
        else
        {
            allCallsEnded();
        }
    }

    @Override
    void onJvbConferenceWillStop(JvbConference jvbConference, int reasonCode,
        String reason)
    {}

    private void sipCallEnded()
    {
        if (sipCall == null)
            return;

        logger.info(this.callContext
            + " Sip call ended: " + sipCall.toString());

        sipCall.removeCallChangeListener(callStateListener);

        if (statsHandler != null)
        {
            statsHandler.dispose();
            statsHandler = null;
        }

        jitsiMeetTools.removeRequestListener(SipGatewaySession.this);

        if (peerStateListener != null)
            peerStateListener.unregister();

        if (this.transformerMonitor != null)
        {
            this.transformerMonitor.dispose();
            this.transformerMonitor = null;
        }

        sipCall = null;

        if (jvbConference != null)
        {
            jvbConference.stop();
        }
        else
        {
            allCallsEnded();
        }
    }

    @Override
    public void onJoinJitsiMeetRequest(
        Call call, String room, Map<String, String> data)
    {
        if (jvbConference == null && this.sipCall == call)
        {
            if (room != null)
            {
                callContext.setRoomName(room);
                callContext.setRoomPassword(data.get(roomPassHeaderName));
                callContext.setDomain(data.get(domainBaseHeaderName));
                callContext.setMucAddressPrefix(sipProvider.getAccountID()
                    .getAccountPropertyString(
                        CallContext.MUC_DOMAIN_PREFIX_PROP, "conference"));

                // we have the room information, lets add cs to incoming call
                if (statsHandler == null)
                {
                    String sipCallIdentifier = this.getMucDisplayName();
                    statsHandler = new StatsHandler(
                        sipCall, sipCallIdentifier, DEFAULT_STATS_REMOTE_ID + "-" + sipCallIdentifier);
                }

                joinJvbConference(callContext);
            }
            else
            {
                logger.warn("No JVB room name provided in INVITE header.");
                logger.info("Count of headers received:" + (data != null ? data.size() : 0));
            }
        }
    }

    /**
     * Received if StartMutedExtension is handled.
     *
     * @param startMutedFlags [0] represents audio stream should be muted,
     * [1] represents video stream should be muted.
     */
    @Override
    public void onSessionStartMuted(boolean[] startMutedFlags)
    {
        if (isMutingSupported())
        {
            this.startAudioMuted = startMutedFlags[0];
        }
    }

    /**
     * Received JSON over SIP from callPeer.
     *
     * @param callPeer callPeer that sent the JSON.
     * @param jsonObject JSON that was sent.
     * @param params Implementation specific parameters.
     */
    @Override
    public void onJSONReceived(CallPeer callPeer,
                               JSONObject jsonObject,
                               Map<String, Object> params)
    {
        try
        {
            if (callPeer.getCall() != this.sipCall)
            {
                if (logger.isTraceEnabled())
                {
                    logger.trace(this.callContext
                        + " Ignoring event for non session call.");
                }
                return;
            }

            int msgId = -1;

            if (jsonObject.containsKey("i"))
            {
                msgId = ((Long)jsonObject.get("i")).intValue();
                if (logger.isDebugEnabled())
                {
                    logger.debug("Received message " + msgId);
                }
            }

            if (jsonObject.containsKey("t"))
            {
                int messageType = ((Long)jsonObject.get("t")).intValue();

                if (messageType == SipInfoJsonProtocol.MESSAGE_TYPE.REQUEST_ROOM_ACCESS)
                {
                    String password = this.sipInfoJsonProtocol.getPasswordFromRoomAccessRequest(jsonObject);
                    this.jvbConference.onPasswordReceived(password);
                }
            }

            if (jsonObject.containsKey("type"))
            {

                if (!jsonObject.containsKey("id"))
                {
                    logger.error(this.callContext + " Unknown json object id!");
                    return;
                }

                String id = (String)jsonObject.get("id");
                String type = (String)jsonObject.get("type");

                if (type.equalsIgnoreCase("muteResponse"))
                {
                    if (!jsonObject.containsKey("status"))
                    {
                        logger.error(this.callContext
                                + " muteResponse without status!");
                        return;
                    }

                    if (((String) jsonObject.get("status")).equalsIgnoreCase("OK"))
                    {
                        JSONObject data = (JSONObject) jsonObject.get("data");

                        boolean bMute = (boolean)data.get("audio");

                        // Send presence audio muted
                        this.jvbConference.setChatRoomAudioMuted(bMute);
                    }
                }
                else if (type.equalsIgnoreCase("muteRequest"))
                {
                    JSONObject data = (JSONObject) jsonObject.get("data");

                    boolean bAudioMute = (boolean)data.get("audio");

                    // Send request to jicofo
                    if (jvbConference.requestAudioMute(bAudioMute))
                    {
                        // Send response through sip
                        respondRemoteAudioMute(bAudioMute,
                                true,
                                callPeer,
                                id);

                        // Send presence if response succeeded
                        this.jvbConference.setChatRoomAudioMuted(bAudioMute);
                    }
                    else
                    {
                        respondRemoteAudioMute(bAudioMute,
                                false,
                                callPeer,
                                id);
                    }
                }
            }
        }
        catch(Exception ex)
        {
            logger.error(this.callContext + " " + ex.getMessage());
        }
    }

    /**
     * Creates a basic JSONObject to be sent over SIP.
     *
     * @param type Used to identify the JSON.
     * @param data Used for JSON additional attributed.
     * @param id Used for identification.
     * @return Formed JSONObject.
     */
    private JSONObject createSIPJSON(String type, JSONObject data, String id)
    {
        JSONObject req = new JSONObject();
        req.put("type", type);
        req.put("data", data);
        req.put("id", id == null ? UUID.randomUUID().toString() : id);
        return req;
    }

    /**
     * Creates a JSONObject to request audio to be muted over SIP.
     *
     * @param muted <tt>true</tt> if audio is to be muted, <tt>false</tt> otherwise.
     * @return Formed JSONObject.
     */
    private JSONObject createSIPJSONAudioMuteRequest(boolean muted)
    {
        JSONObject muteSettingsJson = new JSONObject();
        muteSettingsJson.put("audio", muted);

        return createSIPJSON("muteRequest", muteSettingsJson, null);
    }

    /**
     * Creates a JSONObject as response to a muteRequest.
     *
     * @param muted <tt>true</tt> if audio was muted, <tt>false</tt> otherwise.
     * @param bSucceeded <tt>true</tt> if muteRequest succeeded, <tt>false</tt> otherwise.
     * @param id Represents id of muteRequest.
     * @return Formed JSONObject.
     */
    private JSONObject createSIPJSONAudioMuteResponse(boolean muted,
                                                        boolean bSucceeded,
                                                        String id)
    {
        JSONObject muteSettingsJson = new JSONObject();
        muteSettingsJson.put("audio", muted);
        JSONObject muteResponseJson
            = createSIPJSON("muteResponse", muteSettingsJson, id);
        muteResponseJson.put("status", bSucceeded ? "OK" : "FAILED");
        return muteResponseJson;
    }

    /**
     * Sends a JSON request over SIP to mute callPeer with muted flag.
     *
     * @param muted true if audio should be muted, false otherwise.
     * @param callPeer CallPeer to send JSON to.
     * @throws OperationFailedException
     */
    private void requestRemoteAudioMute(boolean muted, CallPeer callPeer)
        throws OperationFailedException
    {
        // Mute audio
        JSONObject muteRequestJson = createSIPJSONAudioMuteRequest(muted);

        jitsiMeetTools.sendJSON(callPeer,
                                muteRequestJson,
                                new HashMap<String, Object>(){{
                                    put("VIA", "SIP.INFO");
                                }});
    }

    /**
     * Sends a JSON muteResponse over SIP to callPeer with specified flag.
     *
     * @param muted true if audio was muted, false otherwise.
     * @param bSucceeded <tt>true</tt> if request succeeded, <tt>false</tt> otherwise.
     * @param callPeer CallPeer to send response to.
     * @param id Set as muteRequest id.
     * @throws OperationFailedException
     */
    private void respondRemoteAudioMute(boolean muted,
                                        boolean bSucceeded,
                                        CallPeer callPeer,
                                        String id)
        throws OperationFailedException
    {
        JSONObject muteResponseJson
            = createSIPJSONAudioMuteResponse(muted, bSucceeded, id);

        jitsiMeetTools.sendJSON(callPeer,
                                muteResponseJson,
                                new HashMap<String, Object>() {{
                                    put("VIA", "SIP.INFO");
                                }});
    }

    /**
     * Initializes the sip call listeners.
     */
    private void initSipCall()
    {
        sipCall.setData(CallContext.class, super.callContext);
        sipCall.addCallChangeListener(callStateListener);

        jitsiMeetTools.addRequestListener(this);

        if (mediaDroppedThresholdMs != -1)
        {
            CallPeer peer = sipCall.getCallPeers().next();
            if(!addExpireRunnable(peer))
            {
                peer.addCallPeerListener(new CallPeerAdapter()
                {
                    @Override
                    public void peerStateChanged(CallPeerChangeEvent evt)
                    {
                        CallPeer peer = evt.getSourceCallPeer();
                        CallPeerState peerState = peer.getState();

                        if(CallPeerState.CONNECTED.equals(peerState))
                        {
                            peer.removeCallPeerListener(this);
                            addExpireRunnable(peer);
                        }
                    }
                });
            }
        }

        peerStateListener = new CallPeerListener(sipCall);

        boolean useTranslator = sipCall.getProtocolProvider()
            .getAccountID().getAccountPropertyBoolean(
                ProtocolProviderFactory.USE_TRANSLATOR_IN_CONFERENCE,
                false);

        CallPeer sipPeer = sipCall.getCallPeers().next();
        if (useTranslator && !addSipCallTransformer(sipPeer))
        {
            sipPeer.addCallPeerListener(new CallPeerAdapter()
            {
                @Override
                public void peerStateChanged(CallPeerChangeEvent evt)
                {
                    CallPeer peer = evt.getSourceCallPeer();
                    CallPeerState peerState = peer.getState();

                    if (CallPeerState.CONNECTED.equals(peerState))
                    {
                        peer.removeCallPeerListener(this);
                        addSipCallTransformer(peer);
                    }
                }
            });
        }
    }

    /**
     * Initializes this instance for incoming call which was passed to the
     * constructor {@link #SipGatewaySession(SipGateway, CallContext, Call)}.
     */
    void initIncomingCall()
    {
        initSipCall();

        if (jvbConference != null)
        {
            // Reject incoming call
            CallManager.hangupCall(sipCall);
        }
        else
        {
            waitForRoomName();
        }
    }

    private void waitForRoomName()
    {
        if (waitThread != null)
        {
            throw new IllegalStateException("Wait thread exists");
        }

        waitThread = new WaitForJvbRoomNameThread();

        waitThread.start();
    }

    /**
     * Returns {@link Call} instance for JVB leg of the conference.
     */
    public Call getJvbCall()
    {
        return jvbConferenceCall;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void toneReceived(DTMFReceivedEvent dtmfReceivedEvent)
    {
        if (dtmfReceivedEvent != null
                && dtmfReceivedEvent.getSource() == jvbConferenceCall)
        {
            OperationSetDTMF opSet
                    = sipProvider.getOperationSet(OperationSetDTMF.class);
            if (opSet != null && dtmfReceivedEvent.getStart() != null)
            {
                if (dtmfReceivedEvent.getStart())
                {
                    try
                    {
                        opSet.startSendingDTMF(
                                peerStateListener.thePeer,
                                dtmfReceivedEvent.getValue());
                    }
                    catch (OperationFailedException ofe)
                    {
                        logger.info(this.callContext
                            + " Failed to forward a DTMF tone: " + ofe);
                    }
                }
                else
                {
                    opSet.stopSendingDTMF(peerStateListener.thePeer);
                }
            }
        }
    }

    @Override
    public boolean isTranslatorSupported()
    {
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDefaultInitStatus()
    {
        return INIT_STATUS_NAME;
    }

    /**
     * Adds a ssrc rewriter to the peers media stream.
     * @param peer the peer which media streams to manipulate
     * @return true if rewriter was added to peer's media stream.
     */
    private boolean addSsrcRewriter(CallPeer peer)
    {
        if (peer instanceof MediaAwareCallPeer)
        {
            MediaAwareCallPeer peerMedia = (MediaAwareCallPeer) peer;

            CallPeerMediaHandler mediaHandler
                = peerMedia.getMediaHandler();
            if (mediaHandler != null)
            {
                MediaStream stream = mediaHandler.getStream(MediaType.AUDIO);
                if (stream != null)
                {
                    stream.setExternalTransformer(
                        new SsrcRewriter(stream.getLocalSourceID()));
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Adds a thread that will be checking the sip call for incoming RTP
     * and log or hangup it when we hit the threshold.
     * @param peer the call peer.
     * @return whether had started the thread.
     */
    private boolean addExpireRunnable(CallPeer peer)
    {
        if (peer instanceof MediaAwareCallPeer)
        {
            MediaAwareCallPeer peerMedia = (MediaAwareCallPeer) peer;

            CallPeerMediaHandler mediaHandler = peerMedia.getMediaHandler();
            if (mediaHandler != null)
            {
                MediaStream stream = mediaHandler.getStream(MediaType.AUDIO);
                if (stream != null)
                {
                    expireMediaStream
                        = new ExpireMediaStream((AudioMediaStreamImpl)stream);
                    EXECUTOR.registerRecurringRunnable(expireMediaStream);
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    public String getMucDisplayName()
    {
        String mucDisplayName = null;

        String sipDestination = callContext.getDestination();
        Call sipCall = getSipCall();

        if (sipDestination != null)
        {
            mucDisplayName = sipDestination;
        }
        else if (sipCall != null && sipCall.getCallPeers().hasNext())
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
     * Called by JvbConference when jvb session ended.
     */
    @Override
    public void onJvbCallEnded()
    {
        if (this.soundNotificationManager != null)
        {
            this.soundNotificationManager.onJvbCallEnded();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    void notifyChatRoomMemberUpdated(
        ChatRoomMember chatMember, Presence presence)
    {
        // sound manager process
        soundNotificationManager.process(presence);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    void notifyChatRoomMemberJoined(ChatRoomMember member)
    {
        super.notifyChatRoomMemberJoined(member);

        if (soundNotificationManager != null)
        {
            soundNotificationManager.notifyChatRoomMemberJoined(member);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    void notifyChatRoomMemberLeft(ChatRoomMember member)
    {
        super.notifyChatRoomMemberLeft(member);

        if (soundNotificationManager != null)
        {
            soundNotificationManager.notifyChatRoomMemberLeft(member);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    void notifyJvbRoomJoined()
    {
        super.notifyJvbRoomJoined();

        if (soundNotificationManager != null)
        {
            soundNotificationManager.notifyJvbRoomJoined();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void notifyOnLobbyWaitReview(ChatRoom lobbyRoom)
    {
        super.notifyOnLobbyWaitReview(lobbyRoom);

        if (soundNotificationManager != null)
        {
            soundNotificationManager.notifyLobbyWaitReview();
        }

        this.notifyLobbyJoined();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    void handleMaxOccupantsLimitReached()
    {
        soundNotificationManager.indicateMaxOccupantsLimitReached();
    }

    /**
     * Adds a sip transformer monitor to the peers media stream.
     * @param peer the peer which media streams to manipulate
     * @return true if transformer was added to peer's media stream.
     */
    private boolean addSipCallTransformer(CallPeer peer)
    {
        if (peer instanceof MediaAwareCallPeer)
        {
            MediaAwareCallPeer peerMedia = (MediaAwareCallPeer) peer;

            CallPeerMediaHandler mediaHandler
                = peerMedia.getMediaHandler();
            if (mediaHandler != null)
            {
                MediaStream stream = mediaHandler.getStream(MediaType.AUDIO);
                if (stream != null)
                {
                    transformerMonitor = new SipCallKeepAliveTransformer(
                        peerMedia.getMediaHandler(), stream);
                    stream.setExternalTransformer(transformerMonitor);
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    public String toString()
    {
        return "SipGatewaySession{" +
            "sipCall=" + sipCall +
            ", destination='" + destination + '\'' +
            '}';
    }

    /**
     * {@inheritDoc}
     */
    public boolean hasCallResumeSupport()
    {
        return true;
    }

    /**
     * When
     */
    @Override
    public void onJvbCallEstablished()
    {
        maybeProcessStartMuted();
    }

    /**
     * Processes start muted in case:
     * - we had received that flag
     * - start muted is enabled through the flag
     * - jvb call is in progress as we will be muting the channels
     * - sip call is in progress we will be sending SIP Info messages
     */
    private void maybeProcessStartMuted()
    {
        if (this.startAudioMuted
            && isMutingSupported()
            && jvbConferenceCall != null
            && jvbConferenceCall.getCallState() == CallState.CALL_IN_PROGRESS
            && sipCall != null
            && sipCall.getCallState() == CallState.CALL_IN_PROGRESS)
        {
            if (jvbConference.requestAudioMute(startAudioMuted))
            {
                mute();
            }

            // in case we reconnect start muted maybe no-longer set
            this.startAudioMuted = false;
        }
    }

    /**
     * Sends mute request to be remotely muted.
     * This is a SIP Info message to the IVR so the user will be notified of it
     * When we receive confirmation for the announcement we will update
     * our presence status in the conference.
     */
    public void mute()
    {
        if (!isMutingSupported())
            return;

        // Notify peer
        CallPeer callPeer = sipCall.getCallPeers().next();

        try
        {
            logger.info(
                SipGatewaySession.this.callContext + " Sending mute request ");
            requestRemoteAudioMute(true, callPeer);
        }
        catch (Exception ex)
        {
            logger.error(this.callContext + " " + ex.getMessage());
        }
    }

    /**
     * Muting is supported when it is enabled by configuration.
     * @return <tt>true</tt> if mute support is enabled.
     */
    public boolean isMutingSupported()
    {
        return JigasiBundleActivator.isSipStartMutedEnabled();
    }

    /**
     * Returns the SipGatewaySession sound notification manager.
     *
     * @return <tt>SoundNotificationManager</tt>
     */
    public SoundNotificationManager getSoundNotificationManager()
    {
        return this.soundNotificationManager;
    }

    /**
     * Notifies received call that lobby was joined.
     */
    public void notifyLobbyJoined()
    {
        // Notify peer
        CallPeer callPeer = sipCall.getCallPeers().next();

        try
        {
            if (this.sipInfoJsonProtocol != null)
            {
                JSONObject request = this.sipInfoJsonProtocol.createLobbyJoinedNotification();
                this.sipInfoJsonProtocol.sendJson(callPeer, request);
            }
        }
        catch (Exception ex)
        {
            logger.error(this.callContext + " " + ex.getMessage());
        }
    }

    /**
     * Notifies received call that lobby was left.
     */
    public void notifyLobbyLeft()
    {
        // Notify peer
        CallPeer callPeer = sipCall.getCallPeers().next();

        try
        {
            if (this.sipInfoJsonProtocol != null)
            {
                JSONObject request = this.sipInfoJsonProtocol.createLobbyLeftNotification();
                this.sipInfoJsonProtocol.sendJson(callPeer, request);
            }
        }
        catch (Exception ex)
        {
            logger.error(this.callContext + " " + ex.getMessage());
        }
    }

    /**
     * Notifies received call that user was allowed to join while in lobby.
     */
    public void notifyLobbyAllowedJoin()
    {
        // Notify peer
        CallPeer callPeer = sipCall.getCallPeers().next();

        try
        {
            if (this.sipInfoJsonProtocol != null)
            {
                JSONObject request = this.sipInfoJsonProtocol.createLobbyAllowedJoinNotification();
                this.sipInfoJsonProtocol.sendJson(callPeer, request);
            }
        }
        catch (Exception ex)
        {
            logger.error(this.callContext + " " + ex.getMessage());
        }
    }

    /**
     * Notify received call that user was rejected to join while in lobby.
     */
    public void notifyLobbyRejectedJoin()
    {
        // Notify peer
        CallPeer callPeer = sipCall.getCallPeers().next();

        try
        {
            if (this.sipInfoJsonProtocol != null)
            {
                JSONObject request = this.sipInfoJsonProtocol.createLobbyRejectedJoinNotification();
                this.sipInfoJsonProtocol.sendJson(callPeer, request);
            }
        }
        catch (Exception ex)
        {
            logger.error(this.callContext + " " + ex.getMessage());
        }
    }

    /**
     * PeriodicRunnable that will check incoming RTP and if needed to hangup.
     */
    private class ExpireMediaStream
        extends PeriodicRunnable
    {
        /**
         * The stream to check.
         */
        private AudioMediaStreamImpl stream;

        /**
         * Whether we had sent stats for dropped media.
         */
        private boolean statsSent = false;

        public ExpireMediaStream(AudioMediaStreamImpl stream)
        {
            // we want to check every 2 seconds for the media state
            super(2000, false);
            this.stream = stream;
        }

        @Override
        public void run()
        {
            super.run();

            try
            {
                long lastReceived = stream.getLastInputActivityTime();

                if(System.currentTimeMillis() - lastReceived
                        > mediaDroppedThresholdMs)
                {
                    // we want to log only when we go from not-expired into
                    // expired state
                    if (!gatewayMediaDropped)
                    {
                        logger.error(
                            SipGatewaySession.this.callContext +
                            " Stopped receiving RTP for " + getSipCall());

                        if (!statsSent)
                        {
                            Statistics.incrementTotalCallsWithMediaDropped();
                            statsSent = true;
                        }
                    }

                    gatewayMediaDropped = true;

                    if (JigasiBundleActivator.getConfigurationService()
                        .getBoolean(P_NAME_HANGUP_SIP_ON_MEDIA_DROPPED, false))
                    {
                        CallManager.hangupCall(getSipCall(),
                            OperationSetBasicTelephony.HANGUP_REASON_TIMEOUT,
                            "Stopped receiving media");
                    }

                }
                else
                {
                    if (gatewayMediaDropped)
                    {
                        logger.info(SipGatewaySession.this.callContext
                            + " RTP resumed for " + getSipCall());
                    }
                    gatewayMediaDropped = false;
                }
            }
            catch(IOException e)
            {
                //Should not happen
                logger.error(SipGatewaySession.this.callContext
                    + " Should not happen exception", e);
            }
        }
    }

    class SipCallStateListener
        implements CallChangeListener
    {

        @Override
        public void callPeerAdded(CallPeerEvent evt) { }

        @Override
        public void callPeerRemoved(CallPeerEvent evt)
        {
            //if (evt.getSourceCall().getCallPeerCount() == 0)
            //  sipCallEnded();
        }

        @Override
        public void callStateChanged(CallChangeEvent evt)
        {
            handleCallState(evt.getSourceCall(), evt.getCause());
        }

        void handleCallState(Call call, CallPeerChangeEvent cause)
        {
            // Once call is started notify SIP gateway
            if (call.getCallState() == CallState.CALL_IN_PROGRESS)
            {
                logger.info(SipGatewaySession.this.callContext
                    + " Sip call IN_PROGRESS: " + call);
                //sendPresenceExtension(
                  //  createPresenceExtension(
                    //    SipGatewayExtension.STATE_IN_PROGRESS, null));

                //jvbConference.setPresenceStatus(
                  //  SipGatewayExtension.STATE_IN_PROGRESS);

                logger.info(SipGatewaySession.this.callContext
                    + " SIP call format used: "
                    + Util.getFirstPeerMediaFormat(call));

                maybeProcessStartMuted();
            }
            else if(call.getCallState() == CallState.CALL_ENDED)
            {
                logger.info(SipGatewaySession.this.callContext
                    + " SIP call ended: " + cause);

                if (peerStateListener != null)
                    peerStateListener.unregister();

                EXECUTOR.deRegisterRecurringRunnable(expireMediaStream);
                expireMediaStream = null;

                // If we have something to show and we're still in the MUC
                // then we display error reason string and leave the room with
                // 5 sec delay.
                if (cause != null
                    && jvbConference != null && jvbConference.isInTheRoom())
                {
                    // Show reason instead of disconnected
                    if (!StringUtils.isNullOrEmpty(cause.getReasonString()))
                    {
                        jvbConference.setPresenceStatus(
                            cause.getReasonString());
                    }

                    // Delay 5 seconds
                    new Thread(new Runnable()
                    {
                        @Override
                        public void run()
                        {
                            try
                            {
                                Thread.sleep(5000);

                                sipCallEnded();
                            }
                            catch (InterruptedException e)
                            {
                                Thread.currentThread().interrupt();
                            }
                        }
                    }).start();
                }
                else
                {
                    sipCallEnded();
                }
            }
        }
    }

    class CallPeerListener
        extends CallPeerAdapter
    {
        CallPeer thePeer;

        CallPeerListener(Call call)
        {
            thePeer = call.getCallPeers().next();
            thePeer.addCallPeerListener(this);
        }

        @Override
        public void peerStateChanged(final CallPeerChangeEvent evt)
        {
            CallPeerState callPeerState = (CallPeerState)evt.getNewValue();
            String stateString = callPeerState.getStateString();

            logger.info(callContext + " SIP peer state: " + stateString);

            if (jvbConference != null)
                jvbConference.setPresenceStatus(stateString);

            soundNotificationManager.process(callPeerState);
        }

        void unregister()
        {
            thePeer.removeCallPeerListener(this);
        }
    }

    /**
     * FIXME: to be removed
     */
    class WaitForJvbRoomNameThread
            extends Thread
    {
        private boolean cancel = false;

        @Override
        public void run()
        {
            synchronized (waitLock)
            {
                try
                {
                    waitLock.wait(1000);

                    if (cancel)
                    {
                        logger.info(SipGatewaySession.this.callContext
                            + " Wait thread cancelled");
                        return;
                    }

                    if (getJvbRoomName() == null
                           && !CallState.CALL_ENDED.equals(sipCall.getCallState()))
                    {
                        String defaultRoom
                            = JigasiBundleActivator.getConfigurationService()
                                .getString(SipGateway.P_NAME_DEFAULT_JVB_ROOM);

                        if (defaultRoom != null)
                        {
                            logger.info(
                                SipGatewaySession.this.callContext
                                + "Using default JVB room name property "
                                + defaultRoom);

                            callContext.setRoomName(defaultRoom);

                            joinJvbConference(callContext);
                        }
                        else
                        {
                            logger.warn(
                                SipGatewaySession.this.callContext
                                + " No JVB room name provided in INVITE header"
                            );

                            hangUp(OperationSetBasicTelephony.HANGUP_REASON_BUSY_HERE, "No JVB room name provided");
                        }
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
            if (Thread.currentThread() == waitThread)
            {
                waitThread = null;
                return;
            }

            synchronized (waitLock)
            {
                cancel = true;
                waitLock.notifyAll();
            }
            try
            {
                waitThread.join();
                waitThread = null;
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
            }
        }
    }
}
