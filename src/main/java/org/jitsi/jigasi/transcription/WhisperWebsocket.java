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

import com.fasterxml.uuid.*;
import org.eclipse.jetty.websocket.api.*;
import org.eclipse.jetty.websocket.api.annotations.*;
import org.eclipse.jetty.websocket.client.*;
import org.jitsi.jigasi.*;
import org.jitsi.jigasi.stats.*;
import org.jitsi.jigasi.util.Util;
import org.jitsi.utils.logging2.*;
import org.json.simple.*;
import org.json.simple.parser.*;

import java.io.*;
import java.net.*;
import java.nio.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

/**
 * This holds the websocket that is used to send audio data to the Whisper.
 * This is one WhisperWebsocket per room.
 * The jetty WebSocketClient process messages in a single thread.
 */
@WebSocket
public class WhisperWebsocket
{
    private final static Logger classLogger = new LoggerImpl(WhisperWebsocket.class.getName());

    private Session wsSession;

    private Map<String, Participant> participants = new ConcurrentHashMap<>();

    private Map<String, Set<TranscriptionListener>> participantListeners = new ConcurrentHashMap<>();

    private Map<String, Instant> participantTranscriptionStarts = new ConcurrentHashMap<>();

    private Map<String, UUID> participantTranscriptionIds= new ConcurrentHashMap<>();

    private static final int maxRetryAttempts = 3;


    /* Transcription language requested by the user who started the transcription */
    public String transcriptionTag = "en-US";

    private final Logger logger;

    /**
     * JWT audience for the Whisper service.
     */
    public final static String JWT_AUDIENCE
            = "org.jitsi.jigasi.transcription.whisper.jwt_audience";

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

    public final static String DEFAULT_WEBSOCKET_URL = "ws://localhost:8000/ws";

    /**
     * Message to send when closing the connection
     */
    private final static ByteBuffer EOF_MESSAGE = ByteBuffer.wrap(new byte[1]);

    /**
     * The config value of the websocket to the speech-to-text
     * service.
     */
    private final static String websocketUrlConfig;

    /**
     * The URL of the websocket to the speech-to-text service.
     */
    private String websocketUrl;

    /**
     * The Connection ID to the Whisper Service
     */
    private final String connectionId = UUID.randomUUID().toString();

    private final static String privateKey;

    private final static String privateKeyName;

    private final static String jwtAudience;

    private WebSocketClient ws;

    private boolean reconnecting = false;

    private final static long CONNECTION_TIMEOUT_MS = 15000L;

    static
    {
        jwtAudience = JigasiBundleActivator.getConfigurationService()
                .getString(JWT_AUDIENCE, "jitsi");
        privateKey = JigasiBundleActivator.getConfigurationService()
                .getString(PRIVATE_KEY, "");
        privateKeyName = JigasiBundleActivator.getConfigurationService()
                .getString(PRIVATE_KEY_NAME, "");
        if (privateKey.isEmpty() || privateKeyName.isEmpty())
        {
            classLogger.warn("org.jitsi.jigasi.transcription.whisper.private_key_name or " +
                    "org.jitsi.jigasi.transcription.whisper.private_key are empty." +
                    "Will not generate a JWT for skynet/streaming-whisper.");
        }

        String wsUrlConfig = JigasiBundleActivator.getConfigurationService()
                .getString(WEBSOCKET_URL, DEFAULT_WEBSOCKET_URL);
        if (wsUrlConfig.endsWith("/"))
        {
            websocketUrlConfig = wsUrlConfig.substring(0, wsUrlConfig.length() - 1);
        }
        else
        {
            websocketUrlConfig = wsUrlConfig;
        }
        classLogger.info("Websocket transcription streaming endpoint: " + websocketUrlConfig);
    }

    /**
     * The thread pool to serve all connect, disconnect ore reconnect operations.
     */
    private static final ExecutorService threadPool = Util.createNewThreadPool("jigasi-whisper-ws");

    private final JSONParser jsonParser = new JSONParser();

    public WhisperWebsocket(Logger parentLogger)
    {
        logger = parentLogger.createChildLogger(WhisperWebsocket.class.getName());
    }

    /**
     * Creates a connection url by concatenating the websocket
     * url with the Connection Id;
     */
    private void generateWebsocketUrl()
    {
        websocketUrl = websocketUrlConfig + "/" + connectionId;
        if (logger.isDebugEnabled())
        {
            logger.debug(" Whisper URL: " + websocketUrl);
        }
    }

    /**
     * Connect to the websocket in a new thread so we do not block Smack.
     */
    void connect()
    {
        threadPool.submit(this::connectInternal);
    }

    /**
     * Connect to the websocket, retry up to maxRetryAttempts
     * with exponential backoff in case of failure
     */
    private void connectInternal()
    {
        int attempt = 0;
        float multiplier = 1.5f;
        long waitTime = 1000L;
        boolean isConnected = false;
        wsSession = null;
        WebSocketClient localWs = null;
        // avoid executing if meeting ended (we are not running) while we were reconnecting
        while (attempt < maxRetryAttempts && !(reconnecting && !isRunning()) && !isConnected)
        {
            try
            {
                generateWebsocketUrl();
                logger.info("Connecting to " + websocketUrl);
                ClientUpgradeRequest upgradeRequest = new ClientUpgradeRequest();
                if (!privateKey.isEmpty() && !privateKeyName.isEmpty())
                {
                    upgradeRequest.setHeader("Authorization", "Bearer " +
                        Util.generateAsapToken(privateKey, privateKeyName, jwtAudience, "jigasi"));
                }
                localWs = new WebSocketClient();
                localWs.start();
                CompletableFuture<Session> futureSession = localWs.connect(this, new URI(websocketUrl), upgradeRequest);
                wsSession = futureSession.orTimeout(CONNECTION_TIMEOUT_MS, TimeUnit.MILLISECONDS).get();
                wsSession.setIdleTimeout(Duration.ofSeconds(300));
                ws = localWs;
                isConnected = true;
                reconnecting = false;
                logger.info("Successfully connected to " + websocketUrl);
                break;
            }
            catch (Exception e)
            {
                if (localWs != null)
                {
                    stopWsClient(localWs);
                    localWs = null;
                }
                Statistics.incrementTotalTranscriberConnectionErrors();
                int remaining = maxRetryAttempts - attempt;
                waitTime *= multiplier;
                logger.error("Failed connecting to " + websocketUrl + ". Retrying in "
                        + waitTime/1000 + "seconds for another " + remaining + " times.", e);
            }
            attempt++;
            synchronized (this)
            {
                try
                {
                    wait(waitTime);
                }
                catch (InterruptedException ignored) {}
            }
        }

        if (!isConnected)
        {
            Statistics.incrementTotalTranscriberConnectionErrors();
            logger.error("Failed connecting to " + websocketUrl + ". Nothing to do.");
        }
    }

    private void stopWsClient(WebSocketClient webSocketClient)
    {
        try
        {
            webSocketClient.stop();
        }
        catch (Exception e)
        {
            logger.error("Error stopping failed WebSocketClient", e);
        }
    }

    private synchronized void reconnect()
    {
        if (reconnecting && !isRunning())
        {
            return;
        }
        reconnecting = true;

        Statistics.incrementTotalTranscriberConnectionRetries();

        threadPool.submit(() ->
        {
            this.stopWebSocketClient();

            this.connectInternal();
        });
    }

    @OnWebSocketClose
    public synchronized void onClose(int statusCode, String reason)
    {
        logger.error("Websocket closed: " + statusCode + " reason:" + reason
            + " isRunning: " + isRunning() + " isOpen:" + (wsSession != null && wsSession.isOpen()));

        if (isRunning())
        {
            // let's try to reconnect
            if (!wsSession.isOpen() || (statusCode > 1000 && statusCode < 2000))
            {
                reconnect();

                return;
            }
        }

        wsSession = null;
        participants = null;
        participantListeners = null;
        participantTranscriptionStarts = null;
        participantTranscriptionIds = null;

        threadPool.submit(this::stopWebSocketClient);
    }

    /**
     * Stop the websocket client.
     * Make sure this is executed in a different thread than the one
     * the websocket client is running in (the onMessage, onError or onClose callbacks).
     */
    private void stopWebSocketClient()
    {
        try
        {
            if (ws != null)
            {
                ws.stop();
                ws = null;
            }
        }
        catch (Exception e)
        {
            logger.error("Error while stopping WebSocketClient", e);
        }
    }


    @OnWebSocketMessage
    public void onMessage(String msg)
    {
        try
        {
            this.onMessageInternal(msg);
        }
        catch (ParseException e)
        {
            logger.error("Error parsing message: " + msg, e);
        }
    }

    private void onMessageInternal(String msg)
        throws ParseException
    {
        boolean partial = true;
        String result;

        JSONObject obj = (JSONObject)jsonParser.parse(msg);
        String msgType = (String)obj.get("type");
        String participantId = (String)obj.get("participant_id");
        Participant participant = participants.get(participantId);
        if (msgType.equals("final"))
        {
            partial = false;
        }

        result = (String)obj.get("text");
        double stability = (double)obj.get("variance");
        if (logger.isDebugEnabled())
        {
            logger.debug("Received result: " + result);
        }

        Instant startTranscription = participantTranscriptionStarts.getOrDefault(participantId, null);
        UUID transcriptionId = participantTranscriptionIds.getOrDefault(participantId, null);

        if (startTranscription == null)
        {
            Date now = new Date();
            startTranscription = now.toInstant();
            transcriptionId =  Generators.timeBasedReorderedGenerator().generate();
            participantTranscriptionIds.put(participantId, transcriptionId);
            participantTranscriptionStarts.put(participantId, startTranscription);
        }

        Set<TranscriptionListener> partListeners = participantListeners.getOrDefault(participantId, null);
        if (!result.isEmpty() && partListeners != null)
        {
            int i=0;

            for (TranscriptionListener l : partListeners)
            {
                i++;
                if (logger.isDebugEnabled())
                {
                    logger.debug("ParticipantId: " + i + ", " + participantId);
                    logger.debug("TranscriptionListener: " + l.toString());
                }
                TranscriptionResult tsResult = new TranscriptionResult(
                        participant,
                        transcriptionId,
                        startTranscription,
                        partial,
                        getLanguage(participant),
                        stability,
                        new TranscriptionAlternative(result));
                l.notify(tsResult);
            }
        }
        if (!partial)
        {
            participantTranscriptionStarts.remove(participantId);
            participantTranscriptionIds.remove(participantId);
        }
    }



    @OnWebSocketError
    public void onError(Throwable cause)
    {
        if (!ended() && isRunning())
        {
            Statistics.incrementTotalTranscriberSendErrors();
            logger.error("Error while streaming audio data to transcription service.", cause);
        }
    }

    private String getLanguage(Participant participant)
    {
        String lang = participant.getTranslationLanguage();
        if (logger.isDebugEnabled())
        {
            logger.debug("Translation language is " + lang);
        }
        if (lang == null)
        {
            lang = participant.getSourceLanguage();
        }
        if (logger.isDebugEnabled())
        {
            logger.debug("Returned language is " + lang);
        }
        return lang;
    }

    private ByteBuffer buildPayload(String participantId, Participant participant, ByteBuffer audio)
    {
        ByteBuffer header = ByteBuffer.allocate(60);
        int lenAudio = audio.remaining();
        ByteBuffer fullPayload = ByteBuffer.allocate(lenAudio + 60);
        String headerStr = participantId + "|" + this.getLanguage(participant);
        header.put(headerStr.getBytes()).rewind();
        fullPayload.put(header).put(audio).rewind();
        return fullPayload;
    }

    /**
     * Disconnect a participant from the transcription service, executing that in a new thread so we do not block Smack.
     * @param participantId the participant to disconnect.
     * @param callback the callback to execute when the last participant is disconnected and session is closed.
     */
    public void disconnectParticipant(String participantId, Consumer<Boolean> callback)
    {
        threadPool.submit(() -> this.disconnectParticipantInternal(participantId, callback));
    }

    private void disconnectParticipantInternal(String participantId, Consumer<Boolean> callback)
    {
        synchronized (this)
        {
            if (ended() && !isRunning())
            {
                callback.accept(true);
                return;
            }

            if (participants.containsKey(participantId))
            {
                participants.remove(participantId);
                participantListeners.remove(participantId);
                logger.info("Disconnected " + participantId);
            }

            if (participants.isEmpty())
            {
                logger.info("All participants have left, disconnecting from Whisper transcription server.");

                try
                {
                    wsSession.getRemote().sendBytes(EOF_MESSAGE);
                }
                catch (IOException e)
                {
                    logger.error("Error while finalizing websocket connection for participant "
                            + participantId, e);
                }

                wsSession.disconnect();
                callback.accept(true);
            }

            callback.accept(false);
        }
    }

    public void sendAudio(String participantId, Participant participant, ByteBuffer audio)
    {
        if (logger.isDebugEnabled())
        {
            logger.debug("Sending audio for " + participantId);
        }
        addParticipantIfNotExists(participantId, participant);
        RemoteEndpoint remoteEndpoint = wsSession.getRemote();
        if (remoteEndpoint == null)
        {
            Statistics.incrementTotalTranscriberSendErrors();
            logger.error("Failed sending audio for " + participantId + ". Attempting to reconnect.");
            if (!wsSession.isOpen())
            {
                reconnect();
            }
            else
            {
                logger.warn("Failed sending audio for " + participantId
                    + ". RemoteEndpoint is null but sessions is open.");
            }
        }
        else
        {
            try
            {
                remoteEndpoint.sendBytes(buildPayload(participantId, participant, audio));
            }
            catch (IOException e)
            {
                Statistics.incrementTotalTranscriberSendErrors();
                logger.error("Failed sending audio for " + participantId + ". " + e);
            }
        }
    }

    private void addParticipantIfNotExists(String participantId, Participant participant)
    {
        synchronized (this)
        {
            if (!participants.containsKey(participantId))
            {
                participants.put(participantId, participant);
                participantListeners.put(participantId, new HashSet<>());
            }
        }
    }

    public void addListener(TranscriptionListener listener, Participant participant)
    {
        String participantId = participant.getDebugName().split("/")[1];
        addParticipantIfNotExists(participantId, participant);
        participantListeners.get(participantId).add(listener);
    }

    public void setTranscriptionTag(String tsTag)
    {
        transcriptionTag = tsTag;
    }

    public boolean ended()
    {
        return wsSession == null;
    }

    /**
     * We consider this websocket transcription running when there are participants.
     * While reconnecting we still have participants. After disconnectParticipantInternal where we clean
     * all participants and close the socket we are no longer running, and we should not try to reconnect.
     * @return true if operational.
     */
    private boolean isRunning()
    {
        return participants != null && !participants.isEmpty();
    }
}
