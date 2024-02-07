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

import io.jsonwebtoken.*;
import org.eclipse.jetty.websocket.api.*;
import org.eclipse.jetty.websocket.api.annotations.*;
import org.eclipse.jetty.websocket.client.*;
import org.jitsi.jigasi.*;
import org.jitsi.utils.logging.*;
import org.json.*;

import java.io.*;
import java.net.*;
import java.nio.*;
import java.security.*;
import java.security.spec.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;


@WebSocket
public class WhisperWebsocket
{
    private Session wsSession;

    private Map<String, Participant> participants = new ConcurrentHashMap<>();

    private Map<String, Set<TranscriptionListener>> participantListeners = new ConcurrentHashMap<>();

    private static final int maxRetryAttempts = 10;


    /* Transcription language requested by the user who requested the transcription */
    public String transcriptionTag = "en-US";

    private final static Logger logger
            = Logger.getLogger(WhisperWebsocket.class);

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

    private String jwtAudience;


    private String getJWT() throws NoSuchAlgorithmException, InvalidKeySpecException
    {
        long nowMillis = System.currentTimeMillis();
        Date now = new Date(nowMillis);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        PKCS8EncodedKeySpec keySpecPKCS8 = new PKCS8EncodedKeySpec(Base64.getDecoder().decode(privateKey));
        PrivateKey finalPrivateKey = kf.generatePrivate(keySpecPKCS8);
        JwtBuilder builder = Jwts.builder()
                .setHeaderParam("kid", privateKeyName)
                .setIssuedAt(now)
                .setAudience(jwtAudience)
                .setIssuer("jigasi")
                .signWith(finalPrivateKey, SignatureAlgorithm.RS256);
        long expires = nowMillis + (60 * 5 * 1000);
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
            websocketUrl = websocketUrlConfig + "/" + connectionId + "?auth_token=" + getJWT();
        }
        catch (Exception e)
        {
            logger.error("Failed generating JWT for Whisper. " + e);
        }
        if (logger.isDebugEnabled())
        {
            logger.debug("Whisper URL: " + websocketUrl);
        }
    }

    private void getConfig()
    {
        jwtAudience = JigasiBundleActivator.getConfigurationService()
                .getString(JWT_AUDIENCE, "jitsi");
        websocketUrlConfig = JigasiBundleActivator.getConfigurationService()
                .getString(WEBSOCKET_URL, DEFAULT_WEBSOCKET_URL);
        if (websocketUrlConfig.endsWith("/"))
        {
            websocketUrlConfig = websocketUrlConfig.substring(0, websocketUrlConfig.length() - 1);
        }
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
    void connect()
    {
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
            try
            {
                wait(waitTime);
            }
            catch (InterruptedException ignored) {}
        }

        if (!isConnected)
        {
            logger.error("Failed connecting to " + websocketUrl + ". Nothing to do.");
        }
    }

    @OnWebSocketClose
    public void onClose(int statusCode, String reason)
    {
        wsSession = null;
        participants = null;
        participantListeners = null;
    }


    @OnWebSocketMessage
    public void onMessage(String msg)
    {
        boolean partial = true;
        String result;
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
        if (logger.isDebugEnabled())
        {
            logger.debug("Received final: " + result);
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
        if (wsSession != null)
        {
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

    public boolean disconnectParticipant(String participantId)
        throws IOException
    {
        synchronized (this)
        {
            if (participants.containsKey(participantId))
            {
                participants.remove(participantId);
                participantListeners.remove(participantId);
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
            logger.error("Failed sending audio for " + participantId + ". Attempting to reconnect.");
            if (!wsSession.isOpen())
            {
                try
                {
                    connect();
                    remoteEndpoint = wsSession.getRemote();
                }
                catch (Exception ex)
                {
                    logger.error(ex);
                }
            }
        }
        if (remoteEndpoint != null)
        {
            try
            {
                remoteEndpoint.sendBytes(buildPayload(participantId, participant, audio));
            }
            catch (IOException e)
            {
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
}
