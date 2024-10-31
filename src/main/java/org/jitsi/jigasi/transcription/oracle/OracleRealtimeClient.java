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

package org.jitsi.jigasi.transcription.oracle;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.ser.*;
import com.fasterxml.jackson.databind.ser.impl.*;
import com.oracle.bmc.aispeech.model.*;
import com.oracle.bmc.auth.*;
import com.oracle.bmc.http.signing.*;
import org.eclipse.jetty.websocket.api.*;
import org.eclipse.jetty.websocket.api.annotations.*;
import org.eclipse.jetty.websocket.client.*;
import org.jitsi.jigasi.util.*;
import org.jitsi.utils.logging.*;

import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.charset.*;
import java.util.*;
import java.util.concurrent.*;

@WebSocket
public class OracleRealtimeClient
{
    private Session session;
    private boolean isConnected;
    private final OracleRealtimeClientListener listener;
    private WebSocketClient client;

    private final BasicAuthenticationDetailsProvider authenticationDetailsProvider;
    private URI destUri;

    private final FilterProvider filters = new SimpleFilterProvider()
            .setFailOnUnknownId(false)
            .addFilter("explicitlySetFilter", SimpleBeanPropertyFilter.serializeAll());
    private final ObjectMapper objectMapper = new ObjectMapper().setFilterProvider(filters);
    private final String compartmentId;
    private Boolean isClosureClientInitiated = false;

    private final static Logger logger
            = Logger.getLogger(OracleRealtimeClient.class);

    /**
     * The thread pool to serve all connect, disconnect ore reconnect operations.
     */
    private static final ExecutorService threadPool = Util.createNewThreadPool("jigasi-oracle-ws");

    /**
     * Constructor.
     *
     * @param listener for the RealtimeClientListener
     */
    public OracleRealtimeClient(
            OracleRealtimeClientListener listener,
            BasicAuthenticationDetailsProvider authenticationDetailsProvider,
            String compartmentId)
    {
        this.isConnected = false;
        this.listener = listener;
        this.authenticationDetailsProvider = authenticationDetailsProvider;
        this.compartmentId = compartmentId;
    }

    /**
     * the onClose event handler.
     *
     * @param statusCode the status code sent from remote
     * @param reason     the close reason sent from remote
     */
    @OnWebSocketClose
    public void onClose(int statusCode, String reason)
    {
        threadPool.submit(() ->
        {
            String closedBy = isClosureClientInitiated ? "client" : "server";
            logger.info("Session closed by " + closedBy + ", reason = " + reason + ", status code = " + statusCode);
            isConnected = false;
            this.session = null;
            try
            {
                this.client.stop();
            }
            catch (Exception e)
            {
                logger.error("Error while stopping the OCI transcription client: ", e);
            }
            //The listener can implement their own closing logic
            this.listener.onClose(statusCode, reason);
        });
    }

    /**
     * the openError event handler.
     *
     * @param error the error throwable sent from remote
     */
    @OnWebSocketError
    public void onError(Throwable error)
    {
        logger.error(error.getMessage());
        isConnected = false;
        this.session = null;

        // Pass the exception down to the listener.
        if (listener != null)
        {
            listener.onError(error);
        }
    }

    /**
     * the onConnect event handler.
     *
     * @param session the session that got connected
     */
    @OnWebSocketConnect
    public void onConnect(Session session)
    {
        logger.info("Connected to: " + session.getRemoteAddress());
        synchronized (this)
        {
            this.session = session;
        }

        sendCreds(compartmentId);

        isConnected = true;
        if (listener != null)
        {
            listener.onConnect();
        }
    }

    /**
     * The onMessage event handler.
     *
     * @param message the message sent from remote string of server
     * @throws JsonProcessingException if errors happens on processing json response
     */
    @OnWebSocketMessage
    public void onMessage(String message) throws JsonProcessingException
    {
        if (listener == null)
        {
            return;
        }
        try
        {
            final RealtimeMessage realtimeMessage = objectMapper.readValue(message, RealtimeMessage.class);
            if (realtimeMessage instanceof RealtimeMessageAckAudio)
            {
                listener.onAckMessage((RealtimeMessageAckAudio) realtimeMessage);
            }
            else if (realtimeMessage instanceof RealtimeMessageConnect)
            {
                listener.onConnectMessage((RealtimeMessageConnect) realtimeMessage);
            }
            else if (realtimeMessage instanceof RealtimeMessageResult)
            {
                listener.onResult((RealtimeMessageResult) realtimeMessage);
            }
            else if (realtimeMessage instanceof RealtimeMessageError)
            {
                final RealtimeMessageError errorMessage = (RealtimeMessageError) realtimeMessage;
                logger.error("Received RealtimeMessageError {}" + errorMessage.getMessage());
                listener.onError(new ConnectException(errorMessage.getMessage()));
            }
        }
        catch (JsonProcessingException e)
        {
            logger.error("Text Message: JsonProcessingException {}", e);
            throw e;
        }
    }

    /**
     * Opens a connection to the specified remote.
     *
     * @param server     the URL string of server
     * @param port       the port to connect
     * @param parameters other additional connection parameters
     */
    public void open(String server, int port, RealtimeParameters parameters) throws OracleServiceDisruptionException
    {
        try
        {
            this.client = new WebSocketClient(); // TODO Should be global
            client.start();
            final String customizationsJson = objectMapper.writeValueAsString(parameters.getCustomizations());
            String queryParameter = "";
            if (parameters.getIsAckEnabled() != null)
                queryParameter += "isAckEnabled=" + (parameters.getIsAckEnabled() ? "true" : "false") + "&";
            if (parameters.getShouldIgnoreInvalidCustomizations() != null)
                queryParameter += "shouldIgnoreInvalidCustomizations="
                        + (parameters.getShouldIgnoreInvalidCustomizations() ? "true" : "false") + "&";
            if (parameters.getPartialSilenceThresholdInMs() != null)
                queryParameter += "partialSilenceThresholdInMs=" + parameters.getPartialSilenceThresholdInMs() + "&";
            if (parameters.getFinalSilenceThresholdInMs() != null)
                queryParameter += "finalSilenceThresholdInMs=" + parameters.getFinalSilenceThresholdInMs() + "&";
            if (parameters.getLanguageCode() != null)
                queryParameter += "languageCode=" + parameters.getLanguageCode() + "&";
            if (parameters.getModelDomain() != null)
                queryParameter += "modelDomain=" + parameters.getModelDomain().getValue() + "&";
            if (parameters.getCustomizations() != null && !parameters.getCustomizations().isEmpty())
                queryParameter += "customizations=" + URLEncoder.encode(customizationsJson, StandardCharsets.UTF_8);
            if (queryParameter.charAt(queryParameter.length() - 1) == '&')
                queryParameter = queryParameter.substring(0, queryParameter.length() - 1);
            // The server should contain ws or wss
            destUri = new URI(server + ":" + port + "/ws/transcribe/stream?" + queryParameter);
            logger.info("Connecting to " + destUri);
            final ClientUpgradeRequest request = new ClientUpgradeRequest();
            request.setHeader("Content-Type", parameters.getEncoding());
            this.session = client.connect(this, destUri, request).get(10, TimeUnit.SECONDS);
        }
        catch (Exception e)
        {
            logger.error("Failed to connect to OCI Transcriber {}", e);
            throw new OracleServiceDisruptionException(e);
        }
    }

    /**
     * Checks the connection status.
     *
     * @return true if connected
     */
    public boolean isConnected()
    {
        return isConnected;
    }

    /**
     * Sends the audio data of bytes to remote.
     *
     * @param audioBytes representing the audio data
     * @throws OracleServiceDisruptionException If session is closed
     * @throws IOException                If errors happens on sending
     */
    public void sendAudioData(byte[] audioBytes) throws OracleServiceDisruptionException, IOException
    {
        if (this.session == null)
        {
            throw new OracleServiceDisruptionException("Session has been closed, cannot send audio anymore");
        }
        try
        {
            if (this.isConnected)
            {
                synchronized (this)
                {
                    this.session.getRemote().sendBytes(ByteBuffer.wrap(audioBytes));
                }
            }
            else
            {
                logger.error("Websocket not connected.");
            }
        }
        catch (IOException e)
        {
            logger.error("Error while sending audio data: ", e);
            throw e;
        }
    }

    /** Closes the connection. */
    public void close()
    {
        isClosureClientInitiated = true;
        logger.info("Closing OCI Transcriber connection");
        if (this.session != null)
        {
            this.session.close();
            try
            {
                this.client.stop();
            }
            catch (Exception e)
            {
                logger.error("RealtimeSDK client could not be stopped.", e);
            }
            this.isConnected = false;
        }
    }

    private void sendCreds(String compartmentId)
    {
        final RequestSigner requestSigner = DefaultRequestSigner.createRequestSigner(authenticationDetailsProvider);
        final Map<String, List<String>> headers = new HashMap<>();
        final Map<String, String> newHeaders = requestSigner.signRequest(destUri, "GET", headers, null);
        newHeaders.put("uri", destUri.toString());

        final RealtimeMessageAuthenticationCredentials authenticationMessage = RealtimeMessageAuthenticationCredentials
                .builder()
                .compartmentId(compartmentId)
                .headers(newHeaders)
                .build();
        try
        {
            sendMessage(objectMapper.writeValueAsString(authenticationMessage));
        }
        catch (JsonProcessingException e)
        {
            logger.error("Could not serialize authentication credentials: " + e);
        }
    }

    public void sendMessage(String message)
    {
        try
        {
            session.getRemote().sendString(message);
        }
        catch (IOException e)
        {
            logger.error("Could not send message to the remote server: " + e);
        }
    }
}
