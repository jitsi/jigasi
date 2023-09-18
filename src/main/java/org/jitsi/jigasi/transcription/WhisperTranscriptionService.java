/*
 * Jigasi, the JItsi GAteway to SIP.
 *
 * Copyright @ 2023 8x8 Inc.
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

import org.jitsi.impl.neomedia.device.*;
import org.jitsi.utils.logging.*;

import java.nio.*;
import java.util.function.*;


/**
 * Implements a TranscriptionService which uses a custom built Whisper server
 * to perform live transcription.
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
            this.mediaDevice = new TranscribingAudioMixerMediaDevice(new WhisperAudioSilenceMediaDevice(), listener);
        }

        return this.mediaDevice;
    }

    @Override
    public boolean disableSilenceFilter()
    {
        return true;
    }

    /**
     * No configuration required yet
     */
    @Override
    public boolean isConfiguredProperly()
    {
        return true;
    }

    @Override
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
            throw new UnsupportedOperationException("Failed to create WS streaming session", e);
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

        /* The name of the participant */
        private final String participantId;

        /* Transcription language requested by the user who requested the transcription */
        private String transcriptionTag = "en-US";

        private final WhisperWebsocket wsClient;

        private final String roomId;

        private final WhisperConnectionPool connectionPool;


        WhisperWebsocketStreamingSession(Participant participant)
                throws Exception
        {
            this.participant = participant;
            String[] debugName = this.participant.getDebugName().split("/");
            participantId = debugName[1];
            roomId = debugName[0];
            connectionPool = WhisperConnectionPool.getInstance();
            wsClient = connectionPool.getConnection(roomId, participantId);
            wsClient.setTranscriptionTag(transcriptionTag);
        }

        public void sendRequest(TranscriptionRequest request)
        {
            if (this.wsClient.ended())
            {
                logger.warn("Trying to send buffer without a connection.");
                return;
            }
            try
            {
                ByteBuffer audioBuffer = ByteBuffer.wrap(request.getAudio());
                wsClient.sendAudio(participantId, participant, audioBuffer);
            }
            catch (Exception e)
            {
                logger.error("Error while sending websocket request for participant " + participantId, e);
            }
        }

        public void addTranscriptionListener(TranscriptionListener listener)
        {
            wsClient.addListener(listener, participant);
        }

        public void end()
        {
            logger.info("Disconnecting " + this.participantId + " from Whisper transcription service.");
            connectionPool.end(this.roomId, this.participantId);
        }

        public boolean ended()
        {
            return wsClient.ended();
        }
    }
}
