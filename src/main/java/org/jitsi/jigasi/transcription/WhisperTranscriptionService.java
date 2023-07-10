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
                                  final Consumer<TranscriptionResult> resultConsumer)
    {
        logger.warn("The Whisper transcription service does not support single requests.");
    }

    @Override
    public StreamingRecognitionSession initStreamingSession(Participant participant)
            throws UnsupportedOperationException
    {
        try
        {
            WhisperWebsocketStreamingSession streamingSession = new WhisperWebsocketStreamingSession(
                    participant.getDebugName(), participant);

            streamingSession.transcriptionTag = participant.getTranslationLanguage();
            if (streamingSession.transcriptionTag == null)
            {
                streamingSession.transcriptionTag = participant.getSourceLanguage();
            }
            return streamingSession;
        }
        catch (Exception e)
        {
            throw new UnsupportedOperationException("Failed to create streaming session", e);
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
    public class WhisperWebsocketStreamingSession
            implements StreamingRecognitionSession
    {

        private Participant participant;

        private Session session;
        /* The name of the participant */
        private final String debugName;

        /* Transcription language requested by the user who requested the transcription */
        private String transcriptionTag = "en-US";

        private WhisperWebsocket wsClient;

        private String roomId = "";

        private WhisperConnectionPool connectionPool = null;


        WhisperWebsocketStreamingSession(String debugName, Participant participant)
                throws Exception
        {
            this.connectionPool = WhisperConnectionPool.getInstance();
            this.roomId = participant.getChatMember().getChatRoom().toString();
            this.debugName = debugName;
            this.participant = participant;
            this.wsClient = connectionPool.getConnection(this.roomId, debugName);
            this.session = wsClient.getSession();
            this.wsClient.setTranscriptionTag(this.transcriptionTag);
        }

        public void sendRequest(TranscriptionRequest request)
        {
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
                this.connectionPool.end(this.roomId, this.debugName);
                this.session = null;
            }
            catch (Exception e)
            {
                logger.error("Error while finalizing websocket connection for participant " + debugName, e);
            }
        }

        public boolean ended()
        {
            return session == null;
        }
    }


}