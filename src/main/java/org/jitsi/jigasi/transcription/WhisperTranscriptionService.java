package org.jitsi.jigasi.transcription;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.eclipse.jetty.websocket.api.*;
import org.jitsi.impl.neomedia.device.AudioMixerMediaDevice;
import org.jitsi.impl.neomedia.device.ReceiveStreamBufferListener;
import org.jitsi.jigasi.JigasiBundleActivator;
import org.jitsi.utils.logging.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.xbill.DNS.utils.base64;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.*;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.*;


/**
 * Implements a TranscriptionService which uses
 * Whisper and a Python wrapper to do the transcription.
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
    public final static String SINGLE_REQUEST_URL
            = "org.jitsi.jigasi.transcription.whisper.single_request_url";

    /**
     * The config key for http auth user
     */
    public final static String HTTP_AUTH_USER
            = "org.jitsi.jigasi.transcription.whisper.websocket_auth_user";

    /**
     * The config key for http auth password
     */
    public final static String HTTP_AUTH_PASS
            = "org.jitsi.jigasi.transcription.whisper.websocket_auth_password";

    public final static String DEFAULT_SINGLE_REQUEST_URL = "http://localhost:8000/single/";

    private boolean hasHttpAuth = false;

    private String httpAuthUser;

    private String httpAuthPass;

    private String singleRequestUrl;

    private final String meetingId = UUID.randomUUID().toString();

    private final String participantId = UUID.randomUUID().toString();

    ExecutorService singleRequestsExecutorService = Executors.newFixedThreadPool(10);

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
     * No configuration required yet
     */
    public boolean isConfiguredProperly()
    {
        return true;
    }

    public boolean supportsLanguageRouting()
    {
        return false;
    }

    private void getConfig() {
        singleRequestUrl = JigasiBundleActivator.getConfigurationService()
                .getString(SINGLE_REQUEST_URL, DEFAULT_SINGLE_REQUEST_URL);
        httpAuthPass = JigasiBundleActivator.getConfigurationService()
                .getString(HTTP_AUTH_PASS, "");
        httpAuthUser = JigasiBundleActivator.getConfigurationService()
                .getString(HTTP_AUTH_USER, "");
        if (!httpAuthPass.isBlank() && !httpAuthUser.isBlank())
        {
            hasHttpAuth = true;
        }
    }

    @Override
    public void sendSingleRequest(final TranscriptionRequest request,
                                  final Consumer<TranscriptionResult> resultConsumer) {
        this.getConfig();
        String authHeader = httpAuthUser + ":" + httpAuthPass;
        authHeader = Base64.getEncoder().encodeToString(authHeader.getBytes());
        JSONObject payload = new JSONObject();
        payload.put("meeting_id", meetingId);
        payload.put("participant_id", participantId);
        payload.put("language", request.getLocale().toLanguageTag());
        payload.put("audio_chunk", base64.toString(request.getAudio()));
//        try (FileOutputStream output = new FileOutputStream("/Users/rpurdel/single_jigasi.wav", true)) {
//            output.write(request.getAudio());
//        } catch (IOException e){
//            logger.error("Error appending to file");
//        }
        final HttpPost httpPost = new HttpPost(singleRequestUrl);
        httpPost.addHeader("Content-Type", "application/json");
        try
        {
            StringEntity params = new StringEntity(payload.toString());
            httpPost.setEntity(params);
        }
        catch(Exception e)
        {
            logger.error("Failed preparing payload for single request" + e);
        }
        HttpResponse response;
        try (CloseableHttpClient client = HttpClients.createDefault())
        {
            if (hasHttpAuth) {
                httpPost.addHeader("Authorization", authHeader);
            }
            response = client.execute(httpPost);
            String jsonBody = EntityUtils.toString(response.getEntity());
            JSONObject json = new JSONObject(jsonBody);
            JSONArray resultArray = json.getJSONArray("data");
            for (int i = 0; i < resultArray.length(); i++)
            {
                boolean partial = true;
                String result = "";
                JSONObject obj = resultArray.getJSONObject(i);
                String msgType = obj.getString("type");

                if (msgType.equals("final"))
                {
                    partial = false;
                }

                result = obj.getString("text");
                UUID id = UUID.fromString(obj.getString("id"));
                Instant transcriptionStart = Instant.ofEpochMilli(obj.getLong("ts"));
                float stability = obj.getFloat("variance");
                resultConsumer.accept(
                        new TranscriptionResult(
                                null,
                                id,
                                transcriptionStart,
                                partial,
                                request.getLocale().toLanguageTag(),
                                stability,
                                new TranscriptionAlternative(result)
                        )
                );
            }
        }
        catch(Exception e)
        {
            logger.error(e);
        }
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
    public class WhisperWebsocketStreamingSession
            implements StreamingRecognitionSession
    {

        private Participant participant;

        private Session session;
        /* The name of the participant */
        private final String debugName;

        /* Transcription language requested by the user who requested the transcription */
        private String transcriptionTag = "en-US";

        private WhisperWebsocket wsClient;

        private String roomId = "";

        private WhisperConnectionPool connectionPool = null;


        WhisperWebsocketStreamingSession(String debugName, Participant participant)
                throws Exception
        {
            this.connectionPool = WhisperConnectionPool.getInstance();
            this.roomId = participant.getChatMember().getChatRoom().toString();
            this.debugName = debugName;
            this.participant = participant;
            this.wsClient = connectionPool.getConnection(this.roomId, debugName);
            this.session = wsClient.getSession();
            this.wsClient.setTranscriptionTag(this.transcriptionTag);
        }

        public void sendRequest(TranscriptionRequest request)
        {
            try
            {
                ByteBuffer audioBuffer = ByteBuffer.wrap(request.getAudio());
                wsClient.sendAudio(participant, audioBuffer);
            }
            catch (Exception e)
            {
                logger.error("Error while sending websocket request for participant " + debugName, e);
                this.session = null;
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
                logger.info("Disconnecting " + this.debugName + " from Whisper transcription service.");
                this.connectionPool.end(this.roomId, this.debugName);
                this.session = null;
            }
            catch (Exception e)
            {
                logger.error("Error while finalizing websocket connection for participant " + debugName, e);
            }
        }

        public boolean ended()
        {
            return session == null;
        }
    }
}