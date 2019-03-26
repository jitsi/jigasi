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
import net.java.sip.communicator.impl.protocol.jabber.extensions.jitsimeet.*;
import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.protocol.event.*;
import net.java.sip.communicator.service.protocol.media.*;
import org.jitsi.jigasi.transcription.*;
import org.jitsi.jigasi.transcription.audio.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.service.neomedia.device.*;
import org.jitsi.util.*;
import org.jivesoftware.smack.packet.Presence;
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

    public final static String P_NAME_AUDIO_FORMAT
        = "org.jitsi.jigasi.transcription.AUDIO_FORMAT";

    public final static String P_NAME_AUDIO_FORMAT_DEFAULT_VALUE = "linear16";

    public final static String P_NAME_AUDIO_FORMAT_OPUS_VALUE = "opus";

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
    private TranscriptionService service;

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
     *
     */
    private AbstractForwarder audioForwarder = null;

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
                                       TranscriptionService service,
                                       TranscriptHandler handler)
    {
        super(gateway, context);
        this.service = service;
        this.handler = handler;

        this.transcriber = new Transcriber(this.service);
    }

    @Override
    void onConferenceCallInvited(Call incomingCall)
    {
        // We got invited to a room, we can tell the transcriber
        // the room name, url and start listening
        transcriber.addTranscriptionListener(this);
        transcriber.addTranslationListener(this);
        transcriber.setRoomName(getJvbRoomName());
        transcriber.setRoomUrl(getMeetingUrl());

        if(requireOpusAudio())
        {
            logger.debug("adding OpusAudioPacketForwarder to MediaStream to " +
                             "receive opus audio");

            OpusAudioPacketForwarder forwarder = new OpusAudioPacketForwarder();
            audioForwarder = forwarder;
            CallPeer peer = incomingCall.getCallPeers().next();

            if(!addOpusAudioForwarder(peer, forwarder))
            {
                logger.debug("first attempt at adding " +
                                 "OpusAudioPacketForwarder failed");

                // if we failed to add the forwarded, the connection was
                // not ready yet and we need to try again as soon as
                // the connection is ready
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
                            if(!addOpusAudioForwarder(peer, forwarder))
                            {
                                logger.error("failed to add " +
                                                 "OpusAudioPacketForwarder" +
                                                 " to MediaStream");
                            }
                            else
                            {
                                logger.debug("second attempt at adding " +
                                                 "OpusAudioPacketForwarder " +
                                                 "was successful");
                            }
                        }
                    }
                });
            }
        }
        else
        {
            Linear16AudioPacketForwarder forwarder
                = new Linear16AudioPacketForwarder();
            audioForwarder = forwarder;

            logger.debug("creating a custom MediaDevice to receive linear16 " +
                            " audio");

            // We create a MediaWareCallConference whose MediaDevice
            // will get the get all of the audio and video packets
            incomingCall.setConference(new MediaAwareCallConference()
            {
                @Override
                public MediaDevice getDefaultDevice(MediaType mediaType,
                                                    MediaUseCase useCase)
                {
                    if(MediaType.AUDIO.equals(mediaType))
                    {
                        logger.info("Transcriber: Media Device Audio");
                        return forwarder.getMediaDevice();
                    }
                    logger.info("Transcriber: Media Device Video");
                    // FIXME: 18/07/17 what to do with video?
                    // will cause an exception when mediaType == VIDEO
                    return super.getDefaultDevice(mediaType, useCase);
                }
            });

        }

        audioForwarder.addListener(transcriber);

        logger.debug("Invited for conference");
    }

    private boolean addOpusAudioForwarder(CallPeer peer,
                                          OpusAudioPacketForwarder forwarder)
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
                    stream.setExternalTransformer(forwarder.getAdapter());

                    return true;
                }
            }
        }

        return false;
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
        if(!service.isConfiguredProperly())
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
        for(TranscriptPublisher.Promise promise : finalTranscriptPromises)
        {
            if (promise.hasDescription())
            {
                welcomeMessage.append(promise.getDescription());
            }

            if (audioForwarder instanceof Linear16AudioPacketForwarder)
            {
                promise.maybeStartRecording(
                    ((Linear16AudioPacketForwarder) audioForwarder)
                        .getMediaDevice());
            }
        }

        if(welcomeMessage.length() > 0)
        {
            sendMessageToRoom(welcomeMessage.toString());
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
        if(!transcriber.finished())
        {
            transcriber.stop();

            for(TranscriptPublisher.Promise promise : finalTranscriptPromises)
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
        if(!transcriber.finished())
        {
            transcriber.willStop();
        }
    }

    @Override
    void notifyChatRoomMemberJoined(ChatRoomMember chatMember)
    {
        super.notifyChatRoomMemberJoined(chatMember);

        String identifier = getParticipantIdentifier(chatMember);

        System.out.println("member with " + identifier + " joined");

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

        String identifier = getParticipantIdentifier(chatMember);
        TranscriptionLanguageExtension transcriptionLanguageExtension
            = presence.getExtension(
                TranscriptionLanguageExtension.ELEMENT_NAME,
                TranscriptionLanguageExtension.NAMESPACE);
        TranslationLanguageExtension translationLanguageExtension
            = presence.getExtension(
                TranslationLanguageExtension.ELEMENT_NAME,
                TranslationLanguageExtension.NAMESPACE);

        if(transcriptionLanguageExtension != null)
        {
            String language
                = transcriptionLanguageExtension.getTranscriptionLanguage();

            this.transcriber.updateParticipantSourceLanguage(identifier,
                language);
        }

        if(translationLanguageExtension != null)
        {
            String language
                = translationLanguageExtension.getTranslationLanguage();

            this.transcriber.updateParticipantTargetLanguage(identifier, language);
        }
        else
        {
            this.transcriber.updateParticipantTargetLanguage(identifier, null);
        }

        if(transcriber.isTranscribing() &&
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
                    e.printStackTrace();
                }

                if(!transcriber.isAnyParticipantRequestingTranscription())
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
        // we use translator mode when we want to receive opus-formatted audio,
        // or "default" mode when we want to receive linear16 audio
        return requireOpusAudio();
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
    public void onJoinJitsiMeetRequest(Call call, String s,
                                       Map<String, String> map)
    {
        throw new UnsupportedOperationException("Incoming calls are " +
                "not supported by TranscriptionGatewaySession");
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
        if(confMembers == null)
        {
            logger.warn("Cannot add initial ConferenceMembers to " +
                "transcription");
        }
        else
        {
            for(ConferenceMember confMember : confMembers)
            {
                // We should not have the bridge as a participant
                if("jvb".equals(confMember.getAddress()))
                {
                    continue;
                }

                String identifier = getParticipantIdentifier(confMember);

                this.transcriber.updateParticipant(identifier, confMember);
            }
        }

        List<ChatRoomMember> chatRoomMembers = getCurrentChatRoomMembers();
        if(chatRoomMembers == null)
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
        try
        {
            Jid jid = JidCreate.from(member.getAddress());

            if(jid.hasResource())
            {
                return jid.getResourceOrThrow().toString();
            }
        }
        catch (XmppStringprepException e)
        {
            e.printStackTrace();
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
        if(chatRoomMember == null)
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
        if(conferenceMember == null)
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
            logger.error("Cannot sent message as chatRoom is null");
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

    private String getAudioFormat()
    {
        return JigasiBundleActivator.getConfigurationService().getString(
            P_NAME_AUDIO_FORMAT, P_NAME_AUDIO_FORMAT_DEFAULT_VALUE
        );
    }

    private boolean requireOpusAudio()
    {
        return P_NAME_AUDIO_FORMAT_OPUS_VALUE.equals(getAudioFormat());
    }

}
