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
package org.jitsi.jigasi.transcription;

import net.java.sip.communicator.impl.protocol.jabber.*;
import net.java.sip.communicator.service.protocol.*;
import org.jitsi.jigasi.util.Util;
import org.jitsi.xmpp.extensions.jitsimeet.*;
import org.jitsi.utils.logging.*;
import org.jivesoftware.smack.packet.*;

import javax.media.format.*;
import java.nio.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * This class describes a participant in a conference whose
 * transcription is required. It manages the transcription if its own audio
 * will locally buffered until enough audio is collected
 *
 * @author Nik Vaessen
 * @author Boris Grozev
 */
public class Participant
    implements TranscriptionListener
{
    /**
     * The logger of this class
     */
    private final static Logger logger = Logger.getLogger(Participant.class);

    /**
     * The expected amount of bytes each given buffer will have. Webrtc
     * usually has 20ms opus frames which are decoded to 2 bytes per sample
     * and 48000Hz sampling rate, which results in 2 * 48000 = 96000 bytes
     * per second, and because frames are 20 ms we have 1000/20 = 50
     * packets per second. Each packet will thus contain
     * 96000 / 50 = 1920 bytes
     */
    private static final int EXPECTED_AUDIO_LENGTH = 1920;

    /**
     * The size of the local buffer. A single packet is expected to contain
     * 1920 bytes, so the size should be a multiple of 1920. Using
     * 25 results in 20 ms * 25 packets = 500 ms of audio being buffered
     * locally before being send to the TranscriptionService
     */
    private static final int BUFFER_SIZE = EXPECTED_AUDIO_LENGTH * 25;

    /**
     * Whether we should buffer locally before sending
     */
    private static final boolean USE_LOCAL_BUFFER = true;

    /**
     * The string to use when a participant's name is unknown.
     * TODO: assign unique easy to read names to unknown participants (e.g.
     * Speaker 1, Speaker 2, etc.).
     */
    public static final String UNKNOWN_NAME = "Fellow Jitser";

    /**
     * The audio ssrc when it is not known yet
     */
    public static final long DEFAULT_UNKNOWN_AUDIO_SSRC = -1;

    /**
     * The standard URL to a gravatar avatar which still needs to be formatted
     * with the ID. The ID can be received by hasing the email with md5
     */
    private final static String GRAVARAR_URL_FORMAT
        = "https://www.gravatar.com/avatar/%s?d=wavatar&size=200";

    /**
     * The standard url to a meeple avatar by using a random ID. Default usage
     * when email not known, but using `avatar-id` does not actually result
     * int he same meeple
     */
    private final static String MEEPLE_URL_FORMAT
        = "https://abotars.jitsi.net/meeple/%s";

    /**
     * The {@link Transcriber} which owns this {@link Participant}.
     */
    private Transcriber transcriber;

    /**
     * The chat room participant.
     */
    private ChatRoomMember chatMember;

    /**
     * The conference member which this {@link Participant} is representing
     */
    private ConferenceMember confMember;

    /**
     * The streaming session which will constantly receive audio
     */
    private HashMap<String, TranscriptionService.StreamingRecognitionSession> sessions = new HashMap<>();

    /**
     * A buffer which is used to locally store audio before sending
     */
    private ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);

    /**
     * The AudioFormat of the audio being read. It is assumed to not change
     * after initialization
     */
    private AudioFormat audioFormat;

    /**
     * Whether the current session is still transcribing
     */
    private boolean isCompleted = false;

    /**
     * The string which is used to identify this participant
     */
    private String identifier;

    /**
     * Set default language code as en-US for this participant's locale
     */
    private Locale sourceLanguageLocale = Locale.forLanguageTag("en-US");

    /**
     * The String representing the language code for required translation.
     */
    private String translationLanguage = null;

    /**
     * The {@link SilenceFilter} which is used to filter out silenced audio.
     * This object is null when it's not required.
     */
    private SilenceFilter silenceFilter = null;

    /**
     * Create a participant with a given name and audio stream
     *
     * @param transcriber the transcriber which created this participant
     * @param identifier the string which is used to identify this participant
     */
    Participant(Transcriber transcriber, String identifier)
    {
        this(transcriber, identifier, false);
    }

    /**
     * Create a participant with a given name and audio stream
     *
     * @param transcriber the transcriber which created this participant
     * @param identifier the string which is used to identify this participant
     */
    Participant(Transcriber transcriber, String identifier, boolean filterAudio)
    {
        this.transcriber = transcriber;
        this.identifier = identifier;

        if (filterAudio)
        {
            silenceFilter = new SilenceFilter();
        }
    }

    /**
     * A debug name that can be used in the logs related to this participant
     * instance.
     *
     * @return a {@code String} or {@code null} if this instance has not been
     * initialized enough to get a proper debug name(not joined any room yet).
     */
    String getDebugName()
    {
        ChatRoomMember _chatMember = this.chatMember;

        if (_chatMember == null)
        {
            return null;
        }

        String roomId = chatMember.getChatRoom().getIdentifier();

        if (roomId.contains("@"))
        {
            roomId = roomId.substring(0, roomId.indexOf("@"));
        }

        return roomId + "/" + _chatMember.getName();
    }

    /**
     * @return the string to uses when identifying this participant in the
     * transcript (if a display name wasn't specifically set we use the id or
     * a default string).
     */
    public String getName()
    {
        if (chatMember == null)
        {
            return UNKNOWN_NAME;
        }

        String name = chatMember.getDisplayName();
        if (name != null && !name.isEmpty())
        {
            return name;
        }

        return UNKNOWN_NAME;
    }

    /**
     * Returns participant email if any.
     * @return participant email if any.
     */
    public String getEmail()
    {
        if (chatMember == null)
        {
            return null;
        }
        if (!(chatMember instanceof ChatRoomMemberJabberImpl))
        {
            return null;
        }

        return ((ChatRoomMemberJabberImpl) chatMember).getEmail();
    }

    /**
     * @return the URL of the avatar of the participant, if one is set.
     */
    public String getAvatarUrl()
    {
        if (chatMember == null)
        {
            return null;
        }
        if (!(chatMember instanceof ChatRoomMemberJabberImpl))
        {
            return null;
        }

        ChatRoomMemberJabberImpl memberJabber
            = ((ChatRoomMemberJabberImpl) this.chatMember);

        IdentityPacketExtension ipe = getIdentityExtensionOrNull(
            memberJabber.getLastPresence());

        String url;
        if (ipe != null && (url = ipe.getUserAvatarUrl()) != null)
        {
            return url;
        }
        else if ((url = memberJabber.getAvatarUrl()) != null)
        {
            return url;
        }

        String email;
        if ((email = getEmail()) != null)
        {
            return String.format(GRAVARAR_URL_FORMAT,
                Util.stringToMD5hash(email));
        }

        // Create a nice looking meeple avatar when avatar-url nor email is set
        AvatarIdPacketExtension avatarIdExtension = getAvatarIdExtensionOrNull(
            memberJabber.getLastPresence());
        String avatarId;
        if (avatarIdExtension != null &&
            (avatarId = avatarIdExtension.getAvatarId()) != null)
        {
            return String.format(MEEPLE_URL_FORMAT,
                Util.stringToMD5hash(avatarId));
        }
        else
        {
            return String.format(MEEPLE_URL_FORMAT,
                Util.stringToMD5hash(identifier));
        }
    }

    /**
     * Get the user-name in the identity presence, if present
     *
     * @return the user-name or null
     */
    public String getIdentityUserName()
    {
        if (!(chatMember instanceof ChatRoomMemberJabberImpl))
        {
            return null;
        }

        IdentityPacketExtension ipe
            = getIdentityExtensionOrNull(
                ((ChatRoomMemberJabberImpl) chatMember).getLastPresence());

        return ipe != null ?
            ipe.getUserName():
            null;
    }

    /**
     * Get the user id in the identity presence, if present
     *
     * @return the user id or null
     */
    public String getIdentityUserId()
    {
        if (!(chatMember instanceof ChatRoomMemberJabberImpl))
        {
            return null;
        }

        IdentityPacketExtension ipe
            = getIdentityExtensionOrNull(
                ((ChatRoomMemberJabberImpl) chatMember).getLastPresence());

        return ipe != null ?
            ipe.getUserId():
            null;
    }

    /**
     * Get the key to access the transcription session when language
     * routing is supported
     *
     * @return the key of the Hashmap
     */
    public String getLanguageKey()
    {
        if (transcriber.getTranscriptionService().supportsLanguageRouting())
        {
            return this.getSourceLanguage();
        }
        return "global";
    }

    /**
     * Get the group id in the identity presence, if present
     *
     * @return the group id or null
     */
    public String getIdentityGroupId()
    {
        if (!(chatMember instanceof ChatRoomMemberJabberImpl))
        {
            return null;
        }

        IdentityPacketExtension ipe
            = getIdentityExtensionOrNull(
                ((ChatRoomMemberJabberImpl) chatMember).getLastPresence());

        return ipe != null ?
            ipe.getGroupId():
            null;
    }

    /**
     * Get the {@link IdentityPacketExtension} inside a {@link Presence},
     * or null when it's not inside
     *
     * @param p the presence
     * @return the {@link IdentityPacketExtension} or null
     */
    private IdentityPacketExtension getIdentityExtensionOrNull(Presence p)
    {
        return p.getExtension(IdentityPacketExtension.class);
    }

    /**
     * Get the {@link AvatarIdPacketExtension} inside a {@link Presence} or null
     * when it's not inside
     *
     * @param p the presence
     * @return the {@link AvatarIdPacketExtension} or null
     */
    private AvatarIdPacketExtension getAvatarIdExtensionOrNull(Presence p)
    {
        return p.getExtension(AvatarIdPacketExtension.class);
    }

    private TranscriptionRequestExtension
                getTranscriptionRequestExtensionOrNull(Presence p)
    {
        return p != null
                ? p.getExtension(TranscriptionRequestExtension.class)
                : null;
    }

    /**
     * Get the ssrc of the audio of this participant
     *
     * @return the srrc
     */
    public long getSSRC()
    {
        if (confMember == null)
        {
            return DEFAULT_UNKNOWN_AUDIO_SSRC;
        }
        return getConferenceMemberAudioSSRC(confMember);
    }

    /**
     * Get the source language code of the transcription for
     * this {@link Participant}.
     *
     * @return source language code
     */
    public String getSourceLanguage()
    {
        return sourceLanguageLocale == null ?
            null :
            sourceLanguageLocale.getLanguage();
    }

    /**
     * Get the language code for translation for this {@link Participant}.
     *
     * @return language code for translation
     */
    public String getTranslationLanguage()
    {
        return translationLanguage;
    }

    /**
     * Set the source language locale for this @link {@link Participant}.
     *
     * @param language code for transcription
     */
    public void setSourceLanguage(String language)
    {
        if (language == null)
        {
            sourceLanguageLocale = null;
        }
        else
        {
            sourceLanguageLocale = Locale.forLanguageTag(language);
        }
    }

    /**
     * Set the language code for translation for this @link {@link Participant}.
     *
     * @param language code for translation
     */
    public void setTranslationLanguage(String language)
    {
        translationLanguage = language;
    }

    /**
     * Set the {@link ConferenceMember} belonging to this participant
     *
     * @param confMember the conference member
     */
    public void setConfMember(ConferenceMember confMember)
    {
        this.confMember = confMember;
    }

    /**
     * Set the {@link ChatRoomMember} belonging to this participant
     *
     * @param chatMember the chatroom member
     */
    public void setChatMember(ChatRoomMember chatMember)
    {
        this.chatMember = chatMember;
    }

    /**
     * Get the identifier in the JID of this participant
     *
     * @return the id
     */
    public String getId()
    {
        return identifier;
    }

    /**
     * When a participant joined it accepts audio and will send it
     * to be transcribed
     */
    void joined()
    {
        TranscriptionService.StreamingRecognitionSession
                session = sessions.getOrDefault(getLanguageKey(), null);

        if (session != null && !session.ended())
        {
            return; // no need to create new session
        }

        if (transcriber.getTranscriptionService().supportsStreamRecognition())
        {
            session = transcriber.getTranscriptionService()
                .initStreamingSession(this);
            session.addTranscriptionListener(this);
            sessions.put(getLanguageKey(), session);
            isCompleted = false;
        }
    }

    /**
     * When a participant has left it does not accept audio and thus no new
     * results will come in
     */
    public void left()
    {
        TranscriptionService.StreamingRecognitionSession session = sessions.getOrDefault(getLanguageKey(), null);
        if (session != null)
        {
            session.end();
        }
    }

    /**
     * Give a packet of the audio of this participant such that it can be
     * buffered and sent to the transcription once enough has been stored
     *
     * Note: the thread on which this method is called has only a limited amount
     * of time until it is shutdown. Thus, we need to minimize the amount of
     * work we do on in this method (and the children this method calls).
     * This is done by using a single-threaded {@link ExecutorService} defined
     * in {@link Transcriber#executorService}.
     *
     * @param buffer a buffer which is expected to contain a single packet
     *               of audio of this participant
     */
    void giveBuffer(javax.media.Buffer buffer)
    {
        if (audioFormat == null)
        {
            audioFormat = (AudioFormat) buffer.getFormat();
        }

        byte[] audio = (byte[]) buffer.getData();

        if (USE_LOCAL_BUFFER)
        {
            buffer(audio);
        }
        else
        {
            sendRequest(audio);
        }
    }

    @Override
    public void notify(TranscriptionResult result)
    {
        result.setParticipant(this);
        if (logger.isDebugEnabled())
            logger.debug(result);
        transcriber.notify(result);
    }

    @Override
    public void completed()
    {
        isCompleted = true;
        transcriber.checkIfFinishedUp();
    }

    @Override
    public void failed(FailureReason reason)
    {
        isCompleted = true;
        logger.error(getDebugName() + " transcription failed: " + reason);
        transcriber.stop(reason);
    }

    /**
     * Get whether everything this participant said has been transcribed
     *
     * @return true if completed transcribing, false otherwise
     */
    public boolean isCompleted()
    {
        return isCompleted;
    }

    /**
     * Store the given audio in a buffer. When the buffer is full,
     * send the audio
     *
     * @param audio the audio to buffer
     */
    private void buffer(byte[] audio)
    {
        // note: the executorService is single-threaded and thus order
        //       is preserved, even though there are multiple participants.
        transcriber.executorService.execute(() ->
           {
               byte[] toBuffer;
               if (silenceFilter != null)
               {
                   silenceFilter.giveSegment(audio);
                   if (silenceFilter.shouldFilter())
                   {
                       return;
                   }
                   else if (silenceFilter.newSpeech())
                   {
                       // we need to cast here to keep compatability when moving between java8 and java11
                       ((Buffer) buffer).clear();
                       toBuffer = silenceFilter.getSpeechWindow();
                   }
                   else
                   {
                       toBuffer = audio;
                   }
               }
               else
               {
                   toBuffer = audio;
               }

               try
               {
                   buffer.put(toBuffer);
               }
               catch (BufferOverflowException | ReadOnlyBufferException e)
               {
                   sendRequest(audio);
               }

               int spaceLeft = buffer.limit() - buffer.position();
               if (spaceLeft < EXPECTED_AUDIO_LENGTH)
               {
                   sendRequest(buffer.array());
                   // we need to cast here to keep compatability when moving between java8 and java11
                   ((Buffer) buffer).clear();
               }
           });
    }

    /**
     * Send the specified audio to the TranscriptionService.
     * <p>
     * An ExecutorService is used to offload work on the mixing thread
     *
     * @param audio the audio to send
     */
    private void sendRequest(byte[] audio)
    {
        transcriber.executorService.execute(() ->
        {
            TranscriptionService.StreamingRecognitionSession session = sessions.getOrDefault(getLanguageKey(), null);
            TranscriptionRequest request
                = new TranscriptionRequest(audio,
                                           audioFormat,
                                           sourceLanguageLocale);

            if (session != null && !session.ended())
            {
                session.sendRequest(request);
            }
            else if (transcriber.getTranscriptionService().supportsStreamRecognition())
            // re-establish prematurely ended streaming session
            {
                session = transcriber.getTranscriptionService()
                        .initStreamingSession(this);
                session.addTranscriptionListener(this);
                sessions.put(getLanguageKey(), session);
            }
            else
            // fallback if TranscriptionService does not support streams
            {
                // FIXME: 22/07/17 This just assumes given BUFFER_LENGTH
                // is long enough to get decent audio length. Also does
                // not take into account that participant's audio will
                // be cut of mid-sentence. For better results, try to
                // buffer until audio volume is silent for a "decent
                // amount of time". Only relevant if Streaming
                // recognition is not supported by the
                // TranscriptionService
                transcriber.getTranscriptionService().sendSingleRequest(
                        request,
                        this::notify);
            }
        });
    }

    /**
     * Returns the transcriber instance that created this participant.
     * @return the transcriber instance that created this participant.
     */
    public Transcriber getTranscriber()
    {
        return transcriber;
    }

    /**
     * Helper method for getting the SSRC of a conference member due to issues
     * with unsigned longs
     *
     * @param confMember the conference member whose SSRC to get
     * @return the ssrc which is casted to unsigned long
     */
    private static long getConferenceMemberAudioSSRC(
        ConferenceMember confMember)
    {
        // bitwise AND to fix signed int casted to long
        return confMember.getAudioSsrc() & 0xffffffffL;
    }

    /**
     * Check whether this Participant is requesting a source language
     *
     * @return true when the source language is set and non-empty, false
     * otherwise.
     */
    public boolean hasValidSourceLanguage()
    {
        String lang = this.getSourceLanguage();

        return lang != null && !lang.isEmpty();
    }

    /**
     * Get whether this {@link Participant} is requesting transcription by
     * checking the {@link TranscriptionRequestExtension} in the
     * {@link Presence}
     *
     * @return true when the {@link Participant} is requesting transcription,
     * false otherwise
     */
    public boolean isRequestingTranscription()
    {
        // FIXME inconsistent ChatRoomMemberJabberImpl type usage: in some
        //  places it checks instanceof and in other doesn't. Why bother
        //  allowing generic type if transcriptions would crash anyway on
        //  other type.
        ChatRoomMemberJabberImpl memberJabber
            = ((ChatRoomMemberJabberImpl) this.chatMember);
        TranscriptionRequestExtension ext
            = getTranscriptionRequestExtensionOrNull(
                memberJabber != null ? memberJabber.getLastPresence() : null);

        return ext != null && Boolean.parseBoolean(ext.getText());
    }
}
