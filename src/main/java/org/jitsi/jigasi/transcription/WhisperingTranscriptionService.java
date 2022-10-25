/*
 * Jigasi, the Jitsi Gateway to SIP.
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

import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.*;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.jitsi.utils.logging.Logger;
import org.json.JSONObject;
import org.json.simple.JSONArray;

import javax.media.format.AudioFormat;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;


/**
 * Implements a TranscriptionService which uses
 * Whispering, a streaming websocket transcription
 * service, based on OpenAI's Whisper.
 * <p>
 * See https://github.com/shirayu/whispering for
 * more information about the project.
 *
 * @author Charles Zablit
 */
public class WhisperingTranscriptionService
        implements TranscriptionService {

    /**
     * The logger for this class.
     */
    private final static Logger logger
            = Logger.getLogger(WhisperingTranscriptionService.class);

    /**
     * The URL of the websocket service speech-to-text service.
     */
    public final static String WEBSOCKET_URL
            = "org.jitsi.jigasi.transcription.whispering.websocket_url";

    //public final static String DEFAULT_WEBSOCKET_URL = "ws://192.168.43.152:8000";
    public final static String DEFAULT_WEBSOCKET_URL = "ws://152.228.167.183:8000";

    private final static String EOF_MESSAGE = "{\"eof\" : 1}";

    private final String websocketUrl;

    /**
     * Create a TranscriptionService which will send audio to Whispering
     * to get a transcription.
     */
    public WhisperingTranscriptionService() {
        websocketUrl = DEFAULT_WEBSOCKET_URL;
    }

    /**
     * No configuration required yet.
     */
    public boolean isConfiguredProperly() {
        return true;
    }

    /**
     * Sends audio as an array of bytes to Whispering.
     *
     * @param request        the TranscriptionRequest which holds the audio to be sent.
     * @param resultConsumer a Consumer which will handle the TranscriptionResult.
     */
    @Override
    public void sendSingleRequest(final TranscriptionRequest request,
                                  final Consumer<TranscriptionResult> resultConsumer) {
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

            WebSocketClient ws = new WebSocketClient();
            WhisperingWebsocketSession socket = new WhisperingWebsocketSession(request);
            ws.start();
            ws.connect(socket, new URI(websocketUrl));
            socket.awaitClose();
            resultConsumer.accept(
                    new TranscriptionResult(
                            null,
                            UUID.randomUUID(),
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
            throws UnsupportedOperationException {
        try
        {
            WhisperingWebsocketStreamingSession streamingSession = new WhisperingWebsocketStreamingSession(
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
    public boolean supportsFragmentTranscription() {
        return true;
    }

    @Override
    public boolean supportsStreamRecognition() {
        return true;
    }

    /**
     * A Transcription session for transcribing streams, handles
     * the lifecycle of websocket
     */
    @WebSocket(maxBinaryMessageSize = 1024 * 1024 * 1024)
    public class WhisperingWebsocketStreamingSession
            implements StreamingRecognitionSession {
        private Session session;
        /* The name of the participant */
        private final String debugName;
        /* Transcription language requested by the user who requested the transcription */
        private String transcriptionTag = "en-US";

        /**
         * List of TranscriptionListeners which will be notified when a
         * result comes in
         */
        private final List<TranscriptionListener> listeners = new ArrayList<>();

        WhisperingWebsocketStreamingSession(String debugName)
                throws Exception {
            logger.info("STARTING WHISPERING WEBSOCKET.");
            this.debugName = debugName;
            WebSocketClient ws = new WebSocketClient();
            ws.start();
            ws.connect(this, new URI(websocketUrl));
        }

        @OnWebSocketClose
        public void onClose(int statusCode, String reason) {
            logger.info("CLOSED WHISPERING WEBSOCKET.");
            this.session = null;
        }

        @OnWebSocketConnect
        public void onConnect(Session session) {
            logger.info("CONNECTED TO WHISPERING WEBSOCKET.");
            this.session = session;
            try
            {
                WhisperingContext ctx = new WhisperingContext(0.0);
                session.getRemote().sendString(ctx.toJSON().toString());
            }
            catch (Exception e)
            {
                logger.error("Error while sending context to Whispering server " + debugName, e);
            }
        }

        @OnWebSocketMessage
        public void onMessage(String msg) {
            if (logger.isDebugEnabled())
                logger.debug(debugName + "Received response: " + msg);

            JSONObject jsonData = new JSONObject(msg);
            logger.info("YOU'VE GOT MAIL " + jsonData.optString("text", "not working"));
            for (TranscriptionListener l : listeners) {
                l.notify(new TranscriptionResult(
                        null,
                        UUID.randomUUID(),
                        false,
                        transcriptionTag,
                        1.0,
                        new TranscriptionAlternative(jsonData.optString("text", "not working"))));
            }
        }

        @OnWebSocketError
        public void onError(Throwable cause) {
            logger.error("Error while streaming audio data to transcription service", cause);
        }

        public void sendRequest(TranscriptionRequest request) {
            logger.info("SENDING REQUEST");
            logger.info(request.getFormat().getSampleRate());
            logger.info(request.getDurationInMs());
            try
            {
                //if (sampleRate < 0)
                //{
                //    sampleRate = request.getFormat().getSampleRate();
                //    session.getRemote().sendString("{\"config\" : {\"sample_rate\" : " + sampleRate + " }}");
                //}
                ByteBuffer audioBuffer = ByteBuffer.wrap(request.getAudio());
                session.getRemote().sendBytes(audioBuffer);
            }
            catch (Exception e)
            {
                logger.error("Error to send websocket request for participant " + debugName, e);
            }
        }

        public void addTranscriptionListener(TranscriptionListener listener) {
            listeners.add(listener);
        }

        public void end() {
            try
            {
                session.getRemote().sendString(EOF_MESSAGE);
            }
            catch (Exception e)
            {
                logger.error("Error to finalize websocket connection for participant " + debugName, e);
            }
        }

        public boolean ended() {
            return session == null;
        }
    }

    /**
     * Session to send websocket data and receive results. Non-streaming version
     */
    @WebSocket
    public class WhisperingWebsocketSession {
        /* Signal for the end of operation */
        private final CountDownLatch closeLatch;

        /* Request we need to process */
        private final TranscriptionRequest request;

        /* Collect results*/
        private StringBuilder result;

        WhisperingWebsocketSession(TranscriptionRequest request) {
            this.closeLatch = new CountDownLatch(1);
            this.request = request;
            this.result = new StringBuilder();
        }

        @OnWebSocketClose
        public void onClose(int statusCode, String reason) {
            this.closeLatch.countDown(); // trigger latch
        }

        @OnWebSocketConnect
        public void onConnect(Session session) {
            try
            {
                AudioFormat format = request.getFormat();
                WhisperingContext ctx = new WhisperingContext(0.0);
                session.getRemote().sendString(ctx.toJSON().toString());
                ByteBuffer audioBuffer = ByteBuffer.wrap(request.getAudio());
                session.getRemote().sendBytes(audioBuffer);
                session.getRemote().sendString(EOF_MESSAGE);
            }
            catch (IOException e)
            {
                logger.error("Error to transcribe audio", e);
            }
        }

        @OnWebSocketMessage
        public void onMessage(String msg) {
            result.append(msg);
            result.append('\n');
        }

        @OnWebSocketError
        public void onError(Throwable cause) {
            logger.error("Websocket connection error", cause);
        }

        public String getResult() {
            return result.toString();
        }

        void awaitClose()
                throws InterruptedException {
            closeLatch.await();
        }
    }

    /**
     * Represent the Whispering Context to instantiate the transcription
     * service.
     */
    public class WhisperingContext {
        /* Starting timestamp of the transcription service. */
        double timestamp;

        WhisperingContext(double timestamp) {
            this.timestamp = timestamp;
        }

        public JSONObject toJSON() {
            JSONObject ctx = new JSONObject();
            ctx.put("timestamp", this.timestamp);
            ctx.put("buffer_tokens", new JSONArray());
            ctx.put("buffer_mel", JSONObject.NULL);
            ctx.put("vad", true);
            JSONArray temperatures = new JSONArray();
            for (int i = 0; i <= 10; i += 2) {
                temperatures.add(i / 10);
            }
            ctx.put("temperatures", temperatures);
            ctx.put("allow_padding", true);
            ctx.put("patience", JSONObject.NULL);
            ctx.put("compression_ratio_threshold", 2.4);
            ctx.put("logprob_threshold", -1.0);
            ctx.put("no_captions_threshold", 0.6);
            ctx.put("best_of", 5);
            ctx.put("beam_size", 5);
            ctx.put("no_speech_threshold", 0.6);
            ctx.put("buffer_threshold", 0.5);
            ctx.put("vad_threshold", 0.5);
            ctx.put("data_type", "int64");

            JSONObject res = new JSONObject();
            res.put("context", ctx);

            return res;
        }

        public String toString() {
            return this.toJSON().toString();
        }
    }
}
