package org.jitsi.jigasi.transcription.oracle;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ser.FilterProvider;
import com.fasterxml.jackson.databind.ser.impl.*;
import com.oracle.bmc.aispeech.model.*;
import com.oracle.bmc.auth.BasicAuthenticationDetailsProvider;
import com.oracle.bmc.http.signing.*;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.*;
import org.eclipse.jetty.websocket.client.*;
import org.jitsi.utils.logging.Logger;

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.*;

@WebSocket
public class OracleRealtimeClient {
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
     * Constructor.
     *
     * @param listener for the RealtimeClientListener
     */
    public OracleRealtimeClient(
            OracleRealtimeClientListener listener,
            BasicAuthenticationDetailsProvider authenticationDetailsProvider,
            String compartmentId) {
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
    public void onClose(int statusCode, String reason) {
        String closedBy = isClosureClientInitiated ? "client" : "server";
        logger.info("Session closed by " + closedBy + ", reason = " + reason + ", status code = " + statusCode);
        isConnected = false;
        this.session = null;
        try {
            this.client.stop();
        } catch (Exception e) {
            logger.error("The following exception occured while trying to stop the realtime speech websocket client: ", e);
        }
        //The listener can implement their own closing logic
        this.listener.onClose(statusCode, reason);
    }

    /**
     * the openError event handler.
     *
     * @param error the error throwable sent from remote
     */
    @OnWebSocketError
    public void onError(Throwable error) {
        logger.info("Error: " + error.getMessage());
        isConnected = false;
        this.session = null;

        // Pass the exception down to the listener.
        if (listener != null) {
            listener.onError(error);
        }
    }

    /**
     * the onConnect event handler.
     *
     * @param session the session that got connected
     */
    @OnWebSocketConnect
    public void onConnect(Session session) {
        logger.info("Connect: " + session.getRemoteAddress());
        synchronized (this) {
            this.session = session;
        }

        // We need to decide if we want to send tokens or credentials in the client
        // initialization
        sendCreds(compartmentId);

        isConnected = true;
        if (listener != null) {
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
    public void onMessage(String message) throws JsonProcessingException {
        if (listener == null) {
            return;
        }
        try {
            final RealtimeMessage realtimeMessage = objectMapper.readValue(message, RealtimeMessage.class);
            if (realtimeMessage instanceof RealtimeMessageAckAudio) {
                listener.onAckMessage((RealtimeMessageAckAudio) realtimeMessage);
            } else if (realtimeMessage instanceof RealtimeMessageConnect) {
                listener.onConnectMessage((RealtimeMessageConnect) realtimeMessage);
            } else if (realtimeMessage instanceof RealtimeMessageResult) {
                listener.onResult((RealtimeMessageResult) realtimeMessage);
            } else if (realtimeMessage instanceof RealtimeMessageError) {
                final RealtimeMessageError errorMessage = (RealtimeMessageError) realtimeMessage;
                logger.error(
                        "Received RealtimeMessageError with message {}" + errorMessage.getMessage());
                listener.onError(new ConnectException(errorMessage.getMessage()));
            }
        } catch (JsonProcessingException e) {
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
    public void open(String server, int port, RealtimeParameters parameters) throws OracleServiceDisruptionException {
        try {
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
                queryParameter += "languageCode=" + parameters.getLanguageCode().getValue() + "&";
            if (parameters.getModelDomain() != null)
                queryParameter += "modelDomain=" + parameters.getModelDomain().getValue() + "&";
            if (parameters.getCustomizations() != null && !parameters.getCustomizations().isEmpty())
                queryParameter += "customizations=" + URLEncoder.encode(customizationsJson, "UTF-8");
            if (queryParameter.charAt(queryParameter.length() - 1) == '&')
                queryParameter = queryParameter.substring(0, queryParameter.length() - 1);
            // The server should contain ws or wss
            destUri = new URI(
                    server
                            + ":"
                            + port
                            + "/ws/transcribe/stream?"
                            + queryParameter); // TODO

            logger.info("Connecting to " + destUri);

            final ClientUpgradeRequest request = new ClientUpgradeRequest();
            logger.info("Content-Type: " + parameters.getEncoding());
            request.setHeader("Content-Type", parameters.getEncoding());
            logger.info("Connecting to: " + destUri);
            this.session = client.connect(this, destUri, request).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            logger.error("Open connection exception {}", e);
            throw new OracleServiceDisruptionException(e);
        }
    }

    /**
     * Checks the connection status.
     *
     * @return true if connected
     */
    public boolean isConnected() {
        return isConnected;
    }

    /**
     * Sends the audio data of bytes to remote.
     *
     * @param audioBytes represeting the audio data
     * @throws OracleServiceDisruptionException If session is closed
     * @throws IOException                If errors happens on sending
     */
    public void sendAudioData(byte[] audioBytes) throws OracleServiceDisruptionException, IOException {
        if (this.session == null) {
            logger.error("Session has been closed, cannot send audio anymore");
            throw new OracleServiceDisruptionException("Session has been closed, cannot send audio anymore");
        }
        try {
            if (this.isConnected) {
                synchronized (this) {
                    this.session.getRemote().sendBytes(ByteBuffer.wrap(audioBytes));
                }
            } else {
                throw new ConnectException("Websocket not connected.");
            }
        } catch (IOException e) {
            logger.error("Send exception {}", e);
            throw e;
        }
    }

    /** Closes the connection. */
    public void close() {
        isClosureClientInitiated = true;
        logger.info("Closing SDK connection");
        if (this.session != null) {
            this.session.close();
            try {
                this.client.stop();
            } catch (Exception e) {
                logger.error("RealtimeSDK client could not be stopped.");
            }
            this.isConnected = false;
        }
    }

    private void sendCreds(String compartmentId) {
        final RequestSigner requestSigner = DefaultRequestSigner.createRequestSigner(authenticationDetailsProvider);
        logger.info("Sending credentials");
        final Map<String, List<String>> headers = new HashMap<>();
        final Map<String, String> newHeaders = requestSigner.signRequest(destUri, "GET", headers, null);
        newHeaders.put("uri", destUri.toString());

        final RealtimeMessageAuthenticationCredentials authenticationMessage = RealtimeMessageAuthenticationCredentials
                .builder()
                .compartmentId(compartmentId)
                .headers(newHeaders)
                .build();
        try {
            sendMessage(objectMapper.writeValueAsString(authenticationMessage));
        } catch (JsonProcessingException e) {
            logger.info("Could not serialize authentication credentials: " + e);
            // TODO: Add better exceptions
            throw new RuntimeException(e);
        }
    }

    public void sendMessage(String message) {
        try {
            session.getRemote().sendString(message);
        } catch (IOException e) {
            logger.info("Could not send message to the remote server: " + e);
            // TODO: Add better exceptions
            throw new RuntimeException(e);
        }
    }
}
