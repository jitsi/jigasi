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
import net.java.sip.communicator.util.*;
import org.jitsi.jigasi.sip.*;
import org.jitsi.jigasi.util.*;
import org.jitsi.xmpp.extensions.jitsimeet.*;
import org.jivesoftware.smack.*;
import org.jivesoftware.smack.iqrequest.*;
import org.jivesoftware.smack.packet.*;
import org.jxmpp.jid.*;

import org.json.simple.*;

import java.util.*;

/**
 * Handles all the mute/unmute/startMuted logic.
 */
public class AudioModeration
{
    /**
     * The logger.
     */
    private final static Logger logger = Logger.getLogger(AudioModeration.class);

    /**
     * The name of XMPP feature which states this Jigasi SIP Gateway can be
     * muted.
     */
    public static final String MUTED_FEATURE_NAME = "http://jitsi.org/protocol/audio-mute";

    /**
     * The mute IQ handler if enabled.
     */
    private MuteIqHandler muteIqHandler = null;

    /**
     * {@link SipGatewaySession} that is used in the <tt>JvbConference</tt> instance.
     */
    private final SipGatewaySession gatewaySession;

    /**
     * True if call should start muted, false otherwise.
     */
    private boolean startAudioMuted = false;

    /**
     * The <tt>JvbConference</tt> that handles current JVB conference.
     */
    private final JvbConference jvbConference;

    /**
     * The call context used to create this conference, contains info as
     * room name and room password and other optional parameters.
     */
    private final CallContext callContext;

    public AudioModeration(JvbConference jvbConference, SipGatewaySession gatewaySession, CallContext ctx)
    {
        this.gatewaySession = gatewaySession;
        this.jvbConference = jvbConference;
        this.callContext = ctx;
    }

    /**
     * Adds the features supported by jigasi for audio mute if enabled
     * @param meetTools the <tt>OperationSetJitsiMeetTools</tt> instance.
     * @return Returns the features extension element that can be added to presence.
     */
    static ExtensionElement addSupportedFeatures(OperationSetJitsiMeetTools meetTools)
    {
        if (isMutingSupported())
        {
            meetTools.addSupportedFeature(MUTED_FEATURE_NAME);
            return Util.createFeature(MUTED_FEATURE_NAME);
        }

        return null;
    }

    /**
     * Cleans listeners/handlers used by audio moderation.
     */
    public void clean()
    {
        XMPPConnection connection = jvbConference.getConnection();

        if (muteIqHandler != null)
        {
            // we need to remove it from the connection, or we break some Smack
            // weak references map where the key is connection and the value
            // holds a connection and we leak connection/conferences.
            if (connection != null)
            {
                connection.unregisterIQRequestHandler(muteIqHandler);
            }
        }
    }

    /**
     * Adds the iq handler that will handle muting us from the xmpp side.
     * We add it before inviting jicofo and before joining the room to not miss any message.
     */
    void notifyWillJoinJvbRoom()
    {
        if (isMutingSupported())
        {
            if (muteIqHandler == null)
            {
                muteIqHandler = new MuteIqHandler(this.gatewaySession);
            }

            jvbConference.getConnection().registerIQRequestHandler(muteIqHandler);
        }
    }

    /**
     * Changes the start audio muted flag.
     * @param value the new value.
     */
    public void setStartAudioMuted(boolean value)
    {
        this.startAudioMuted = value;
    }

    /**
     * Muting is supported when it is enabled by configuration.
     * @return <tt>true</tt> if mute support is enabled.
     */
    public static boolean isMutingSupported()
    {
        return JigasiBundleActivator.isSipStartMutedEnabled();
    }

    /**
     * Received JSON over SIP from callPeer.
     *
     * @param callPeer callPeer that sent the JSON.
     * @param jsonObject JSON that was sent.
     * @param params Implementation specific parameters.
     */
    public void onJSONReceived(CallPeer callPeer, JSONObject jsonObject, Map<String, Object> params)
    {
        try
        {
            if (jsonObject.containsKey("i"))
            {
                int msgId = ((Long)jsonObject.get("i")).intValue();
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
                    this.jvbConference.onPasswordReceived(
                        SipInfoJsonProtocol.getPasswordFromRoomAccessRequest(jsonObject));
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
                        logger.error(this.callContext + " muteResponse without status!");
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
                        // Send response through sip, respondRemoteAudioMute
                        this.gatewaySession.sendJson(callPeer,
                            SipInfoJsonProtocol.createSIPJSONAudioMuteResponse(bAudioMute, true, id));

                        // Send presence if response succeeded
                        this.jvbConference.setChatRoomAudioMuted(bAudioMute);
                    }
                    else
                    {
                        this.gatewaySession.sendJson(callPeer,
                            SipInfoJsonProtocol.createSIPJSONAudioMuteResponse(bAudioMute, false, id));
                    }
                }
            }
        }
        catch(Exception ex)
        {
            logger.error(this.callContext + " Error processing json ", ex);
        }
    }

    /**
     * Processes start muted in case:
     * - we had received that flag
     * - start muted is enabled through the flag
     * - jvb call is in progress as we will be muting the channels
     * - sip call is in progress we will be sending SIP Info messages
     */
    public void maybeProcessStartMuted()
    {
        Call jvbConferenceCall = this.gatewaySession.getJvbCall();
        Call sipCall = this.gatewaySession.getSipCall();

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
        CallPeer callPeer = this.gatewaySession.getSipCall().getCallPeers().next();

        try
        {
            logger.info(this.callContext + " Sending mute request ");
            this.gatewaySession.sendJson(callPeer, SipInfoJsonProtocol.createSIPJSONAudioMuteRequest(true));
        }
        catch (Exception ex)
        {
            logger.error(this.callContext + " Error sending mute request", ex);
        }
    }

    /**
     * Handles mute requests received by jicofo if enabled.
     */
    private class MuteIqHandler
        extends AbstractIqRequestHandler
    {
        /**
         * {@link AbstractGatewaySession} that is used in the <tt>JvbConference</tt> instance.
         */
        private final AbstractGatewaySession gatewaySession;

        public MuteIqHandler(AbstractGatewaySession gatewaySession)
        {
            super(
                MuteIq.ELEMENT_NAME,
                MuteIq.NAMESPACE,
                IQ.Type.set,
                Mode.sync);

            this.gatewaySession = gatewaySession;
        }

        @Override
        public IQ handleIQRequest(IQ iqRequest)
        {
            return handleMuteIq((MuteIq) iqRequest);
        }

        /**
         * Handles the incoming mute request only if it is from the focus.
         * @param muteIq the incoming iq.
         * @return the result iq.
         */
        private IQ handleMuteIq(MuteIq muteIq)
        {
            Boolean doMute = muteIq.getMute();
            Jid from = muteIq.getFrom();

            if (doMute == null || !from.getResourceOrEmpty().equals(this.gatewaySession.getFocusResourceAddr()))
            {
                return IQ.createErrorResponse(muteIq, XMPPError.getBuilder(XMPPError.Condition.item_not_found));
            }

            if (doMute)
            {
                mute();
            }

            return IQ.createResultIQ(muteIq);
        }
    }
}
