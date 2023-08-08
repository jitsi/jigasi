package org.jitsi.jigasi.transcription;
import org.eclipse.jetty.websocket.api.*;
import org.jitsi.impl.neomedia.device.AudioMixerMediaDevice;
import org.jitsi.impl.neomedia.device.ReceiveStreamBufferListener;
import org.jitsi.utils.logging.*;
import java.nio.*;
import java.util.function.*;


/**
 * Implements a TranscriptionService which uses
 * Whisper and a Python wrapper to do the transcription.
 *
 * @author Razvan Purdel
 */


public class WhisperTranscriptionService
        extends AbstractTranscriptionService
{

    /**
     * The logger for this class
     */
    private final static Logger logger
            = Logger.getLogger(WhisperTranscriptionService.class);

    @Override
    public AudioMixerMediaDevice getMediaDevice(ReceiveStreamBufferListener listener)
    {
        if (this.mediaDevice == null)
        {
            this.mediaDevice = new TranscribingAudioMixerMediaDevice(new WhisperTsAudioSilenceMediaDevice(), listener);
        }

        return this.mediaDevice;
    }

    /**
     * No configuration required yet
     */
    public boolean isConfiguredProperly()
    {
        return true;
    }

    public boolean supportsLanguageRouting()
    {
        return false;
    }

    @Override
    public void sendSingleRequest(final TranscriptionRequest request,
                                  final Consumer<TranscriptionResult> resultConsumer) {
        logger.warn("The Whisper transcription service does not support single requests :( please reconnect.");
    }

    @Override
    public StreamingRecognitionSession initStreamingSession(Participant participant)
            throws UnsupportedOperationException
    {
        try
        {
            WhisperWebsocketStreamingSession streamingSession = new WhisperWebsocketStreamingSession(participant);

            streamingSession.transcriptionTag = participant.getTranslationLanguage();
            if (streamingSession.transcriptionTag == null)
            {
                streamingSession.transcriptionTag = participant.getSourceLanguage();
            }
            return streamingSession;
        }
        catch (Exception e)
        {
            throw new UnsupportedOperationException("Failed to create ws streaming session", e);
        }
    }

    @Override
    public boolean supportsFragmentTranscription()
    {
        return true;
    }

    @Override
    public boolean supportsStreamRecognition()
    {
        return true;
    }

    /**
     * A Transcription session for transcribing streams, handles
     * the lifecycle of websocket
     */
    public static class WhisperWebsocketStreamingSession
            implements StreamingRecognitionSession
    {

        private final Participant participant;

        private final Session wsSession;
        /* The name of the participant */
        private final String debugName;

        /* Transcription language requested by the user who requested the transcription */
        private String transcriptionTag = "en-US";

        private final WhisperWebsocket wsClient;

        private String roomId = "";

        private WhisperConnectionPool connectionPool = null;


        WhisperWebsocketStreamingSession(Participant participant)
                throws Exception
        {
            this.participant = participant;
            connectionPool = WhisperConnectionPool.getInstance();
            roomId = participant.getChatMember().getChatRoom().toString();
            debugName = participant.getDebugName();
            wsClient = connectionPool.getConnection(this.roomId, this.debugName);
            wsSession = wsClient.getWsSession();
            wsClient.setTranscriptionTag(this.transcriptionTag);
        }

        public void sendRequest(TranscriptionRequest request)
        {
            if (wsSession == null)
            {
                logger.warn("Trying to send buffer without a connection.");
                return;
            }
            try
            {
                ByteBuffer audioBuffer = ByteBuffer.wrap(request.getAudio());
                wsClient.sendAudio(participant, audioBuffer);
            }
            catch (Exception e)
            {
                logger.error("Error while sending websocket request for participant " + debugName, e);
            }
        }

        public void addTranscriptionListener(TranscriptionListener listener)
        {
            wsClient.addListener(listener, participant);
        }

        public void end()
        {
            try
            {
                logger.info("Disconnecting " + this.debugName + " from Whisper transcription service.");
                connectionPool.end(this.roomId, this.debugName);
            }
            catch (Exception e)
            {
                logger.error("Error while finalizing websocket connection for participant " + debugName, e);
            }
        }

        public boolean ended()
        {
            return wsSession == null;
        }
    }
}