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

package org.jitsi.jigasi.transcription;

import com.fasterxml.uuid.*;
import org.jitsi.impl.neomedia.device.*;
import org.jitsi.jigasi.*;
import org.jitsi.jigasi.transcription.openai.*;
import org.jitsi.utils.logging2.*;

import java.time.*;
import java.util.*;
import java.util.function.*;

/**
 * Implements a TranscriptionService which uses the OpenAI Realtime API
 * to perform live transcriptions.
 *
 * One WebSocket session is created per participant (not per room).
 *
 * Required configuration in sip-communicator.properties:
 *   org.jitsi.jigasi.transcription.openai.apiKey=sk-...
 *
 * Optional:
 *   org.jitsi.jigasi.transcription.openai.websocketUrl=wss://api.openai.com/v1/realtime
 *   org.jitsi.jigasi.transcription.openai.model=gpt-4o-realtime-preview-2024-12-17
 */
public class OpenAIRealtimeTranscriptionService
    extends AbstractTranscriptionService
{
    private static final Logger logger
        = new LoggerImpl(OpenAIRealtimeTranscriptionService.class.getName());

    @Override
    public AudioMixerMediaDevice getMediaDevice(ReceiveStreamBufferListener listener)
    {
        if (this.mediaDevice == null)
        {
            this.mediaDevice = new TranscribingAudioMixerMediaDevice(
                new PCMAudioSilence24kMediaDevice(), listener);
        }
        return this.mediaDevice;
    }

    @Override
    public boolean isConfiguredProperly()
    {
        String apiKey = JigasiBundleActivator.getConfigurationService()
            .getString(OpenAIRealtimeClient.API_KEY_CONFIG, "");
        if (apiKey.isEmpty())
        {
            logger.error("OpenAI Realtime transcription is not configured: "
                + OpenAIRealtimeClient.API_KEY_CONFIG + " is missing.");
            return false;
        }
        return true;
    }

    @Override
    public boolean supportsStreamRecognition()
    {
        return true;
    }

    @Override
    public boolean supportsFragmentTranscription()
    {
        return false;
    }

    @Override
    public boolean supportsLanguageRouting()
    {
        return false;
    }

    @Override
    public boolean disableSilenceFilter()
    {
        return true;
    }

    @Override
    public void sendSingleRequest(TranscriptionRequest request,
                                  Consumer<TranscriptionResult> resultConsumer)
    {
        logger.warn("OpenAI Realtime transcription does not support single requests.");
    }

    @Override
    public StreamingRecognitionSession initStreamingSession(Participant participant)
        throws UnsupportedOperationException
    {
        try
        {
            return new OpenAIStreamingSession(participant);
        }
        catch (Exception e)
        {
            throw new UnsupportedOperationException(
                "Failed to create OpenAI Realtime streaming session", e);
        }
    }

    /**
     * One streaming session per participant, backed by one OpenAIRealtimeClient.
     */
    private static class OpenAIStreamingSession
        implements StreamingRecognitionSession, OpenAIRealtimeClientListener
    {
        private final Logger logger;

        private final Participant participant;

        private final OpenAIRealtimeClient client;

        private final List<TranscriptionListener> listeners = new ArrayList<>();

        private UUID transcriptionId;

        private Instant sessionStart;

        private boolean sessionEnding = false;

        /** Accumulates delta chunks until a completed event resets it. */
        private final StringBuilder partialBuffer = new StringBuilder();

        /** Commit audio buffer every 50 frames (20ms each = 1s of audio). */
        private static final int COMMIT_INTERVAL_FRAMES = 50;

        private int frameCount = 0;

        OpenAIStreamingSession(Participant participant)
        {
            this.participant = participant;
            this.logger = participant.getCallContext().getLogger()
                .createChildLogger(OpenAIStreamingSession.class.getName());

            transcriptionId = Generators.timeBasedReorderedGenerator().generate();
            sessionStart = Instant.now();

            String language = participant.getSourceLanguage();
            // OpenAI whisper-1 wants BCP-47 language subtag only (e.g. "pt", "en")
            String langCode = (language != null && language.contains("-"))
                ? language.split("-")[0]
                : language;

            client = new OpenAIRealtimeClient(this, langCode);
            client.connect();
        }

        @Override
        public void sendRequest(TranscriptionRequest request)
        {
            if (sessionEnding)
            {
                logger.warn("Session is ending, dropping audio.");
                return;
            }
            if (!client.isConnected())
            {
                logger.warn("OpenAI client not yet connected, dropping audio frame.");
                return;
            }
            try
            {
                client.sendAudio(request.getAudio());
                frameCount++;
                if (frameCount >= COMMIT_INTERVAL_FRAMES)
                {
                    client.commitAudioBuffer();
                    frameCount = 0;
                }
            }
            catch (Exception e)
            {
                logger.error("Error sending audio to OpenAI Realtime API", e);
            }
        }

        @Override
        public void end()
        {
            logger.info("Ending OpenAI Realtime session.");
            sessionEnding = true;
            client.close();
        }

        @Override
        public boolean ended()
        {
            return sessionEnding;
        }

        @Override
        public void addTranscriptionListener(TranscriptionListener listener)
        {
            listeners.add(listener);
        }

        // --- OpenAIRealtimeClientListener ---

        @Override
        public void onConnect()
        {
            logger.info("OpenAI Realtime session connected.");
        }

        @Override
        public void onClose(int statusCode, String statusMessage)
        {
            if (logger.isDebugEnabled())
            {
                logger.debug("OpenAI Realtime session closed: " + statusCode + " " + statusMessage);
            }
        }

        @Override
        public void onTranscriptionDelta(String delta)
        {
            partialBuffer.append(delta);
            String partial = partialBuffer.toString().trim();
            if (partial.isEmpty())
            {
                return;
            }

            TranscriptionResult result = new TranscriptionResult(
                participant,
                transcriptionId,
                sessionStart,
                true,   // isPartial
                getLanguage(),
                0.0,
                new TranscriptionAlternative(partial));

            for (TranscriptionListener l : listeners)
            {
                l.notify(result);
            }
        }

        @Override
        public void onTranscriptionCompleted(String transcript)
        {
            partialBuffer.setLength(0);
            String text = transcript.trim();
            if (text.isEmpty())
            {
                return;
            }

            TranscriptionResult result = new TranscriptionResult(
                participant,
                transcriptionId,
                sessionStart,
                false,  // isFinal
                getLanguage(),
                1.0,
                new TranscriptionAlternative(text));

            for (TranscriptionListener l : listeners)
            {
                l.notify(result);
            }

            transcriptionId = Generators.timeBasedReorderedGenerator().generate();
            sessionStart = Instant.now();
        }

        @Override
        public void onError(Throwable error)
        {
            logger.error("OpenAI Realtime transcription error", error);
            String msg = error.getMessage();
            if (msg != null && (msg.contains("invalid_api_key") || msg.contains("Incorrect API key")))
            {
                for (TranscriptionListener l : listeners)
                {
                    l.failed(TranscriptionListener.FailureReason.AUTHENTICATION_FAILED);
                }
            }
        }

        private String getLanguage()
        {
            String lang = participant.getTranslationLanguage();
            return lang != null ? lang : participant.getSourceLanguage();
        }
    }
}
