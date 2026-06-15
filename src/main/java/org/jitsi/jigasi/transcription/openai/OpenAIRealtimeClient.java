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

package org.jitsi.jigasi.transcription.openai;

import org.eclipse.jetty.websocket.api.*;
import org.eclipse.jetty.websocket.api.annotations.*;
import org.eclipse.jetty.websocket.client.*;
import org.jitsi.jigasi.*;
import org.jitsi.jigasi.util.*;
import org.jitsi.utils.logging2.*;
import org.json.simple.*;
import org.json.simple.parser.*;

import java.net.URI;
import java.util.Base64;
import java.util.concurrent.*;

/**
 * WebSocket client for the OpenAI Realtime transcription API.
 * One instance per participant session.
 *
 * Protocol:
 *   1. Connect WebSocket to wss://api.openai.com/v1/realtime?model=gpt-realtime-2
 *      with Authorization: Bearer <API_KEY>
 *   2. On session.created: send session.update to enable transcription mode
 *      (session.type="transcription", audio.input.transcription.model="gpt-realtime-whisper")
 *   3. Send audio: input_audio_buffer.append with base64-encoded PCM 24kHz mono
 *   4. Commit: input_audio_buffer.commit every N frames (manual turn detection)
 *   5. Receive: conversation.item.input_audio_transcription.delta (partial)
 *               conversation.item.input_audio_transcription.completed (final)
 */
@WebSocket
public class OpenAIRealtimeClient
{
    private static final Logger logger = new LoggerImpl(OpenAIRealtimeClient.class.getName());

    public static final String API_KEY_CONFIG
        = "org.jitsi.jigasi.transcription.openai.apiKey";

    public static final String WEBSOCKET_URL
        = "org.jitsi.jigasi.transcription.openai.websocketUrl";

    /** Session model used in the WebSocket URL — must support session.update with type=transcription. */
    public static final String SESSION_MODEL_CONFIG
        = "org.jitsi.jigasi.transcription.openai.sessionModel";

    /** Transcription model passed inside session.update (gpt-realtime-whisper). */
    public static final String TRANSCRIPTION_MODEL_CONFIG
        = "org.jitsi.jigasi.transcription.openai.transcriptionModel";

    /**
     * Latency/accuracy tradeoff for gpt-realtime-whisper.
     * Accepted values: minimal, low, medium, high, xhigh.
     */
    public static final String TRANSCRIPTION_DELAY_CONFIG
        = "org.jitsi.jigasi.transcription.openai.transcriptionDelay";

    public static final String DEFAULT_WEBSOCKET_URL
        = "wss://api.openai.com/v1/realtime";

    public static final String DEFAULT_SESSION_MODEL
        = "gpt-realtime-2";

    public static final String DEFAULT_TRANSCRIPTION_MODEL
        = "gpt-realtime-whisper";

    public static final String DEFAULT_TRANSCRIPTION_DELAY
        = "low";

    /** PCM sample rate produced by PCMAudioSilence24kMediaDevice. */
    private static final int AUDIO_SAMPLE_RATE = 24000;

    private static final int MAX_RETRY_ATTEMPTS = 3;

    private static final long CONNECTION_TIMEOUT_MS = 15000L;

    /** Thread pool shared across all OpenAI WS connections in this JVM. */
    private static final ExecutorService threadPool
        = Util.createNewThreadPool("jigasi-openai-ws");

    private final OpenAIRealtimeClientListener listener;

    private final String apiKey;

    private final String language;

    private final String websocketBaseUrl;

    private final String sessionModel;

    private final String transcriptionModel;

    private final String transcriptionDelay;

    private Session session;

    private WebSocketClient wsClient;

    private boolean connected = false;

    private boolean closureClientInitiated = false;

    private final JSONParser jsonParser = new JSONParser();

    public OpenAIRealtimeClient(OpenAIRealtimeClientListener listener, String language)
    {
        this.listener = listener;
        this.language = language;

        apiKey = JigasiBundleActivator.getConfigurationService()
            .getString(API_KEY_CONFIG, "");

        websocketBaseUrl = JigasiBundleActivator.getConfigurationService()
            .getString(WEBSOCKET_URL, DEFAULT_WEBSOCKET_URL);

        sessionModel = JigasiBundleActivator.getConfigurationService()
            .getString(SESSION_MODEL_CONFIG, DEFAULT_SESSION_MODEL);

        transcriptionModel = JigasiBundleActivator.getConfigurationService()
            .getString(TRANSCRIPTION_MODEL_CONFIG, DEFAULT_TRANSCRIPTION_MODEL);

        transcriptionDelay = JigasiBundleActivator.getConfigurationService()
            .getString(TRANSCRIPTION_DELAY_CONFIG, DEFAULT_TRANSCRIPTION_DELAY);
    }

    /**
     * Initiates a non-blocking connection with exponential-backoff retry.
     */
    public void connect()
    {
        threadPool.submit(this::connectInternal);
    }

    private void connectInternal()
    {
        int attempt = 0;
        float multiplier = 1.5f;
        long waitMs = 1000L;

        while (attempt < MAX_RETRY_ATTEMPTS)
        {
            WebSocketClient localClient = null;
            try
            {
                String wsUrl = websocketBaseUrl + "?model=" + sessionModel;
                logger.info("Connecting to OpenAI Realtime API: " + wsUrl
                    + " (attempt " + (attempt + 1) + ")");

                ClientUpgradeRequest request = new ClientUpgradeRequest();
                request.setHeader("Authorization", "Bearer " + apiKey);

                localClient = new WebSocketClient();
                localClient.start();

                CompletableFuture<Session> future = localClient.connect(
                    this, new URI(wsUrl), request);

                session = future.orTimeout(CONNECTION_TIMEOUT_MS, TimeUnit.MILLISECONDS).get();
                session.setIdleTimeout(java.time.Duration.ofSeconds(300));
                wsClient = localClient;
                connected = true;

                logger.info("Connected to OpenAI Realtime API");
                return;
            }
            catch (Exception e)
            {
                if (localClient != null)
                {
                    stopClientQuietly(localClient);
                }
                attempt++;
                int remaining = MAX_RETRY_ATTEMPTS - attempt;
                waitMs *= multiplier;
                logger.error("Failed to connect to OpenAI Realtime API. Retrying in "
                    + waitMs / 1000 + "s (" + remaining + " attempts left).", e);

                if (attempt < MAX_RETRY_ATTEMPTS)
                {
                    synchronized (this)
                    {
                        try { wait(waitMs); } catch (InterruptedException ignored) {}
                    }
                }
            }
        }

        logger.error("Could not connect to OpenAI Realtime API after "
            + MAX_RETRY_ATTEMPTS + " attempts.");
    }

    @OnWebSocketOpen
    public void onConnect(Session sess)
    {
        logger.info("WebSocket open: " + sess.getRemoteSocketAddress());
        synchronized (this)
        {
            this.session = sess;
        }
        sendSessionUpdate();
        if (listener != null)
        {
            listener.onConnect();
        }
    }

    @OnWebSocketClose
    public void onClose(int statusCode, String reason)
    {
        connected = false;
        session = null;
        threadPool.submit(() ->
        {
            String who = closureClientInitiated ? "client" : "server";
            logger.info("WebSocket closed by " + who + " — code=" + statusCode + " reason=" + reason);
            stopClientQuietly(wsClient);
            wsClient = null;
            if (listener != null)
            {
                listener.onClose(statusCode, reason);
            }
        });
    }

    @OnWebSocketError
    public void onError(Throwable cause)
    {
        logger.error("OpenAI Realtime WebSocket error", cause);
        connected = false;
        session = null;
        if (listener != null)
        {
            listener.onError(cause);
        }
    }

    @OnWebSocketMessage
    public void onMessage(String message)
    {
        try
        {
            onMessageInternal(message);
        }
        catch (ParseException e)
        {
            logger.error("Failed to parse OpenAI message: " + message, e);
        }
    }

    private void onMessageInternal(String message) throws ParseException
    {
        JSONObject obj = (JSONObject) jsonParser.parse(message);
        String type = (String) obj.get("type");
        if (type == null)
        {
            return;
        }

        switch (type)
        {
        case "conversation.item.input_audio_transcription.delta":
            if (listener != null)
            {
                String delta = (String) obj.get("delta");
                if (delta != null && !delta.isEmpty())
                {
                    listener.onTranscriptionDelta(delta);
                }
            }
            break;

        case "conversation.item.input_audio_transcription.completed":
            if (listener != null)
            {
                String transcript = (String) obj.get("transcript");
                if (transcript != null && !transcript.isEmpty())
                {
                    listener.onTranscriptionCompleted(transcript);
                }
            }
            break;

        case "error":
            JSONObject error = (JSONObject) obj.get("error");
            String errMsg = error != null ? (String) error.get("message") : "unknown error";
            logger.error("OpenAI Realtime API error: " + errMsg);
            if (listener != null)
            {
                listener.onError(new RuntimeException("OpenAI API error: " + errMsg));
            }
            break;

        case "session.created":
        case "session.updated":
            if (logger.isDebugEnabled())
            {
                logger.debug("Session event: " + type);
            }
            break;

        default:
            if (logger.isDebugEnabled())
            {
                logger.debug("Unhandled OpenAI event type: " + type);
            }
            break;
        }
    }

    /**
     * Sends audio bytes to OpenAI as a base64-encoded input_audio_buffer.append event.
     */
    public void sendAudio(byte[] audioBytes)
    {
        if (session == null || !connected)
        {
            logger.warn("Cannot send audio — not connected.");
            return;
        }

        String encoded = Base64.getEncoder().encodeToString(audioBytes);
        String json = "{\"type\":\"input_audio_buffer.append\",\"audio\":\"" + encoded + "\"}";

        synchronized (this)
        {
            session.sendText(json, Callback.from(
                () -> {},
                cause -> logger.error("Failed to send audio to OpenAI Realtime API", cause)));
        }
    }

    public void close()
    {
        closureClientInitiated = true;
        logger.info("Closing OpenAI Realtime connection");
        if (session != null)
        {
            session.close();
        }
    }

    public boolean isConnected()
    {
        return connected;
    }

    /**
     * Configures the session for transcription-only mode.
     * Must be sent after session.created is received.
     * Uses session.type="transcription" with gpt-realtime-whisper as the transcription model.
     */
    private void sendSessionUpdate()
    {
        String lang = (language != null && !language.isEmpty()) ? language : "en";

        String json = "{"
            + "\"type\":\"session.update\","
            + "\"session\":{"
            + "\"type\":\"transcription\","
            + "\"audio\":{"
            + "\"input\":{"
            + "\"format\":{\"type\":\"audio/pcm\",\"rate\":" + AUDIO_SAMPLE_RATE + "},"
            + "\"transcription\":{"
            + "\"model\":\"" + transcriptionModel + "\","
            + "\"language\":\"" + lang + "\","
            + "\"delay\":\"" + transcriptionDelay + "\""
            + "}"
            + "}"
            + "}"
            + "}"
            + "}";

        sendText(json);
    }

    /**
     * Commits the audio buffer to trigger transcription.
     * Required when turn_detection is null (manual mode).
     */
    public void commitAudioBuffer()
    {
        sendText("{\"type\":\"input_audio_buffer.commit\"}");
    }

    private void sendText(String message)
    {
        if (session == null)
        {
            logger.warn("Cannot send message — session is null.");
            return;
        }
        session.sendText(message, Callback.from(
            () -> {},
            cause -> logger.error("Failed to send message to OpenAI Realtime API", cause)));
    }

    private void stopClientQuietly(WebSocketClient client)
    {
        if (client == null)
        {
            return;
        }
        try
        {
            client.stop();
        }
        catch (Exception e)
        {
            logger.error("Error stopping WebSocketClient", e);
        }
    }
}
