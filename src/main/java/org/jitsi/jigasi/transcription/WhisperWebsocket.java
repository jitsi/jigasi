package org.jitsi.jigasi.transcription;

import io.jsonwebtoken.*;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.*;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.jitsi.jigasi.JigasiBundleActivator;
import org.jitsi.utils.logging.Logger;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;


@WebSocket
public class WhisperWebsocket {

    private Session wsSession;

    private HashMap<String, Participant> participants = new HashMap<>();

    private HashMap<String, String> prevTranscriptions = new HashMap<>();

    private HashMap<String, Set<TranscriptionListener>> participantListeners = new HashMap<>();

    private static final int maxRetryAttempts = 10;


    /* Transcription language requested by the user who requested the transcription */
    public String transcriptionTag = "en-US";

    private final static Logger logger
            = Logger.getLogger(WhisperWebsocket.class);

    /**
     * The config key of the websocket to the speech-to-text service.
     */
    public final static String WEBSOCKET_URL
            = "org.jitsi.jigasi.transcription.whisper.websocket_url";

    /**
     * The config key for the JWT key name
     */
    public final static String PRIVATE_KEY_NAME
            = "org.jitsi.jigasi.transcription.whisper.private_key_name";

    /**
     * The base64 encoded private key used for signing
     */
    public final static String PRIVATE_KEY
            = "org.jitsi.jigasi.transcription.whisper.private_key";

    public final static String DEFAULT_WEBSOCKET_URL = "ws://livets-pilot.jitsi.net/ws/";

    /**
     * Message to send when closing the connection
     */
    private final static ByteBuffer EOF_MESSAGE = ByteBuffer.wrap(new byte[1]);

    /**
     * The config value of the websocket to the speech-to-text
     * service.
     */
    private String websocketUrlConfig;

    /**
     * The URL of the websocket to the speech-to-text service.
     */
    private String websocketUrl;

    /**
     * The Connection ID to the Whisper Service
     */
    private final String connectionId = UUID.randomUUID().toString();

    private String privateKey;

    private String privateKeyName;


    private String getJWT() throws NoSuchAlgorithmException, InvalidKeySpecException {
        long nowMillis = System.currentTimeMillis();
        Date now = new Date(nowMillis);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        PKCS8EncodedKeySpec keySpecPKCS8 = new PKCS8EncodedKeySpec(Base64.getDecoder().decode(privateKey));
        PrivateKey finalPrivateKey = kf.generatePrivate(keySpecPKCS8);
        JwtBuilder builder = Jwts.builder()
                .setHeaderParam("kid", privateKeyName)
                .setIssuedAt(now)
                .setIssuer("jigasi")
                .signWith(SignatureAlgorithm.RS256, finalPrivateKey);
        long expires = nowMillis + (60 * 60 * 1000);
        Date expiry = new Date(expires);
        builder.setExpiration(expiry);
        return builder.compact();
    }

    /**
     * Creates a connection url by concatenating the websocket
     * url with the Connection Id;
     */
    private void generateWebsocketUrl()
    {
        getConfig();
        try
        {
            websocketUrl = websocketUrlConfig + connectionId + "?auth_token=" + getJWT();
        }
        catch (Exception e)
        {
            logger.error("Failed generating JWT for Whisper. " + e);
        }
        logger.debug("Whisper URL: " + websocketUrl);
    }

    private void getConfig() {
        websocketUrlConfig = JigasiBundleActivator.getConfigurationService()
                .getString(WEBSOCKET_URL, DEFAULT_WEBSOCKET_URL);
        privateKey = JigasiBundleActivator.getConfigurationService()
                        .getString(PRIVATE_KEY, "");
        privateKeyName = JigasiBundleActivator.getConfigurationService()
                .getString(PRIVATE_KEY_NAME, "");
        logger.info("Websocket streaming endpoint: " + websocketUrlConfig);
    }

    /**
     * Connect to the websocket, retry up to maxRetryAttempts
     * with exponential backoff in case of failure
     */
    private void connect() throws Exception {
        int attempt = 0;
        float multiplier = 1.5f;
        long waitTime = 1000L;
        boolean isConnected = false;
        wsSession = null;
        while (attempt < maxRetryAttempts && !isConnected)
        {
            try
            {
                generateWebsocketUrl();
                logger.info("Connecting to " + websocketUrl);
                WebSocketClient ws = new WebSocketClient();
                ws.start();
                CompletableFuture<Session> connectFuture = ws.connect(this, new URI(websocketUrl));
                wsSession = connectFuture.get();
                wsSession.setIdleTimeout(Duration.ofSeconds(300));
                isConnected = true;
                logger.info("Successfully connected to " + websocketUrl);
                break;
            }
            catch (Exception e)
            {
                int remaining = maxRetryAttempts - attempt;
                waitTime *= multiplier;
                logger.error("Failed connecting to " + websocketUrl + ". Retrying in "
                        + waitTime/1000 + "seconds for another " + remaining + " times.");
                logger.error(e.toString());
            }
            attempt++;
            wait(waitTime);
        }

        if (!isConnected)
        {
            throw new Exception("Failed connecting to " + websocketUrl + ". Nothing to do.");
        }
    }


    WhisperWebsocket() throws Exception {
        connect();
    }

    @OnWebSocketClose
    public void onClose(int statusCode, String reason)
    {
        wsSession = null;
        prevTranscriptions = null;
        participants = null;
        participantListeners = null;
    }


    @OnWebSocketMessage
    public void onMessage(String msg)
    {
        boolean partial = true;
        String result = "";
        JSONObject obj = new JSONObject(msg);
        String msgType = obj.getString("type");
        String participantId = obj.getString("participant_id");
        Participant participant = participants.get(participantId);
        if (msgType.equals("final"))
        {
            partial = false;
        }

        result = obj.getString("text");
        UUID id = UUID.fromString(obj.getString("id"));
        Instant transcriptionStart = Instant.ofEpochMilli(obj.getLong("ts"));
        float stability = obj.getFloat("variance");

        logger.debug("Received final: " + result);
        Set<TranscriptionListener> partListeners = participantListeners.getOrDefault(participantId, null);
        if (!result.isEmpty() && partListeners != null)
        {
            int i=0;

            for (TranscriptionListener l : partListeners)
            {
                i++;
                logger.debug("ParticipantId: " + i + ", " + participantId);
                logger.debug("TranscriptionListener: " + l.toString());
                TranscriptionResult tsResult = new TranscriptionResult(
                        participant,
                        id,
                        transcriptionStart,
                        partial,
                        getLanguage(participant),
                        stability,
                        new TranscriptionAlternative(result));
                l.notify(tsResult);
            }
        }
    }



    @OnWebSocketError
    public void onError(Throwable cause)
    {
        logger.error("Error while streaming audio data to transcription service.", cause);
//        try
//        {
//            this.connectionId = UUID.randomUUID().toString();
//            connect();
//        }
//        catch (Exception e)
//        {
//            logger.error("Websocket connection failure");
//        }
    }

    private String getLanguage(Participant participant) {
        String lang = participant.getTranslationLanguage();
        logger.debug("Translation language is " + lang);
        if (lang == null)
        {
            lang = participant.getSourceLanguage();
        }
        logger.debug("Returned language is " + lang);
        return lang;
    }

    private ByteBuffer buildPayload(Participant participant, ByteBuffer audio) {
        ByteBuffer header = ByteBuffer.allocate(60);
        int lenAudio = audio.remaining();
        ByteBuffer fullPayload = ByteBuffer.allocate(lenAudio + 60);
        String headerStr = participant.getDebugName() + "|" + this.getLanguage(participant);
        header.put(headerStr.getBytes()).rewind();
        fullPayload.put(header).put(audio).rewind();
        return fullPayload;
    }

    public boolean disconnectParticipant(String participantId) throws IOException {
        if (participants.containsKey(participantId))
        {
            participants.remove(participantId);
            participantListeners.remove(participantId);
            prevTranscriptions.remove(participantId);
            logger.info("Disconnected " + participantId);
        }

        if (participants.isEmpty())
        {
            logger.info("All participants have left, disconnecting from Whisper transcription server.");
            wsSession.getRemote().sendBytes(EOF_MESSAGE);
            wsSession.disconnect();
            return true;
        }
        return false;
    }

    public void sendAudio(Participant participant, ByteBuffer audio) {
        String participantId = participant.getDebugName();
        addParticipantIfNotExists(participant);
        try
        {
            logger.debug("Sending audio for " + participantId);
            wsSession.getRemote().sendBytes(buildPayload(participant, audio));
        }
        catch (NullPointerException e)
        {
            logger.error("Failed sending audio for " + participantId + ". " + e);
            if (!wsSession.isOpen())
            {
                try
                {
                    connect();
                }
                catch (Exception ex)
                {
                    logger.error(ex);
                }
            }
        }
        catch (IOException e)
        {
            logger.error("Failed sending audio for " + participantId + ". " + e);
        }
    }

    private void addParticipantIfNotExists(Participant participant) {
            String participantId = participant.getDebugName();
            if (!participants.containsKey(participantId))
            {
                participants.put(participantId, participant);
                participantListeners.put(participantId, new HashSet<>());
                prevTranscriptions.put(participantId, "");
            }
    }

    public void addListener(TranscriptionListener listener, Participant participant) {
        addParticipantIfNotExists(participant);
        participantListeners.get(participant.getDebugName()).add(listener);
    }

    public void setTranscriptionTag(String tsTag) {
        transcriptionTag = tsTag;
    }

    public Session getWsSession() {
        return wsSession;
    }
}
