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
import org.jitsi.util.*;

import javax.media.format.*;
import java.nio.*;
import java.util.*;

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
     * Currently assume everyone to have this locale
     */
    private final static Locale ENGLISH_LOCALE = Locale.forLanguageTag("en-US");

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
    public static final String UNKNOWN_NAME = "Unknown";

    /**
     * The {@link Transcriber} which owns this {@link Participant}.
     */
    private Transcriber transcriber;

    /**
     * The id identifying audio belonging to this participant
     */
    private long ssrc;

    /**
     * The chat room participant.
     */
    private ChatRoomMember chatMember;

    /**
     * The streaming session which will constantly receive audio
     */
    private TranscriptionService.StreamingRecognitionSession session;

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
     * Create a participant with a given name and audio stream
     *
     * @param chatMember the chat room participant.
     * @param ssrc the ssrc of the audio of this participant
     */
    Participant(Transcriber transcriber, ChatRoomMember chatMember, long ssrc)
    {
        this.transcriber = transcriber;
        this.chatMember = chatMember;
        this.ssrc = ssrc;
    }

    /**
     * @return the string to uses when identifying this participant in the
     * transcript (if a display name wasn't specifically set we use the id or
     * a default string).
     */
    public String getName()
    {
        String name = chatMember.getDisplayName();
        if (name != null)
        {
            return name;
        }
        String id = chatMember.getContactAddress();
        if (id != null)
        {
            return id;
        }
        return UNKNOWN_NAME;
    }

    /**
     * Returns participant email if any.
     * @return participant email if any.
     */
    public String getEmail()
    {
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
        if (!(chatMember instanceof ChatRoomMemberJabberImpl))
        {
            return null;
        }

        return ((ChatRoomMemberJabberImpl) chatMember).getAvatarUrl();
    }

    /**
     * Get the ssrc of the audio of this participant
     *
     * @return the srrc
     */
    public long getSSRC()
    {
        return ssrc;
    }

    /**
     * Get the id in the JID of this participant
     *
     * @return the id
     */
    public String getId()
    {
        return chatMember.getContactAddress();
    }

    /**
     * When a participant joined it accepts audio and will send it
     * to be transcribed
     */
    void joined()
    {
        if (session != null && !session.ended())
        {
            return; // no need to create new session
        }

        if (transcriber.getTranscriptionService().supportsStreamRecognition())
        {
            session
                = transcriber.getTranscriptionService().initStreamingSession();
            session.addTranscriptionListener(this);
            isCompleted = false;
        }
    }

    /**
     * When a participant has left it does not accept audio and thus no new
     * results will come in
     */
    void left()
    {
        if (session != null)
        {
            session.end();
        }
    }

    /**
     * Give a packet of the audio of this participant such that it can be
     * buffered and sent to the transcription once enough has been stored
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
        try
        {
            buffer.put(audio);
        }
        catch (BufferOverflowException | ReadOnlyBufferException e)
        {
            e.printStackTrace();
        }

        int spaceLeft = buffer.limit() - buffer.position();
        if(spaceLeft < EXPECTED_AUDIO_LENGTH)
        {
            sendRequest(buffer.array());
            buffer.clear();
        }
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
        transcriber.executorService.submit(() ->
        {
            TranscriptionRequest request
                = new TranscriptionRequest(audio,
                                           audioFormat,
                                           ENGLISH_LOCALE);

            if (session != null && !session.ended())
            {
                session.sendRequest(request);
            }
            else
            // fallback if TranscriptionService does not support streams
            // or session got ended prematurely
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
}
