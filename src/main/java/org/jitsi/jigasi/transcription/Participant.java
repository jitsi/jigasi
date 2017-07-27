package org.jitsi.jigasi.transcription;

import org.jitsi.util.*;

import javax.media.format.*;
import java.nio.*;

/**
 * This class describes a participant in a conference whose
 * transcription is required. It manages the transcription if its own audio
 * will locally buffered until enough audio is collected
 *
 * @author Nik Vaesen
 */
public class Participant
        implements TranscriptionListener
{
    /**
     * The logger of this class
     */
    private final static Logger logger = Logger.getLogger(Transcriber.class);

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

    public static final String UNKNOWN_NAME = "Unknown";

    /**
     * The {@link Transcriber} which owns this {@link Participant}.
     */
    private Transcriber transcriber;

    /**
     * The name of the participant
     */
    private String name;

    /**
     * The id identifying audio belonging to this participant
     */
    private long ssrc;

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
     * Create a participant with a given name and audio stream
     *
     * @param name the name of the participant
     */
    Participant(Transcriber transcriber, String name, long ssrc)
    {
        this.transcriber = transcriber;
        this.name = name;
        this.ssrc = ssrc;
    }

    /**
     * Get the name of the participant
     *
     * @return the name of this particular participant
     */
    public String getName()
    {
        return name == null ? UNKNOWN_NAME : name;
    }

    /**
     * Get the id of the audio of this participant
     *
     * @return the id
     */
    public long getSSRC()
    {
        return ssrc;
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

        TranscriptionService transcriptionService
            = transcriber.getTranscriptionService();
        if (transcriptionService.supportsStreamRecognition())
        {
            session = transcriptionService.initStreamingSession();
            session.addTranscriptionListener(this);
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
        logger.debug(result);
        transcriber.notify(result);
    }

    @Override
    public void completed()
    {
        // nothing to do
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
     * An ExecutorService is used to offload work on the mxing thread
     *
     * @param audio the audio to send
     */
    private void sendRequest(byte[] audio)
    {
        transcriber.executorService.submit(() ->
        {
            TranscriptionRequest request
                = new TranscriptionRequest(
                        audio, audioFormat, Transcriber.ENGLISH_LOCALE);

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
                // be cut of midsentence. For better results, try to
                // buffer until audio volume is silent for a "decent
                // amount of time". Only relevant if Streaming
                // recognition is not supported by the
                // TranscriptionService
                transcriber.getTranscriptionService().sendSingleRequest(
                        request, Participant.this::notify);
            }
        });
    }
}
