/*
 * Jigasi, the JItsi GAteway to SIP.
 *
 * Copyright @ 2017 Atlassian Pty Ltd
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
import net.java.sip.communicator.service.protocol.media.*;
import org.jitsi.xmpp.extensions.jitsimeet.*;
import org.jitsi.jigasi.transcription.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.service.neomedia.device.*;
import org.jitsi.utils.logging.*;
import org.jitsi.utils.*;
import org.jivesoftware.smack.packet.Presence;
import org.json.simple.*;
import org.jxmpp.jid.*;
import org.jxmpp.jid.impl.*;
import org.jxmpp.stringprep.*;

import java.util.*;

/**
 * A TranscriptionGatewaySession is able to join a JVB conference and
 * manage the transcription of said conference
 *
 * @author Nik Vaessen
 */
public class TranscriptionGatewaySession
    extends AbstractGatewaySession
    implements TranscriptionListener,
               TranscriptionEventListener,
               TranslationResultListener
{
    /**
     * The logger of this class
     */
    private final static Logger logger
            = Logger.getLogger(TranscriptionGatewaySession.class);

    /**
     * The display name which should be displayed when Jigasi joins the
     * room
     */
    public final static String DISPLAY_NAME = "Transcriber";

    /**
     * How long the transcriber should wait until really leaving the conference
     * when no participant is requesting transcription anymore.
     */
    public final static int PRESENCE_UPDATE_WAIT_UNTIL_LEAVE_DURATION = 2500;

    /**
     * The TranscriptionService used by this session
     */
    private AbstractTranscriptionService service;

    /**
     * The TranscriptHandler which enables publishing a {@link Transcript} and
     * sending {@link TranscriptionResult} to a {@link ChatRoom}
     */
    private TranscriptHandler handler;

    /**
     * The ChatRoom of the conference which is going to be transcribed.
     * We will post messages to the ChatRoom to update users of progress
     * of transcription
     */
    private ChatRoom chatRoom = null;

    /**
     * The transcriber managing transcriptions of audio
     */
    private Transcriber transcriber;

    /**
     * The call to the jvb room jigasi joins. This is used to get
     * the names and ssrc's of the participants
     */
    private Call jvbCall = null;

    /**
     * A list of {@link TranscriptPublisher.Promise}s which will be used
     * to handle the {@link Transcript} when the session is stopped
     */
    private List<TranscriptPublisher.Promise> finalTranscriptPromises
        = new LinkedList<>();

    /**
     * Create a TranscriptionGatewaySession which can handle the transcription
     * of a JVB conference
     *
     * @param gateway the gateway which controls this session
     * @param context the context of the call this session is joining
     * @param service the TranscriptionService which should be used
     *                to transcribe the audio in the conference
     * @param handler the TranscriptHandler which can handle the
     * {@link Transcript} and incoming {@link TranscriptionResult}
     */
    public TranscriptionGatewaySession(AbstractGateway gateway,
                                       CallContext context,
                                       AbstractTranscriptionService service,
                                       TranscriptHandler handler)
    {
        super(gateway, context);
        this.service = service;
        this.handler = handler;

        this.transcriber = new Transcriber(this.service);

        if (this.service instanceof TranscriptionEventListener)
        {
            this.transcriber.addTranscriptionEventListener(
                (TranscriptionEventListener)this.service);
        }

        transcriber.setRoomName(this.getCallContext().getRoomJid().toString());
    }

    @Override
    void onConferenceCallInvited(Call incomingCall)
    {
        // We got invited to a room, we can tell the transcriber
        // the room name, url and start listening
        transcriber.addTranscriptionListener(this);
        transcriber.addTranslationListener(this);
        transcriber.setRoomUrl(getMeetingUrl());

        // We create a MediaWareCallConference whose MediaDevice
        // will get the get all of the audio and video packets
        incomingCall.setConference(new MediaAwareCallConference()
        {
            @Override
            public MediaDevice getDefaultDevice(MediaType mediaType,
                MediaUseCase useCase)
            {
                if (MediaType.AUDIO.equals(mediaType))
                {
                    logger.info("Transcriber: Media Device Audio");
                    return transcriber.getMediaDevice();
                }
                logger.info("Transcriber: Media Device Video");
                // FIXME: 18/07/17 what to do with video?
                // will cause an exception when mediaType == VIDEO
                return super.getDefaultDevice(mediaType, useCase);
            }
        });

        Exception error = this.onConferenceCallStarted(incomingCall);

        if (error != null)
        {
            logger.error(error, error);
        }
    }

    @Override
    Exception onConferenceCallStarted(Call jvbConferenceCall)
    {
        // We can now safely set the Call connecting to the muc room
        // and the ChatRoom of the muc room
        this.jvbCall = jvbConferenceCall;
        this.chatRoom = super.jvbConference.getJvbRoom();

        // If the transcription service is not correctly configured, there is no
        // point in continuing this session, so end it immediately
        if (!service.isConfiguredProperly())
        {
            logger.warn("TranscriptionService is not properly configured");
            sendMessageToRoom("Transcriber is not properly " +
                "configured. Contact the service administrators and let them " +
                "know! I will now leave.");
            jvbConference.stop();
            return null;
        }

        // adds all TranscriptionEventListener among TranscriptResultPublishers
        for (TranscriptionResultPublisher pub
            : handler.getTranscriptResultPublishers())
        {
            if (pub instanceof TranscriptionEventListener)
                transcriber.addTranscriptionEventListener(
                    (TranscriptionEventListener)pub);
        }

        transcriber.addTranscriptionEventListener(this);

        // FIXME: 20/07/17 Do we want to start transcribing on joining room?
        transcriber.start();

        // for every member already in the room, now is the time to add them
        // to the transcriber
        addInitialMembers();

        StringBuilder welcomeMessage = new StringBuilder();

        finalTranscriptPromises.addAll(handler.getTranscriptPublishPromises());
        for (TranscriptPublisher.Promise promise : finalTranscriptPromises)
        {
            if (promise.hasDescription())
            {
                welcomeMessage.append(promise.getDescription());
            }

            promise.maybeStartRecording(transcriber.getMediaDevice());
        }

        if (welcomeMessage.length() > 0)
        {
            sendMessageToRoom(welcomeMessage.toString());
        }

        try
        {
            CallManager.acceptCall(jvbConferenceCall);
        }
        catch(OperationFailedException e)
        {
            return e;
        }

        logger.debug("TranscriptionGatewaySession started transcribing");

        return null;
    }

    @Override
    void onJvbConferenceStopped(JvbConference jvbConference,
                                int reasonCode, String reason)
    {
        // FIXME: 22/07/17 this actually happens only when every user leaves
        // instead of when transcription is over.
        // Need a solution for stopping the transcription earlier

        // The conference is over, make sure the transcriber stops
        if (!transcriber.finished())
        {
            transcriber.stop(null);

            for (TranscriptPublisher.Promise promise : finalTranscriptPromises)
            {
                promise.publish(transcriber.getTranscript());
            }
        }

        this.gateway.notifyCallEnded(this.callContext);

        logger.debug("Conference ended");
    }

    @Override
    void onJvbConferenceWillStop(JvbConference jvbConference, int reasonCode,
        String reason)
    {
        if (!transcriber.finished())
        {
            transcriber.willStop();
        }
    }

    @Override
    void notifyChatRoomMemberJoined(ChatRoomMember chatMember)
    {
        super.notifyChatRoomMemberJoined(chatMember);

        String identifier = getParticipantIdentifier(chatMember);

        // The focus user should not connect to the transcription service
        if ("focus".equals(identifier))
        {
            return;
        }
        this.transcriber.updateParticipant(identifier, chatMember);
        this.transcriber.participantJoined(identifier);
    }

    @Override
    void notifyChatRoomMemberLeft(ChatRoomMember chatMember)
    {
        super.notifyChatRoomMemberLeft(chatMember);

        String identifier = getParticipantIdentifier(chatMember);
        this.transcriber.participantLeft(identifier);
    }

    @Override
    void notifyChatRoomMemberUpdated(ChatRoomMember chatMember, Presence presence)
    {
        super.notifyChatRoomMemberUpdated(chatMember, presence);

        //This needed for the translation language change.
        //update a language change coming in the presence
        String identifier = getParticipantIdentifier(chatMember);
        this.transcriber.updateParticipant(identifier, chatMember);

        if (transcriber.isTranscribing() &&
            !transcriber.isAnyParticipantRequestingTranscription())
        {
            new Thread(() ->
            {
                try
                {
                    Thread.sleep(PRESENCE_UPDATE_WAIT_UNTIL_LEAVE_DURATION);
                }
                catch (InterruptedException e)
                {
                    logger.error(e);
                }

                if (!transcriber.isAnyParticipantRequestingTranscription())
                {
                    jvbConference.stop();
                }
            }).start();
        }
    }

    @Override
    void notifyConferenceMemberJoined(ConferenceMember conferenceMember)
    {
        super.notifyConferenceMemberJoined(conferenceMember);

        String identifier = getParticipantIdentifier(conferenceMember);
        this.transcriber.updateParticipant(identifier, conferenceMember);
    }

    @Override
    void notifyConferenceMemberLeft(ConferenceMember conferenceMember)
    {
        super.notifyConferenceMemberLeft(conferenceMember);

        // we don't care about this event
    }

    @Override
    public boolean isTranslatorSupported()
    {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDefaultInitStatus()
    {
        return null;
    }

    /**
     * This method will be called by the {@link Transcriber} every time a new
     * result comes in
     *
     * @param result the result which has come in
     */
    @Override
    public void notify(TranscriptionResult result)
    {
        sendTranscriptionResultToRoom(result);
    }

    /**
     * This method will be called by the {@link TranslationManager} every time a
     * final transcription result is translated.
     *
     * @param result the translation result which has come in
     */
    @Override
    public void notify(TranslationResult result)
    {
        sendTranslationResultToRoom(result);
    }

    /**
     * This method will be called by the {@link Transcriber} when it is done
     * and thus no more results will come in
     */
    @Override
    public void completed()
    {
        // FIXME: 19/07/17 Insert link!
        // FIXME: 23/07/17 This will actually never be seen as there is no way
        // to stop transcription before jigasi leaves conference
//        sendMessageToRoom("The complete transcription can be " +
//                "found at <insert_link_here>");
    }

    @Override
    public void failed(FailureReason reason)
    {
        // Leave the conference room
        this.jvbConference.stop();
    }

    @Override
    public void onJoinJitsiMeetRequest(Call call, String s,
                                       Map<String, String> map)
    {
        throw new UnsupportedOperationException("Incoming calls are " +
                "not supported by TranscriptionGatewaySession");
    }

    @Override
    public void onSessionStartMuted(boolean[] startMutedFlags)
    {
        // Not used.
    }

    @Override
    public void onJSONReceived(CallPeer callPeer,
                               JSONObject jsonObject,
                               Map<String, Object> params)
    {
        // Not used.
    }

    @Override
    public void toneReceived(DTMFReceivedEvent dtmfReceivedEvent)
    {
        throw new UnsupportedOperationException("TranscriptionGatewaySession " +
            "does " + "not support receiving DTMF tones");
    }

    /**
     * Add every participant already in the room to the transcriber
     * and give the initial people in the room to the transcriber to create
     * the header of the transcript(s)
     */
    private void addInitialMembers()
    {
        List<ConferenceMember> confMembers = getCurrentConferenceMembers();
        if (confMembers == null)
        {
            logger.warn("Cannot add initial ConferenceMembers to " +
                "transcription");
        }
        else
        {
            for (ConferenceMember confMember : confMembers)
            {
                // We should not have the bridge as a participant
                if ("jvb".equals(confMember.getAddress()))
                {
                    continue;
                }

                String identifier = getParticipantIdentifier(confMember);

                this.transcriber.updateParticipant(identifier, confMember);
            }
        }

        List<ChatRoomMember> chatRoomMembers = getCurrentChatRoomMembers();
        if (chatRoomMembers == null)
        {
            logger.warn("Cannot add initial ChatRoomMembers to transcription");
            return;
        }

        for (ChatRoomMember chatRoomMember : chatRoomMembers)
        {
            ChatRoomMemberJabberImpl chatRoomMemberJabber;

            if (chatRoomMember instanceof ChatRoomMemberJabberImpl)
            {
                chatRoomMemberJabber
                    = (ChatRoomMemberJabberImpl) chatRoomMember;
            }
            else
            {
                logger.warn("Could not cast a ChatRoomMember to " +
                    "ChatRoomMemberJabberImpl");
                continue;
            }

            String identifier = getParticipantIdentifier(chatRoomMemberJabber);

            // We should not have the focus to the list of transcribed
            // participants
            if ("focus".equals(identifier))
            {
                continue;
            }

            // If the address does not have a resource part, we can never
            // match it to a ConferenceMember and thus we should never
            // add it to the list of transcribed participants
            if (chatRoomMemberJabber.getJabberID().getResourceOrNull() == null)
            {
                continue;
            }

            this.transcriber.updateParticipant(identifier, chatRoomMember);
            this.transcriber.participantJoined(identifier);
        }
    }

    /**
     * Get a list of all the ConferenceMembers currently in the conference
     *
     * @return the list of ConferenceMembers or null if not currently connected
     */
    private List<ConferenceMember> getCurrentConferenceMembers()
    {
        if (jvbCall == null)
        {
            return null;
        }
        Iterator<? extends CallPeer> iter = jvbCall.getCallPeers();
        return
            iter.hasNext() ? iter.next().getConferenceMembers() : null;
    }

    /**
     * Get a list of all the ChatRoomMembers currently in the ChatRoom
     *
     * @return the list of ChatRoomMembers or null if not currently connected
     */
    private List<ChatRoomMember> getCurrentChatRoomMembers()
    {
        return chatRoom == null ? null : chatRoom.getMembers();
    }

    /**
     * Get the unique identifier for a ConferenceMember, which can be used
     * to see if it corresponds to the name of a ChatRoomMember
     *
     * @param member the ConferenceMember whose ID to retrieve
     * @return the ID of the conference member or null if address cannot be
     * parsed
     */
    private String getConferenceMemberResourceID(ConferenceMember member)
    {
        // assume address is in the form
        // <room_name>@conference.<jitsi_meet_domain>/<some_unique_id>
        // or just <some_unique_id>
        try
        {
            Jid jid = JidCreate.from(member.getAddress());

            if (jid.hasResource())
            {
                return jid.getResourceOrThrow().toString();
            }
            else
            {
                return jid.toString();
            }
        }
        catch (XmppStringprepException e)
        {
            logger.error(e);
        }

        return null;
    }

    /**
     * Get the common identifier between a {@link ConferenceMember} and
     * {@link ChatRoomMember} of the given {@link ChatRoomMember}
     *
     * @param chatRoomMember the ChatRoomMember to get the common identifier
     * from
     * @return the common identifier or null when ChatRoomMember is null
     */
    private String getParticipantIdentifier(ChatRoomMember chatRoomMember)
    {
        if (chatRoomMember == null)
        {
            return null;
        }

        return chatRoomMember.getName();
    }

    /**
     * Get the common identifier between a {@link ConferenceMember} and
     * {@link ChatRoomMember} of the given {@link ConferenceMember}
     *
     * @param conferenceMember the ConferenceMember to get the common identifier
     * from
     * @return the common identifier or null when ConferenceMember is null
     */
    private String getParticipantIdentifier(ConferenceMember conferenceMember)
    {
        if (conferenceMember == null)
        {
            return null;
        }

        return getConferenceMemberResourceID(conferenceMember);
    }


    /**
     * Send a message to the muc room
     *
     * @param messageString the message to send
     */
    private void sendMessageToRoom(String messageString)
    {
        if (chatRoom == null)
        {
            logger.error("Cannot send message as chatRoom is null");
            return;
        }

        Message message = chatRoom.createMessage(messageString);
        try
        {
            chatRoom.sendMessage(message);
            logger.debug("Sending message: \"" + messageString + "\"");
        }
        catch (OperationFailedException e)
        {
            logger.warn("Failed to send message " + messageString, e);
        }
    }

    /**
     * Send a {@link TranscriptionResult} to the {@link ChatRoom}
     *
     * @param result the {@link TranscriptionResult} to send
     */
    private void sendTranscriptionResultToRoom(TranscriptionResult result)
    {
        handler.publishTranscriptionResult(this.chatRoom, result);
    }

    /**
     * Send a {@link TranslationResult} to the {@link ChatRoom}
     *
     * @param result the {@link TranscriptionResult} to send
     */
    private void sendTranslationResultToRoom(TranslationResult result)
    {
        handler.publishTranslationResult(this.chatRoom, result);
    }

    @Override
    public String getMucDisplayName()
    {
        return TranscriptionGatewaySession.DISPLAY_NAME;
    }

    @Override
    public void notify(Transcriber transcriber, TranscriptEvent event)
    {
        if (event.getEvent() == Transcript.TranscriptEventType.START
                || event.getEvent() == Transcript.TranscriptEventType.WILL_END)
        {
            // in will_end we will be still transcribing but we need
            // to explicitly send off
            TranscriptionStatusExtension.Status status
                = event.getEvent() ==
                    Transcript.TranscriptEventType.WILL_END ?
                        TranscriptionStatusExtension.Status.OFF
                        : transcriber.isTranscribing() ?
                            TranscriptionStatusExtension.Status.ON
                            : TranscriptionStatusExtension.Status.OFF;

            TranscriptionStatusExtension extension
                = new TranscriptionStatusExtension();
            extension.setStatus(status);

            jvbConference.sendPresenceExtension(extension);
        }
    }

    /**
     * {@inheritDoc}
     */
    public boolean hasCallResumeSupport()
    {
        return false;
    }
}
