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
import org.jitsi.jigasi.sip.*;
import org.jitsi.jigasi.util.*;
import org.jitsi.utils.logging.*;
import org.jitsi.xmpp.extensions.*;
import org.jitsi.xmpp.extensions.jitsimeet.*;
import org.jivesoftware.smack.*;
import org.jivesoftware.smack.filter.*;
import org.jivesoftware.smack.iqrequest.*;
import org.jivesoftware.smack.packet.*;
import org.jivesoftware.smackx.disco.*;
import org.jivesoftware.smackx.disco.packet.*;
import org.json.simple.parser.*;
import org.jxmpp.jid.*;

import org.json.simple.*;
import org.jxmpp.jid.impl.*;

import java.util.*;

/**
 * Handles all the mute/unmute/startMuted logic.
 */
public class AudioModeration
    implements ChatRoomLocalUserRoleListener
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
     * We always start the call unmuted, we keep the instance, so we can remove it from the list of extensions that
     * we always add after joining and when sending a presence.
     */
    private static final AudioMutedExtension initialAudioMutedExtension = new AudioMutedExtension();
    static
    {
        initialAudioMutedExtension.setAudioMuted(false);
    }
    /**
     * The call context used to create this conference, contains info as
     * room name and room password and other optional parameters.
     */
    private final CallContext callContext;

    /**
     * We keep track of the AV moderation component to be able to trust the incoming messages.
     */
    private String avModerationAddress = null;

    /**
     * Whether AV moderation is currently enabled.
     */
    private boolean avModerationEnabled = false;

    /**
     * Whether current jigasi provider is allowed to unmute itself. By default, AV moderation is not enabled,
     * and we are allowed to unmute.
     */
    private boolean isAllowedToUnmute = true;

    /**
     * We use the same instance of extension so we can remove and add it from the default presence set of
     * extensions.
     */
    private static final RaiseHandExtension lowerHandExtension = new RaiseHandExtension();

    /**
     * The listener for incoming messages with AV moderation commands.
     */
    private final AVModerationListener avModerationListener;

    public AudioModeration(JvbConference jvbConference, SipGatewaySession gatewaySession, CallContext ctx)
    {
        this.gatewaySession = gatewaySession;
        this.jvbConference = jvbConference;
        this.callContext = ctx;
        this.avModerationListener = new AVModerationListener();
    }

    /**
     * Adds the features supported by jigasi for audio mute if enabled
     * @param meetTools the <tt>OperationSetJitsiMeetTools</tt> instance.
     * @return Returns the features extension element that can be added to presence.
     */
    static ExtensionElement addSupportedFeatures(OperationSetJitsiMeetToolsJabber meetTools)
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

        if (jvbConference.getJvbRoom() != null)
        {
            jvbConference.getJvbRoom().removelocalUserRoleListener(this);
        }

        if (connection == null)
        {
            // if there is no connection nothing to clear
            return;
        }

        if (muteIqHandler != null)
        {
            // we need to remove it from the connection, or we break some Smack
            // weak references map where the key is connection and the value
            // holds a connection and we leak connection/conferences.
            connection.unregisterIQRequestHandler(muteIqHandler);
        }
    }

    /**
     * Adds the iq handler that will handle muting us from the xmpp side.
     * We add it before inviting jicofo and before joining the room to not miss any message.
     */
    void notifyWillJoinJvbRoom(ChatRoom mucRoom)
    {
        if (mucRoom instanceof ChatRoomJabberImpl)
        {
            // we always start the call unmuted
            ((ChatRoomJabberImpl) mucRoom).addPresencePacketExtensions(initialAudioMutedExtension);
        }

        mucRoom.addLocalUserRoleListener(this);

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
                        this.setChatRoomAudioMuted(bMute);
                    }
                }
                else if (type.equalsIgnoreCase("muteRequest"))
                {
                    JSONObject data = (JSONObject) jsonObject.get("data");

                    boolean bAudioMute = (boolean)data.get("audio");

                    // Send request to jicofo
                    if (this.requestAudioMuteByJicofo(bAudioMute))
                    {
                        // Send response through sip, respondRemoteAudioMute
                        this.gatewaySession.sendJson(
                            SipInfoJsonProtocol.createSIPJSONAudioMuteResponse(bAudioMute, true, id));

                        // Send presence if response succeeded
                        this.setChatRoomAudioMuted(bAudioMute);
                    }
                    else
                    {
                        this.gatewaySession.sendJson(
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
     * Sets the chatroom presence for the participant.
     *
     * @param muted <tt>true</tt> for presence as muted,
     * false otherwise
     */
    void setChatRoomAudioMuted(boolean muted)
    {
        ChatRoom mucRoom = this.jvbConference.getJvbRoom();

        if (mucRoom != null)
        {
            if (mucRoom instanceof ChatRoomJabberImpl)
            {
                // remove the initial extension otherwise it will overwrite our new setting
                ((ChatRoomJabberImpl) mucRoom).removePresencePacketExtensions(initialAudioMutedExtension);

                if (!muted)
                {
                    // if we are unmuting make sure our raise hand is always lowered
                    ((ChatRoomJabberImpl) mucRoom).addPresencePacketExtensions(lowerHandExtension);
                }
            }

            AudioMutedExtension audioMutedExtension = new AudioMutedExtension();

            audioMutedExtension.setAudioMuted(muted);

            OperationSetJitsiMeetToolsJabber jitsiMeetTools
                = this.jvbConference.getXmppProvider()
                .getOperationSet(OperationSetJitsiMeetToolsJabber.class);

            jitsiMeetTools.sendPresenceExtension(mucRoom, audioMutedExtension);
        }
    }

    /**
     * Request Jicofo on behalf to mute/unmute us.
     *
     * @param bMuted <tt>true</tt> if request is to mute audio,
     * false otherwise
     * @return <tt>true</tt> if request succeeded, false
     * otherwise
     */
    public boolean requestAudioMuteByJicofo(boolean bMuted)
    {
        ChatRoom mucRoom = this.jvbConference.getJvbRoom();

        if (!bMuted && this.avModerationEnabled && !isAllowedToUnmute)
        {
            OperationSetJitsiMeetToolsJabber jitsiMeetTools
                = this.jvbConference.getXmppProvider()
                .getOperationSet(OperationSetJitsiMeetToolsJabber.class);

            if (mucRoom instanceof ChatRoomJabberImpl)
            {
                // remove the default value which is lowering the hand
                ((ChatRoomJabberImpl) mucRoom).removePresencePacketExtensions(lowerHandExtension);
            }

            // let's raise hand
            jitsiMeetTools.sendPresenceExtension(mucRoom, new RaiseHandExtension().setRaisedHandValue(true));

            return false;
        }

        StanzaCollector collector = null;
        try
        {
            String roomName = mucRoom.getIdentifier();

            String jidString = roomName  + "/" + this.jvbConference.getResourceIdentifier().toString();
            Jid memberJid = JidCreate.from(jidString);
            String roomJidString = roomName + "/" + this.gatewaySession.getFocusResourceAddr();
            Jid roomJid = JidCreate.from(roomJidString);

            MuteIq muteIq = new MuteIq();
            muteIq.setJid(memberJid);
            muteIq.setMute(bMuted);
            muteIq.setType(IQ.Type.set);
            muteIq.setTo(roomJid);

            collector = this.jvbConference.getConnection().createStanzaCollectorAndSend(muteIq);

            collector.nextResultOrThrow();
        }
        catch(Exception ex)
        {
            logger.error(this.callContext + " Error sending xmpp request for audio mute", ex);

            return false;
        }
        finally
        {
            if (collector != null)
            {
                collector.cancel();
            }
        }

        return true;
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
            if (!this.avModerationEnabled)
            {
                // in case of startAudioMuted, we want jicofo to stop the bridge from sending our audio
                // a specific case for jigasi as it doesn't do local muting
                // in case of av-moderation jicofo has done that already for us
                this.requestAudioMuteByJicofo(true);
            }

            // inform the sip side that our state is muted (av moderation or not)
            mute();

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

        try
        {
            logger.info(this.callContext + " Sending mute request avModeration:" + this.avModerationEnabled
                + " allowed to unmute:" + this.isAllowedToUnmute);

            this.gatewaySession.sendJson(SipInfoJsonProtocol.createSIPJSONAudioMuteRequest(true));
        }
        catch (Exception ex)
        {
            logger.error(this.callContext + " Error sending mute request", ex);
        }
    }

    /**
     * The xmpp provider for JvbConference has registered after connecting.
     */
    void setAvModerationAddress(String address)
    {
        this.avModerationAddress = address;

        if (this.avModerationAddress != null)
        {
            try
            {
                this.jvbConference.getConnection().addAsyncStanzaListener(
                    this.avModerationListener,
                    new AndFilter(
                        MessageTypeFilter.NORMAL,
                        FromMatchesFilter.create(JidCreate.domainBareFrom(this.avModerationAddress))));
            }
            catch(Exception e)
            {
                logger.error("Error adding AV moderation listener", e);
            }
        }
    }

    /**
     * We want to remove avModerationListener only when we are cleaning resources, the JvbConference is stopping.
     * It is added the moment the provider registers and should not be removed
     * while moving between lobby and conference.
     */
    public void cleanXmppProvider()
    {
        XMPPConnection connection = jvbConference.getConnection();

        if (connection == null)
        {
            // if there is no connection nothing to clear
            return;
        }

        connection.removeAsyncStanzaListener(this.avModerationListener);
    }

    @Override
    public void localUserRoleChanged(ChatRoomLocalUserRoleChangeEvent evt)
    {
        // handling role change only in case of av moderation enabled
        if (!avModerationEnabled || evt.getNewRole().equals(evt.getPreviousRole()))
        {
            return;
        }

        boolean signalAvModerationEnabled = true;
        if (evt.getNewRole().equals(ChatRoomMemberRole.OWNER) || evt.getNewRole().equals(ChatRoomMemberRole.MODERATOR))
        {
            isAllowedToUnmute = true;

            signalAvModerationEnabled = false;
        }
        else
        {
            isAllowedToUnmute = false;
        }

        // in case av moderation was on and we were granted moderator - signal to IVR that
        // we can unmute without raising a hand, but if we were moderator and our permissions were revoked
        // do the opposite
        try
        {
            gatewaySession.sendJson(
                SipInfoJsonProtocol.createAVModerationEnabledNotification(signalAvModerationEnabled));
        }
        catch(OperationFailedException e)
        {
            logger.info(callContext + " Error sending av moderation enable/disable notification", e);
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
                MuteIq.ELEMENT,
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

            if (doMute == null || !from.getResourceOrEmpty().toString()
                    .equals(this.gatewaySession.getFocusResourceAddr()))
            {
                return IQ.createErrorResponse(muteIq,
                    StanzaError.getBuilder(StanzaError.Condition.item_not_found)
                        .build());
            }

            if (doMute)
            {
                mute();
            }

            return IQ.createResultIQ(muteIq);
        }
    }

    /**
     * Added to presence to raise hand.
     */
    private static class RaiseHandExtension
        extends AbstractPacketExtension
    {
        /**
         * The namespace of this packet extension.
         */
        public static final String NAMESPACE = "jabber:client";

        /**
         * XML element name of this packet extension.
         */
        public static final String ELEMENT_NAME = "jitsi_participant_raisedHand";

        /**
         * Creates a {@link org.jitsi.xmpp.extensions.jitsimeet.TranslationLanguageExtension} instance.
         */
        public RaiseHandExtension()
        {
            super(NAMESPACE, ELEMENT_NAME);
        }

        /**
         * Sets user's audio muted status.
         *
         * @param value <tt>true</tt> or <tt>false</tt> which indicates audio
         *                   muted status of the user.
         */
        public ExtensionElement setRaisedHandValue(Boolean value)
        {
            setText(value ? value.toString() : null);

            return this;
        }
    }

    /**
     * Listens for incoming messages with AV moderation commands.
     */
    private class AVModerationListener
        implements StanzaListener
    {
        @Override
        public void processStanza(Stanza packet)
        {
            JsonMessageExtension jsonMsg =
                packet.getExtension(JsonMessageExtension.class);

            if (jsonMsg == null)
            {
                return;
            }

            try
            {
                Object o = new JSONParser().parse(jsonMsg.getJson());

                if (o instanceof JSONObject)
                {
                    JSONObject data = (JSONObject) o;

                    if (data.get("type").equals("av_moderation"))
                    {
                        Object enabledObj = data.get("enabled");
                        Object approvedObj = data.get("approved");
                        Object removedObj = data.get("removed");
                        Object mediaTypeObj = data.get("mediaType");

                        // we are interested only in audio moderation
                        if (mediaTypeObj == null || !mediaTypeObj.equals("audio"))
                        {
                            return;
                        }

                        if (enabledObj != null)
                        {
                            avModerationEnabled = (Boolean) enabledObj;
                            logger.info(callContext + " AV moderation has been enabled:" + avModerationEnabled);

                            // if jigasi is moderator there are no restrictions of muting and unmuting
                            // so we skip sending the notifications to the sip side to avoid activating the
                            // av moderation logic
                            ChatRoomMemberRole role = jvbConference.getJvbRoom().getUserRole();
                            if (!role.equals(ChatRoomMemberRole.OWNER) && !role.equals(ChatRoomMemberRole.MODERATOR))
                            {
                                // we will receive separate message when we are allowed to unmute
                                isAllowedToUnmute = false;

                                gatewaySession.sendJson(
                                    SipInfoJsonProtocol.createAVModerationEnabledNotification(avModerationEnabled));
                            }
                        }
                        else if (removedObj != null && (Boolean) removedObj)
                        {
                            isAllowedToUnmute = false;
                            gatewaySession.sendJson(SipInfoJsonProtocol.createAVModerationDeniedNotification());
                        }
                        else if (approvedObj != null && (Boolean) approvedObj)
                        {
                            isAllowedToUnmute = true;
                            gatewaySession.sendJson(SipInfoJsonProtocol.createAVModerationApprovedNotification());
                        }
                    }
                }
            }
            catch(Exception e)
            {
                logger.error(callContext + " Error parsing", e);
            }
        }
    }
}
