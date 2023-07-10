package org.jitsi.jigasi.transcription;

import org.apache.http.HttpHeaders;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.*;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.jitsi.jigasi.JigasiBundleActivator;
import org.jitsi.utils.logging.Logger;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * A Transcription session for transcribing streams, handles
 * the lifecycle of websocket
 */
@WebSocket
public class WhisperWebsocketSingleton {

    private Session session;

    private HashMap<String, Participant> participants = new HashMap<>();

    private HashMap<String, String> prevTranscriptions = new HashMap<>();

    private HashMap<String, Set<TranscriptionListener>> participantListeners = new HashMap<>();

    private static final int maxRetryAttempts = 10;


    /* Transcription language requested by the user who requested the transcription */
    public String transcriptionTag = "en-US";

    private final static Logger logger
            = Logger.getLogger(WhisperWebsocketSingleton.class);

    /**
     * The config key of the websocket to the speech-to-text service.
     */
    public final static String WEBSOCKET_URL
            = "org.jitsi.jigasi.transcription.whisper.websocket_url";

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

    /**
     * Message to send when closing the connection
     */
    private final static ByteBuffer EOF_MESSAGE = ByteBuffer.wrap(new byte[1]);

    private Boolean hasHttpAuth = false;

    private String httpAuthUser;

    private String httpAuthPass;

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
    private String connectionId = UUID.randomUUID().toString();

    /**
     * The instance returned by the Singleton
     */
    private static WhisperWebsocketSingleton instance;

    /**
     * Creates a connection url by concatenating the websocket
     * url with the Connection Id;
     */
    private void generateWebsocketUrl()
    {
        getConfig();
        this.websocketUrl = websocketUrlConfig + connectionId;
        logger.debug("Whisper URL: " + websocketUrl);
    }

    private void getConfig() {
        websocketUrlConfig = JigasiBundleActivator.getConfigurationService()
                .getString(WEBSOCKET_URL, DEFAULT_WEBSOCKET_URL);
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
     * Connect to the websocket, retry up to maxRetryAttempts
     * with exponential backoff in case of failure
     */
    private void connect() throws Exception {
        int attempt = 0;
        float multiplier = 1.5f;
        long waitTime = 1000L;
        boolean isConnected = false;
        this.session = null;
        while (attempt < maxRetryAttempts && !isConnected)
        {
            try
            {
                generateWebsocketUrl();
                logger.info("Connecting to " + websocketUrl);
                WebSocketClient ws = new WebSocketClient();
                ws.start();
                if (hasHttpAuth)
                {
                    final ClientUpgradeRequest upgReq = new ClientUpgradeRequest();
                    String encoded = Base64.getEncoder().encodeToString((httpAuthUser + ":" + httpAuthPass).getBytes());
                    upgReq.setHeader(HttpHeaders.AUTHORIZATION, "Basic " + encoded);
                    ws.connect(this, new URI(websocketUrl), upgReq);
                }
                else
                {
                    ws.connect(this, new URI(websocketUrl));
                }
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

        if (!isConnected) {
            throw new Exception("Failed connecting to " + websocketUrl + ". Nothing to do.");
        }
    }


    WhisperWebsocketSingleton() throws Exception {
        connect();
    }

    @OnWebSocketClose
    public void onClose(int statusCode, String reason)
    {
        this.session = null;
        this.prevTranscriptions = null;
        this.participants = null;
        this.participantListeners = null;
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

        logger.info("===Received final: " + result);
        if (!result.isEmpty())
        {
            int i=0;
            for (TranscriptionListener l : participantListeners.get(participantId))
            {
                i++;
                logger.info("===ParticipantId: " + i + ", " + participantId);
                logger.info("===TranscriptionListener: " + l.toString());
                TranscriptionResult tsResult = new TranscriptionResult(
                        participant,
                        id,
                        transcriptionStart,
                        partial,
                        transcriptionTag,
                        stability,
                        new TranscriptionAlternative(result));
                l.notify(tsResult);
            }
        }
    }



    @OnWebSocketError
    public void onError(Throwable cause)
    {
        logger.error("Error while streaming audio data to transcription service, attempting to reconnect.", cause);
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

    private ByteBuffer buildPayload(Participant participant, ByteBuffer audio) {
        ByteBuffer header = ByteBuffer.allocate(60);
        int lenAudio = audio.remaining();
        ByteBuffer fullPayload = ByteBuffer.allocate(lenAudio + 60);
        String headerStr = participant.getDebugName() + "|" + participant.getSourceLanguage();
        header.put(headerStr.getBytes()).rewind();
        fullPayload.put(header).put(audio).rewind();
        return fullPayload;
    }

    public void disconnectParticipant(String participantId) throws IOException {
        if (participants.containsKey(participantId))
        {
            participants.remove(participantId);
            participantListeners.remove(participantId);
            prevTranscriptions.remove(participantId);
            logger.debug("Disconnected " + participantId);
        }

        if (participants.isEmpty())
        {
            logger.debug("All participants left, disconnecting from Whisper transcription server.");
            session.getRemote().sendBytes(EOF_MESSAGE);
        }
    }

    public void sendAudio(Participant participant, ByteBuffer audio) {
        String participantId = participant.getDebugName();
        addParticipantIfNotExists(participant);
        try
        {
            logger.debug("Sending audio for " + participantId);
            session.getRemote().sendBytes(buildPayload(participant, audio));
        }
        catch (NullPointerException e)
        {
            logger.error("Failed sending audio for " + participantId + ". " + e);
            if (!session.isOpen())
            {
                try {
                    connect();
                }
                catch (Exception ex)
                {
                    logger.error(ex.toString());
                }
            }
        }
        catch (IOException e) {
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

    public Session getSession(){
        return session;
    }

    public static WhisperWebsocketSingleton getInstance() throws Exception {
        if (instance == null)
        {
            synchronized (WhisperWebsocketSingleton.class)
            {
                if (instance == null)
                {
                    instance = new WhisperWebsocketSingleton();
                }
            }
        }
        return instance;
    }
}
