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

import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.protocol.event.DTMFReceivedEvent;
import net.java.sip.communicator.service.protocol.media.MediaAwareCallConference;
import org.jitsi.jigasi.transcription.*;
import org.jitsi.service.neomedia.MediaType;
import org.jitsi.service.neomedia.MediaUseCase;
import org.jitsi.service.neomedia.device.MediaDevice;
import org.jitsi.util.*;

import java.util.*;
import java.util.function.BiConsumer;

/**
 * A TranscriptionGatewaySession is able to join a JVB conference and
 * manage the transcription of said conference
 *
 * @author Nik Vaessen
 */
public class TranscriptionGatewaySession
    extends AbstractGatewaySession
    implements TranscriptionListener
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
     * The name which should be fell back on when a participants's name
     * cannot be retrieved
     */
    private final static String FALLBACK_NAME = "Unknown";

    /**
     * The TranscriptionService used by this session
     */
    private TranscriptionService service;

    /**
     * The ChatRoom of the conference which is going to be transcribed.
     * We will post messages to the ChatRoom to update users of progress
     * of transcription
     */
    private ChatRoom chatRoom = null;

    /**
     * The transcriber managing transcriptions of audio
     */
    private Transcriber transcriber = null;

    /**
     * The call to the jvb room jigasi joins. This is used to get
     * the names and ssrc's of the participants
     */
    private Call jvbCall = null;

    /**
     * Create a TranscriptionGatewaySession which can handle the transcription
     * of a JVB conference
     *
     * @param gateway the gateway which controls this session
     * @param context the context of the call this session is joining
     * @param service the TranscriptionService which should be used
     *                to transcribe the audio in the conference
     */
    public TranscriptionGatewaySession(AbstractGateway gateway,
                                       CallContext context,
                                       TranscriptionService service)
    {
        super(gateway, context);
        this.service = service;
    }

    @Override
    void onConferenceCallInvited(Call incomingCall)
    {
        // We got invited to a room, ready up the transcriber!
        transcriber = new Transcriber(service);
        transcriber.addTranscriptionListener(this);
        logger.debug("Invited for conference");
    }

    @Override
    Exception onConferenceCallStarted(Call jvbConferenceCall)
    {
        // We can now safely set the Call connecting to the muc room
        // and the ChatRoom of the muc room
        this.jvbCall = jvbConferenceCall;
        this.chatRoom = super.jvbConference.getJvbRoom();

        // We create a MediaWareCallConference whose MediaDevice
        // will get the get all of the audio and video packets
        jvbConferenceCall.setConference(new MediaAwareCallConference()
            {
                @Override
                public MediaDevice getDefaultDevice(MediaType mediaType,
                                                    MediaUseCase useCase)
                {
                    if(MediaType.AUDIO.equals(mediaType))
                    {
                        return transcriber.getMediaDevice();
                    }
                    // FIXME: 18/07/17 what to do with video?
                    // will cause an exception when mediaType == VIDEO
                    return null;
                }
            });

        // for every member already in the room, now is the time to add them
        // to the transcriber
        addInitialMembers();

        // FIXME: 20/07/17 Do we want to start transcribing on joining room?
        transcriber.start();
        sendMessageToRoom("Started transcription!");

        logger.debug("TranscriptionGatewaySession started transcribing");

        return null;
    }

    @Override
    void onJvbConferenceStopped(JvbConference jvbConference,
                                int reasonCode, String reason)
    {
        // FIXME: 22/07/17 this actually happens only every user leaves
        // instead of when transcription is user.
        // Need a solution for stopping the transcription earlier

        // The conference is over, make sure the transcriber stops
        if(!transcriber.finished())
        {
            transcriber.stop();
        }

        logger.debug("Conference ended");
    }

    @Override
    void notifyMemberJoined(ChatRoomMember chatMember)
    {
        super.notifyMemberJoined(chatMember);

        ConferenceMember confMember = findMatchingConferenceMember(chatMember);

        if (confMember == null)
        {
            logger.debug("Starting WaitThread for joining ChatRoomMember "+
                    chatMember.getDisplayName());
            new WaitForConferenceMemberThread(chatMember,
                    this::addParticipant).start();
        }
        else
        {
            addParticipant(chatMember, confMember);
        }
    }

    @Override
    void notifyMemberLeft(ChatRoomMember chatMember)
    {
        super.notifyMemberLeft(chatMember);

        ConferenceMember confMember = findMatchingConferenceMember(chatMember);

        if (confMember == null)
        {
            logger.debug("Starting WaitThread for leaving ChatRoomMember "+
                    chatMember.getDisplayName());

            new WaitForConferenceMemberThread(chatMember,
                    this::removeParticipant).start();
        }
        else
        {
            removeParticipant(chatMember, confMember);
        }
    }

    /**
     * Find the ConferenceMember which has the same ID as the given
     * ChatRoomMember, if any exists
     *
     * @param chatMember the ChatRoomMember whose match to find
     * @return the matching ConferenceMember of the given ChatRoomMember or
     * null if no match found
     */
    private ConferenceMember findMatchingConferenceMember(
            ChatRoomMember chatMember)
    {
        List<ConferenceMember> confMembers = getCurrentConferenceMembers();

        if(confMembers == null)
        {
            logger.warn("ConferenceMember list is null");
            return null;
        }
        if(chatMember == null)
        {
            throw new IllegalArgumentException("ChatRoomMember is null");
        }

        // if Address of ChatRoomMember and ID of ConferenceMember are equal,
        // they are the same person
        String address = chatMember.getContactAddress();
        logger.trace("Trying to find matching ConferenceMember with" +
                " id " + address);

        if(address == null)
        {
            logger.warn("address of ChatRoomMember is null");
            return null;
        }

        for(ConferenceMember confMember : confMembers)
        {
            String id = getConferenceMemberID(confMember);
            logger.trace("There is a ConferenceMember with id " + id);
            if(address.equals(id))
            {
                return confMember;
            }
        }

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
        String toSend = (result.getName() == null ?
                            FALLBACK_NAME :
                            result.getName())
                + ": " + result.getTranscription();

        sendMessageToRoom(toSend);
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
        sendMessageToRoom("The complete transcription can be " +
                "found at <insert_link_here>");
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
        throw new UnsupportedOperationException("TranscriptionGatewaySession does " +
                "not support receiving DTMF tones");
    }

    /**
     * Add every participant already in the room to the transcriber
     */
    private void addInitialMembers()
    {
        List<ConferenceMember> confMembers = getCurrentConferenceMembers();
        List<ChatRoomMember> chatRoomMembers = getCurrentChatRoomMembers();

        if(confMembers == null || chatRoomMembers == null)
        {
            logger.warn("Cannot add initial members to transcription" );
            return;
        }

        // We can get the name of ConferenceMember by comparing the unique ID
        // given to a ConferenceMember and ChatRooMember; The same
        // person should have the same ID.
        // The ID is called the "address" for ChatRoomMember and
        // is part of the address for ConferenceMember

        // So first get a map of every id to the ChatRoomMember instance
        Map<String, ChatRoomMember> chatRoomMemberMap
                = getChatRoomMemberMap(chatRoomMembers);

        // and then for every non-jvb ConferenceMember, add to Transcriber
        logger.debug(confMembers.size() + " conferenceMembers currently" +
                " in room");
        for(ConferenceMember confMember : confMembers)
        {
            String address = confMember.getAddress();
            if("jvb".equals(address))
            {
                logger.trace("There is a ConferenceMember with ID jvb");
                continue; // as jvb won't be an interesting member to transcribe
            }

            String id = getConferenceMemberID(confMember);
            ChatRoomMember chatRoomMember = chatRoomMemberMap.get(id);
            if(chatRoomMember == null)
            {
                logger.warn("Not able to find a ChatRoomMember for " +
                        "ConferenceMember with ID " + id);
                continue;
            }
            logger.trace("There is a ConferenceMember with ID " + id +
                    "and a ChatRoomMember with address" + chatRoomMember.
                    getContactAddress());

            addParticipant(chatRoomMember, confMember);
        }
    }

    /**
     * Add a new participant to the transcriber
     *
     * @param chatMember the ChatRoomMember instance of the participant
     * @param confMember the ConferenceMember instance of the participant
     */
    private void addParticipant(ChatRoomMember chatMember,
                                ConferenceMember confMember)
    {
        String name;
        if((name = chatMember.getDisplayName()) == null)
        {
            name = FALLBACK_NAME;
        }
        long ssrc = getConferenceMemberAudioSSRC(confMember);

        transcriber.add(name, ssrc);
    }

    /**
     * remove a participant from being transcribed
     *
     * @param chatMember the ChatRoomMember instance of the participant
     * @param confMember the ConferenceMember instance of the participant
     */
    private void removeParticipant(ChatRoomMember chatMember,
                                   ConferenceMember confMember)
    {
        String name;
        if((name = chatMember.getDisplayName()) == null)
        {
            name = FALLBACK_NAME;
        }
        long ssrc = getConferenceMemberAudioSSRC(confMember);

        transcriber.remove(name, ssrc);
    }

    /**
     * Helper method for getting the SSRC of a conference member due to issues
     * with unsigned longs
     *
     * @param confMember the conference member whose SSRC to get
     * @return the ssrc which is casted to unsigned long
     */
    private long getConferenceMemberAudioSSRC(ConferenceMember confMember)
    {
        // bitwise AND to fix signed int casted to long
        return confMember.getAudioSsrc() & 0xffffffffL;
    }

    /**
     * Get a map which maps the address of a ChatRooMember to its instance
     *
     * @param chatRoomMembers the list of ChatRoomMembers to map
     * @return the map where each member has its id mapped to its instance
     */
    private Map<String, ChatRoomMember> getChatRoomMemberMap(
            List<ChatRoomMember> chatRoomMembers)
    {
        HashMap<String, ChatRoomMember> addressToInstanceMap = new HashMap<>();

        logger.trace(chatRoomMembers.size() + " members in chatroom");
        for(ChatRoomMember chatMember : chatRoomMembers)
        {
            String address = chatMember.getContactAddress();
            // the name of a ChatRoomMember is the same as the ContactAddress
            addressToInstanceMap.put(address, chatMember);

            logger.trace(String.format(
                    "mapped ChatRoomMember key=%s, value=%s id=%s",
                    address,
                    chatMember.getDisplayName(),
                    chatMember.getContactAddress()));
        }

        return addressToInstanceMap;
    }

    /**
     * Get a list of all the ConferenceMembers currently in the conference
     *
     * @return the list of ConferenceMembers or null if not currently connected
     */
    private List<ConferenceMember> getCurrentConferenceMembers()
    {
        return jvbCall == null ?
                null : jvbCall.getCallPeers().next().getConferenceMembers();
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
    private String getConferenceMemberID(ConferenceMember member)
    {
        // assume address is in the form
        // <room_name>@conference.<jitsi_meet_domain>/<some_unique_id>
        String address = member.getAddress();

        int idx = address.lastIndexOf("/");
        return  idx > -1 && (idx + 1) < address.length() ?
                address.substring(idx + 1) :
                null;
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
            e.printStackTrace();
        }
    }

    /**
     * This Thread is used in the
     * {@link TranscriptionGatewaySession#notifyMemberJoined(ChatRoomMember)} and
     * {@link TranscriptionGatewaySession#notifyMemberLeft(ChatRoomMember)} methods
     * to wait for a matching ConferenceMember, which might be added to the
     * CallPeer later than the ChatRoomMember gets added to the ChatRoom
     */
    private class WaitForConferenceMemberThread
        extends Thread
    {
        /**
         * The maximum amount of time this thread tries to wait for a match
         */
        private final static int MAX_WAIT_TIME_IN_MS = 5000;

        /**
         * The amount of time this thread waits before trying to find a match
         * again
         */
        private final static int ITERATION_WAIT_TIME_IN_MS = 500;

        /**
         * The ChatRooMember this thread is trying to match to a
         * ConferenceMember
         */
        private ChatRoomMember chatMember;

        /**
         * The matching ConferenceMember, if found
         */
        private ConferenceMember confMember = null;

        /**
         * The time this thread started waiting
         */
        private long startTime;

        /**
         * The consumer to run when a match was found
         */
        private BiConsumer<ChatRoomMember, ConferenceMember> matchConsumer;

        /**
         * Create a new Thread which will try to match a ChatRoomMember
         * to a ConferenceMember
         *
         * @param chatMember the ChatRoomMember to try to match
         * @param c the consumer which will accept the match, if found
         */
        WaitForConferenceMemberThread(ChatRoomMember chatMember,
                                      BiConsumer<ChatRoomMember,
                                              ConferenceMember> c)
        {
            this.chatMember = chatMember;
            this.matchConsumer = c;
        }

        @Override
        public void run()
        {
            startTime = System.currentTimeMillis();

            try
            {
                while(confMember == null && hasTimeLeft())
                {
                    Thread.sleep(ITERATION_WAIT_TIME_IN_MS);
                    confMember = findMatchingConferenceMember(chatMember);
                }

                if(confMember != null && matchConsumer != null)
                {
                    matchConsumer.accept(chatMember, confMember);
                }
                else
                {
                    logger.warn("WaitForConferenceMemberThread was" +
                            "not able to find a match");
                }
            }
            catch (InterruptedException e)
            {
                e.printStackTrace();
            }
        }

        /**
         * Get whether this Thread is allowed to keep looking for a match
         *
         * @return true if allowed to keep looking, false otherwise
         */
        private boolean hasTimeLeft()
        {
            return System.currentTimeMillis() - startTime < MAX_WAIT_TIME_IN_MS;
        }
    }

}
