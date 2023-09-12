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

import org.eclipse.jetty.websocket.api.*;
import org.eclipse.jetty.websocket.api.annotations.*;
import org.eclipse.jetty.websocket.client.*;
import org.json.*;
import org.jitsi.jigasi.*;
import org.jitsi.utils.logging.*;

import javax.media.format.*;
import java.io.*;
import java.net.*;
import java.nio.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import com.google.gson.Gson;


/**
 * Implements a TranscriptionService which uses local
 * Gladia websocket transcription service.
 * <p>
 * See https://github.com/alphacep/Gladia-server for
 * information about server
 *
 * @author Nik Vaessen
 * @author Damian Minkov
 * @author Nickolay V. Shmyrev
 */
public class GladiaTranscriptionService
    implements TranscriptionService
{

    /**
     * The logger for this class
     */
    private final static Logger logger
            = Logger.getLogger(GladiaTranscriptionService.class);

    private static Gson gson = new Gson();

    public final static String EMPTY_STRING = "";

    public final static String TRANSCRIPTION_KEY = "transcription";

    public final static String TYPE_KEY = "type";

    public final static String FINAL_KEY = "final";

    /**
     * The config key of the websocket to the speech-to-text service.
     */
    public final static String WEBSOCKET_URL
            = "org.jitsi.jigasi.transcription.gladia.websocket_url";
    
    private final static String X_GLADIA_KEY = "org.jitsi.jigasi.transcription.gladia.api_key";


    public final static String DEFAULT_WEBSOCKET_URL = "wss://api.gladia.io/audio/text/audio-transcription";

    private final static String EOF_MESSAGE = "{\"eof\" : 1}";

    /**
     * The config value of the websocket to the speech-to-text service.
     */
    private String websocketUrlConfig;

    /**
     * The URL of the websocket to the speech-to-text service.
     */
    private String websocketUrl;

    private String apiKey;
    /**
     * Assigns the websocketUrl to use to websocketUrl by reading websocketUrlConfig;
     */
    private void generateWebsocketUrl(Participant participant)
        throws org.json.simple.parser.ParseException
    {
        websocketUrl = websocketUrlConfig;
            return;
    }

    /**
     * Create a TranscriptionService which will send audio to the Gladia service
     * platform to get a transcription
     */
    public GladiaTranscriptionService()
    {
        websocketUrlConfig = JigasiBundleActivator.getConfigurationService()
                .getString(WEBSOCKET_URL, DEFAULT_WEBSOCKET_URL);
        logger.info("" + websocketUrlConfig);
        apiKey= JigasiBundleActivator.getConfigurationService()
        .getString(X_GLADIA_KEY);
    }

    /**
     * No configuration required yet
     */
    public boolean isConfiguredProperly()
    {
        return true;
    }

    /**
     * If the websocket url is a JSON, language routing is supported
     */
    public boolean supportsLanguageRouting()
    {
        return websocketUrlConfig.trim().startsWith("{");
    }

    /**
     * Sends audio as an array of bytes to Gladia service
     *
     * @param request        the TranscriptionRequest which holds the audio to be sent
     * @param resultConsumer a Consumer which will handle the
     *                       TranscriptionResult
     */
    @Override
    public void sendSingleRequest(final TranscriptionRequest request,
                                  final Consumer<TranscriptionResult> resultConsumer)
    {
        // Try to create the client, which can throw an IOException
        try
        {
            // Set the sampling rate and encoding of the audio
            AudioFormat format = request.getFormat();
            if (!format.getEncoding().equals("LINEAR"))
            {
                throw new IllegalArgumentException("Given AudioFormat" +
                        "has unexpected" +
                        "encoding");
            }
            Instant timeRequestReceived = Instant.now();

            WebSocketClient ws = new WebSocketClient();
            GladiaWebsocketSession socket = new GladiaWebsocketSession(request);
            ws.start();
            ws.connect(socket, new URI(websocketUrl));
            socket.awaitClose();
            resultConsumer.accept(
                    new TranscriptionResult(
                            null,
                            UUID.randomUUID(),
                            timeRequestReceived,
                            false,
                            request.getLocale().toLanguageTag(),
                            0,
                            new TranscriptionAlternative(socket.getResult())));
        }
        catch (Exception e)
        {
            logger.error("Error sending single req", e);
        }
    }

    @Override
    public StreamingRecognitionSession initStreamingSession(Participant participant)
        throws UnsupportedOperationException
    {
        try
        {
            generateWebsocketUrl(participant);
            GladiaWebsocketStreamingSession streamingSession = new GladiaWebsocketStreamingSession(
                    participant.getDebugName());
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
    @WebSocket
    public class GladiaWebsocketStreamingSession
        implements StreamingRecognitionSession
    {
        private Session session;
        /* The name of the participant */
        private final String debugName;
        /* The sample rate of the audio stream we collect from the first request */
        private Integer sampleRate = -1;
        /* Last returned result so we do not return the same string twice */
        private String lastTranscription = "";
        /* Transcription language requested by the user who requested the transcription */
        private String transcriptionTag = "en-US";

        /**
         * List of TranscriptionListeners which will be notified when a
         * result comes in
         */
        private final List<TranscriptionListener> listeners = new ArrayList<>();

        /**
         *  Latest assigned UUID to a transcription result.
         *  A new one has to be generated whenever a definitive result is received.
         */
        private UUID uuid = UUID.randomUUID();

        GladiaWebsocketStreamingSession(String debugName)
            throws Exception
        {
            this.debugName = debugName;
            WebSocketClient ws = new WebSocketClient();
            ws.start();
            ws.connect(this, new URI(websocketUrl));
        }

        @OnWebSocketClose
        public void onClose(int statusCode, String reason)
        {
            this.session = null;
        }

        @OnWebSocketConnect
        public void onConnect(Session session)
        {
            this.session = session;
        }

        @OnWebSocketMessage
        public void onMessage(String msg)
        {
            // log json message 
            logger.info(debugName + "on message: " + msg);

            // create the json object
            JSONObject obj = new JSONObject(msg);

            // retrieve the transcription of the utterance
            boolean hasTranscription = obj.has(TRANSCRIPTION_KEY);
            String transcription = null;
            if (hasTranscription)
            {
                transcription = obj.getString(TRANSCRIPTION_KEY);
            } 


            // retrieve the type of utterance
            Boolean partial = true;
            boolean hasType = obj.has(TYPE_KEY);
            if (hasType && obj.getString(TYPE_KEY) == FINAL_KEY)
            {
                partial = false;
            } 
            
            // notify the listeners
            if (transcription != null && (!partial || !transcription.equals(lastTranscription)))
            {
                logger.info("transcription: " + transcription);
                lastTranscription = transcription;
                for (TranscriptionListener l : listeners)
                {
                    l.notify(new TranscriptionResult(
                            null,
                            uuid,
                            // this time needs to be the one when the audio was sent
                            // the results need to be matched with the time when we sent the audio, so we have
                            // the real time when this transcription was started
                            Instant.now(),
                            partial,
                            transcriptionTag,
                            1.0,
                            new TranscriptionAlternative(transcription)));
                }
            }

            // if final, renew the id
            if (!partial)
            {
                this.uuid = UUID.randomUUID();
            }
        }

        @OnWebSocketError
        public void onError(Throwable cause)
        {
            logger.error("Error while streaming audio data to transcription service" , cause);
        }

        public void sendRequest(TranscriptionRequest request)
        {
            try
            {
                // actual code that sends the audio data
                if (sampleRate < 0)
                {
                    sampleRate = (int) request.getFormat().getSampleRate();
                }
                ByteBuffer audioBuffer = ByteBuffer.wrap(request.getAudio());
                String encodedAudioData = Base64.getEncoder().encodeToString(audioBuffer.array());
                Map<String, Object> message = new HashMap<>();
                message.put("x_gladia_key", apiKey);
                message.put("sample_rate", sampleRate);
                message.put("frames", encodedAudioData);
                message.put("reinject_context", "true");
                session.getRemote().sendString(gson.toJson(message));
            }
            catch (Exception e)
            {
                logger.error("Error to send websocket request for participant " + debugName, e);
            }
        }

        public void addTranscriptionListener(TranscriptionListener listener)
        {
            listeners.add(listener);
        }

        public void end()
        {
            try
            {
                session.getRemote().sendString(EOF_MESSAGE);
            }
            catch (Exception e)
            {
                logger.error("Error to finalize websocket connection for participant " + debugName, e);
            }
        }

        public boolean ended()
        {
            return session == null;
        }
    }

    /**
     * Session to send websocket data and recieve results. Non-streaming version
     */
    @WebSocket
    public class GladiaWebsocketSession
    {
        /* Signal for the end of operation */
        private final CountDownLatch closeLatch;

        /* Request we need to process */
        private final TranscriptionRequest request;

        /* Collect results*/
        private StringBuilder result;

        GladiaWebsocketSession(TranscriptionRequest request)
        {
            this.closeLatch = new CountDownLatch(1);
            this.request = request;
            this.result = new StringBuilder();
        }

        @OnWebSocketClose
        public void onClose(int statusCode, String reason)
        {
            this.closeLatch.countDown(); // trigger latch
        }

        @OnWebSocketConnect
        public void onConnect(Session session)
        {
            try
            {
                AudioFormat format = request.getFormat();
                ByteBuffer audioBuffer = ByteBuffer.wrap(request.getAudio());
                String encodedAudioData = Base64.getEncoder().encodeToString(audioBuffer.array());
                Map<String, Object> message = new HashMap<>();
                message.put("x_gladia_key", apiKey);
                message.put("sample_rate", (int) format.getSampleRate());
                message.put("frames", encodedAudioData);
                
                session.getRemote().sendString(gson.toJson(message));
            }
            catch (IOException e)
            {
                logger.error("Error to transcribe audio", e);
            }
        }

        @OnWebSocketMessage
        public void onMessage(String msg)
        {
            result.append(msg);
            result.append('\n');
        }

        @OnWebSocketError
        public void onError(Throwable cause)
        {
            logger.error("Websocket connection error", cause);
        }

        public String getResult()
        {
            return result.toString();
        }

        void awaitClose()
            throws InterruptedException
        {
            closeLatch.await();
        }
    }

}
