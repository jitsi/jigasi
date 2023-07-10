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
    {
        String lang = participant.getSourceLanguage() != null ? participant.getSourceLanguage() : "en";
        websocketUrl = websocketUrlConfig + UUID.randomUUID() + "?lang=" + lang;
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
        if (!httpAuthPass.isBlank() && !httpAuthUser.isBlank())
        {
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
        return false;
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
        logger.warn("The Whisper transcription service does not support single requests.");
//        // Try to create the client, which can throw an IOException
//        logger.info("Single request");
//        try
//        {
//            // Set the sampling rate and encoding of the audio
//            AudioFormat format = request.getFormat();
//            logger.info("Sample rate: " + format.getSampleRate());
//            if (!format.getEncoding().equals("LINEAR"))
//            {
//                throw new IllegalArgumentException("Given AudioFormat" +
//                        "has unexpected" +
//                        "encoding");
//            }
//            WebSocketClient ws = new WebSocketClient();
//            ws.setIdleTimeout(java.time.Duration.ofSeconds(-1));
//            WhisperWebsocketSession socket = new WhisperWebsocketSession(request);
//            ws.start();
//            if (hasHttpAuth)
//            {
//                logger.info("HTTP Auth Enabled");
//                final ClientUpgradeRequest upgReq = new ClientUpgradeRequest();
//                String encoded = Base64.getEncoder().encodeToString((httpAuthUser + ":" + httpAuthPass).getBytes());
//                upgReq.setHeader(HttpHeaders.AUTHORIZATION, "Basic " + encoded);
//                ws.connect(socket, new URI(websocketUrlSingle), upgReq);
//            }
//            else
//            {
//                ws.connect(socket, new URI(websocketUrlSingle));
//            }
//            socket.awaitClose();
//            String msg = socket.getResult();
//
//            String result = "";
//            JSONObject obj = new JSONObject(msg);
//
//            result = obj.getString("text").strip();
//            UUID id = UUID.fromString(obj.getString("id"));
//            Instant transcriptionStart = Instant.now();
//
//            if (!result.isEmpty() && !result.equals(lastResult))
//            {
//                lastResult = result;
//                resultConsumer.accept(
//                        new TranscriptionResult(
//                                null,
//                                id,
//                                transcriptionStart,
//                                false,
//                                request.getLocale().toLanguageTag(),
//                                1.0,
//                                new TranscriptionAlternative(result)));
//            }
//        }
//        catch (Exception e)
//        {
//            logger.error("Error sending single req", e);
//        }
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
//    @WebSocket
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

        private WhisperWebsocket wsClient;

        private String roomId = "";

        private WhisperConnectionPoolSingleton connectionPool = null;


        WhisperWebsocketStreamingSession(String debugName, Participant participant)
                throws Exception
        {
            this.connectionPool = WhisperConnectionPoolSingleton.getInstance();
            this.roomId = participant.getChatMember().getChatRoom().toString();
            this.debugName = debugName;
            this.participant = participant;
            this.wsClient = connectionPool.getConnection(this.roomId, participant);
            this.session = wsClient.getSession();
            this.wsClient.setTranscriptionTag(this.transcriptionTag);
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
                wsClient.sendAudio(participant, audioBuffer);
            }
            catch (Exception e)
            {
                logger.error("Error to send websocket request for participant " + debugName, e);
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
                logger.info("Disconnecting " + this.debugName + " from Whisper transcription service");
                this.connectionPool.end(this.roomId, this.debugName);
                this.session = null;
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


}