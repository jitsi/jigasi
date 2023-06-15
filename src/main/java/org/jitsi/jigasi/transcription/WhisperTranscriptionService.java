package org.jitsi.jigasi.transcription;

import org.apache.http.HttpHeaders;
import org.eclipse.jetty.websocket.api.*;
import org.eclipse.jetty.websocket.api.annotations.*;
import org.eclipse.jetty.websocket.client.*;
import org.jitsi.impl.neomedia.device.AudioMixerMediaDevice;
import org.jitsi.impl.neomedia.device.ReceiveStreamBufferListener;
import org.json.*;
import org.jitsi.jigasi.*;
import org.jitsi.utils.logging.*;

import javax.media.format.*;
import java.io.*;
import java.net.*;
import java.nio.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;


/**
 * Implements a TranscriptionService which uses
 * Whisper websocket transcription service.
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

    /**
     * The config key of the websocket to the speech-to-text service.
     */
    public final static String WEBSOCKET_URL
            = "org.jitsi.jigasi.transcription.whisper.websocket_url";

    /**
     * The config key of the websocket url for single requests
     */
    public final static String WEBSOCKET_URL_SINGLE_REQ
            = "org.jitsi.jigasi.transcription.whisper.websocket_url_single";

    /**
     * The config key for http auth user
     */
    public final static String WEBSOCKET_HTTP_AUTH_USER
            = "org.jitsi.jigasi.transcription.whisper.websocket_auth_user";

    /**
     * The config key for http auth password
     */
    public final static String WEBSOCKET_HTTP_AUTH_PASS
            = "org.jitsi.jigasi.transcription.whisper.websocket_auth_password";

    public final static String DEFAULT_WEBSOCKET_URL = "ws://livets-pilot.jitsi.net/ws/";

    public final static String DEFAULT_WEBSOCKET_SINGLE_REQ_URL = "ws://livets-pilot.jitsi.net/ws-single/";

    private final static ByteBuffer EOF_MESSAGE = ByteBuffer.wrap(new byte[1]);

    private Boolean hasHttpAuth = false;

    private String httpAuthUser;

    private String httpAuthPass;

    /**
     * The config value of the websocket to the speech-to-text service.
     */
    private String websocketUrlConfig;

    /**
     * The config value of the websocket url for a single request
     */
    private String websocketSingleUrlConfig;

    /**
     * The URL of the websocket to the speech-to-text service.
     */
    private String websocketUrl;

    /**
     * The URL of the websocket for single calls
     */
    private String websocketUrlSingle;

    /**
     * Assigns the websocketUrl to use to websocketUrl by reading websocketUrlConfig;
     */
    private void generateWebsocketUrl(Participant participant)
            throws org.json.simple.parser.ParseException
    {
        String lang = participant.getSourceLanguage() != null ? participant.getSourceLanguage() : "en";
        websocketUrl = websocketUrlConfig + participant.getId() + "?lang=" + lang;
        websocketUrlSingle = websocketSingleUrlConfig + participant.getId() + "?lang=" + lang;
        logger.info("Whisper URL: " + websocketUrl);
    }

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
     * Create a TranscriptionService which will send audio to the Whisper service
     * platform to get a transcription
     */
    public WhisperTranscriptionService()
    {
        websocketUrlConfig = JigasiBundleActivator.getConfigurationService()
                .getString(WEBSOCKET_URL, DEFAULT_WEBSOCKET_URL);
        websocketSingleUrlConfig = JigasiBundleActivator.getConfigurationService()
                .getString(WEBSOCKET_URL_SINGLE_REQ, DEFAULT_WEBSOCKET_SINGLE_REQ_URL);
        httpAuthPass = JigasiBundleActivator.getConfigurationService()
                .getString(WEBSOCKET_HTTP_AUTH_PASS, "");
        httpAuthUser = JigasiBundleActivator.getConfigurationService()
                .getString(WEBSOCKET_HTTP_AUTH_USER, "");
        if (!httpAuthPass.isBlank() && !httpAuthUser.isBlank()) {
            hasHttpAuth = true;
        }
        logger.info("Websocket streaming endpoint: " + websocketUrlConfig);
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

    private String lastResult = "";

    /**
     * Sends audio as an array of bytes to the Whisper service
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
        logger.info("Single request");
        try
        {
            // Set the sampling rate and encoding of the audio
            AudioFormat format = request.getFormat();
            logger.info("Sample rate: " + format.getSampleRate());
            if (!format.getEncoding().equals("LINEAR"))
            {
                throw new IllegalArgumentException("Given AudioFormat" +
                        "has unexpected" +
                        "encoding");
            }
            WebSocketClient ws = new WebSocketClient();
            ws.setIdleTimeout(java.time.Duration.ofSeconds(-1));
            WhisperWebsocketSession socket = new WhisperWebsocketSession(request);
            ws.start();
            if (hasHttpAuth) {
                logger.info("HTTP Auth Enabled");
                final ClientUpgradeRequest upgReq = new ClientUpgradeRequest();
                String encoded = Base64.getEncoder().encodeToString((httpAuthUser + ":" + httpAuthPass).getBytes());
                upgReq.setHeader(HttpHeaders.AUTHORIZATION, "Basic " + encoded);
                ws.connect(socket, new URI(websocketUrlSingle), upgReq);
            } else {
                ws.connect(socket, new URI(websocketUrlSingle));
            }
            socket.awaitClose();
            String msg = socket.getResult();

            String result = "";
            JSONObject obj = new JSONObject(msg);

            result = obj.getString("text").strip();
            UUID id = UUID.fromString(obj.getString("id"));
            Instant transcriptionStart = Instant.now();

            if (!result.isEmpty() && !result.equals(lastResult))
            {
                lastResult = result;
                resultConsumer.accept(
                        new TranscriptionResult(
                                null,
                                id,
                                transcriptionStart,
                                false,
                                request.getLocale().toLanguageTag(),
                                1.0,
                                new TranscriptionAlternative(result)));
            }
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
    @WebSocket
    public class WhisperWebsocketStreamingSession
            implements StreamingRecognitionSession
    {

        private Participant participant;

        private Session session;
        /* The name of the participant */
        private final String debugName;
        /* The sample rate of the audio stream we collect from the first request */
        private double sampleRate = -1.0;
        /* Last returned result, so we do not return the same string twice */
        private String lastResult = "";
        /* Transcription language requested by the user who requested the transcription */
        private String transcriptionTag = "en-US";

        /**
         * List of TranscriptionListeners which will be notified when a
         * result comes in
         */
        private final List<TranscriptionListener> listeners = new ArrayList<>();


        WhisperWebsocketStreamingSession(String debugName, Participant participant)
                throws Exception
        {
            this.debugName = debugName;
            this.participant = participant;
            logger.info("Connecting to " + websocketUrl);
            WebSocketClient ws = new WebSocketClient();
            ws.start();
            if (hasHttpAuth) {
                final ClientUpgradeRequest upgReq = new ClientUpgradeRequest();
                String encoded = Base64.getEncoder().encodeToString((httpAuthUser + ":" + httpAuthPass).getBytes());
                upgReq.setHeader(HttpHeaders.AUTHORIZATION, "Basic " + encoded);
                ws.connect(this, new URI(websocketUrl), upgReq);
            } else {
                ws.connect(this, new URI(websocketUrl));
            }
        }

        @OnWebSocketClose
        public void onClose(int statusCode, String reason)
        {
            this.session = null;
        }

        @OnWebSocketConnect
        public void onConnect(Session session)
        {
            session.setIdleTimeout(Duration.ofSeconds(300));
            this.session = session;
        }

        @OnWebSocketMessage
        public void onMessage(String msg)
        {
            boolean partial = true;
            String result = "";
            JSONObject obj = new JSONObject(msg);
            String msgType = obj.getString("type");
            if (msgType.equals("final")) {
                partial = false;
            }

            result = obj.getString("text");
            UUID id = UUID.fromString(obj.getString("id"));
            Instant transcriptionStart = Instant.ofEpochMilli(obj.getLong("ts"));
            float stability = obj.getFloat("variance");

            if (!result.isEmpty() && !result.equals(lastResult))
            {
                lastResult = result;
                for (TranscriptionListener l : listeners)
                {
                    l.notify(new TranscriptionResult(
                            participant,
                            id,
                            transcriptionStart,
                            partial,
                            transcriptionTag,
                            stability,
                            new TranscriptionAlternative(result)));
                }
            }
        }

        @OnWebSocketError
        public void onError(Throwable cause)
        {
            logger.error("Samplerate error: " + sampleRate);
            logger.error("Error while streaming audio data to transcription service" , cause);
        }

        public void sendRequest(TranscriptionRequest request)
        {
            try
            {
                if (sampleRate < 0)
                {
                    sampleRate = request.getFormat().getSampleRate();
                }
                ByteBuffer audioBuffer = ByteBuffer.wrap(request.getAudio());
                session.getRemote().sendBytes(audioBuffer);
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
                session.getRemote().sendBytes(EOF_MESSAGE);
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
    public class WhisperWebsocketSession
    {
        /* Signal for the end of operation */
        private final CountDownLatch closeLatch;

        /* Request we need to process */
        private final TranscriptionRequest request;

        /* Collect results*/
        private StringBuilder result;

        WhisperWebsocketSession(TranscriptionRequest request)
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
                ByteBuffer audioBuffer = ByteBuffer.wrap(request.getAudio());
                session.getRemote().sendBytes(audioBuffer);
            }
            catch (IOException e)
            {
                logger.error("Error while transcribing audio", e);
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