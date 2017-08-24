/*
 * Jigasi, the JItsi GAteway to SIP.
 *
 * Copyright @ 2017 Atlassian Pty Ltd
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

import com.google.api.gax.grpc.*;
import com.google.auth.oauth2.*;
import com.google.cloud.speech.spi.v1.*;
import com.google.cloud.speech.v1.*;
import com.google.protobuf.*;
import org.jitsi.jigasi.transcription.action.*;
import org.jitsi.util.*;

import javax.media.format.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

/**
 * Implements a TranscriptionService which will use Google Cloud
 * speech-to-text API to transcribe audio
 *
 * Requires google cloud sdk on the machine running the JVM running
 * this application. See
 * https://cloud.google.com/speech/docs/getting-started
 * for details
 *
 * @author Nik Vaessen
 * @author Damian Minkov
 */
public class GoogleCloudTranscriptionService
    implements TranscriptionService
{

    /**
     * BCP-47 (see https://www.rfc-editor.org/rfc/bcp/bcp47.txt)
     * language tags of the languages supported by Google cloud speech
     * to-text API (See https://cloud.google.com/speech/docs/languages)
     */
    public final static String[] SUPPORTED_LANGUAGE_TAGS = new String[]
        {
            "af-ZA",
            "id-ID",
            "ms-MY",
            "ca-ES",
            "cs-CZ",
            "da-DK",
            "de-DE",
            "en-AU",
            "en-CA",
            "en-GB",
            "en-IN",
            "en-IE",
            "en-NZ",
            "en-PH",
            "en-ZA",
            "en-US",
            "es-AR",
            "es-BO",
            "es-CL",
            "es-CO",
            "es-CR",
            "es-EC",
            "es-SV",
            "es-ES",
            "es-US",
            "es-GT",
            "es-HN",
            "es-MX",
            "es-NI",
            "es-PA",
            "es-PY",
            "es-PE",
            "es-PR",
            "es-DO",
            "es-UY",
            "es-VE",
            "eu-ES",
            "fil-PH",
            "fr-CA",
            "fr-FR",
            "gl-ES",
            "hr-HR",
            "zu-ZA",
            "is-IS",
            "it-IT",
            "lt-LT",
            "hu-HU",
            "nl-NL",
            "nb-NO",
            "pl-PL",
            "pt-BR",
            "pt-PT",
            "ro-RO",
            "sk-SK",
            "sl-SI",
            "fi-FI",
            "sv-SE",
            "vi-VN",
            "tr-TR",
            "el-GR",
            "bg-BG",
            "ru-RU",
            "sr-RS",
            "uk-UA",
            "he-IL",
            "ar-IL",
            "ar-JO",
            "ar-AE",
            "ar-BH",
            "ar-DZ",
            "ar-SA",
            "ar-IQ",
            "ar-KW",
            "ar-MA",
            "ar-TN",
            "ar-OM",
            "ar-PS",
            "ar-QA",
            "ar-LB",
            "ar-EG",
            "fa-IR",
            "hi-IN",
            "th-TH",
            "ko-KR",
            "cmn-Hant-TW",
            "yue-Hant-HK",
            "ja-JP",
            "cmn-Hans-HK",
            "cmn-Hans-CN",
        };

    /**
     * The logger for this class
     */
    private final static Logger logger
            = Logger.getLogger(GoogleCloudTranscriptionService.class);

    /**
     * The maximum amount of alternative transcriptions desired. The server may
     * return fewer than MAXIMUM_DESIRED_ALTERNATIVES.
     * Valid values are 0-30. A value of 0 or 1 will return a maximum of one.
     * If omitted from a {@link RecognitionConfig} will return a maximum of one.
     */
    private final static int MAXIMUM_DESIRED_ALTERNATIVES = 0;

    /**
     * Whether the Google cloud API sends interim, non-final results
     */
    private final static boolean RETRIEVE_INTERIM_RESULTS = true;

    /**
     * Whether the Google Cloud API only listens for a single utterance
     * or continuous to listen once an utterance is over
     */
    private final static boolean SINGLE_UTTERANCE_ONLY = true;

    /**
     * The amount of ms after which a StreamingRecognize session will be closed
     * when no new audio is given. This is to make sure the session retrieves
     * audio in "real-time". This also ensures that participants using push-
     * to-talk do not have delayed results
     */
    private final static int STREAMING_SESSION_TIMEOUT_MS = 2000;

    /**
     * List of <tt>SpeechContext</tt>s to be inserted in
     * the <tt>RecognitionConfig</tt>. This is a list of phrases to be used as
     * a dictionary to assist the speech recognition.
     */
    private static List<SpeechContext> speechContexts = null;

    /**
     * Creates the RecognitionConfig the Google service uses based
     * on the TranscriptionRequest
     *
     * @param request the transcriptionRequest which will need to be transcribed
     * @return the config based on the audio contained in the request
     * @throws UnsupportedOperationException when this service cannot process
     * the given request
     */
    private static RecognitionConfig getRecognitionConfig(TranscriptionRequest
                                                              request)
        throws UnsupportedOperationException
    {
        RecognitionConfig.Builder builder = RecognitionConfig.newBuilder();

        // Set the sampling rate and encoding of the audio
        AudioFormat format = request.getFormat();
        builder.setSampleRateHertz(new Double(format.getSampleRate())
            .intValue());
        switch(format.getEncoding())
        {
            case "LINEAR":
                builder.setEncoding(RecognitionConfig.AudioEncoding.LINEAR16);
                break;
            default:
                throw new IllegalArgumentException("Given AudioFormat" +
                    "has unexpected" +
                    "encoding");
        }

        // set the Language tag
        String languageTag = request.getLocale().toLanguageTag();
        validateLanguageTag(languageTag);
        builder.setLanguageCode(languageTag);

        addSpeechContexts(builder);

        // set the requested alternatives
        builder.setMaxAlternatives(MAXIMUM_DESIRED_ALTERNATIVES);

        return builder.build();
    }

    /**
     * Check whether the given string contains a supported language tag
     *
     * @param tag the language tag
     * @throws UnsupportedOperationException when the google cloud API does not
     * support the given language
     */
    private static void validateLanguageTag(String tag)
        throws UnsupportedOperationException
    {
        for(String supportedTag : SUPPORTED_LANGUAGE_TAGS)
        {
            if(supportedTag.equals(tag))
            {
                return;
            }
        }
        throw new UnsupportedOperationException(tag + " is not a language " +
            "supported by the Google " +
            "Cloud speech-to-text API");
    }

    /**
     * Create a TranscriptionService which will send audio to the google cloud
     * platform to get a transcription
     */
    public GoogleCloudTranscriptionService()
    {
    }

    /**
     * Get whether Google credentials are properly set
     *
     * @return true when able to authenticate to the Google Cloud service, false
     * otherwise
     */
    public boolean isConfiguredProperly()
    {
        try
        {
            GoogleCredentials.getApplicationDefault();
            return true;
        }
        catch (IOException e)
        {
            logger.warn("Google Credentials are not properly set", e);
            return false;
        }
    }

    /**
     * Sends audio as an array of bytes to speech-to-text API of google cloud
     *
     * @param request the TranscriptionRequest which holds the audio to be sent
     * @param resultConsumer a Consumer which will handle the
     *                       TranscriptionResult
     */
    @Override
    public void sendSingleRequest(final TranscriptionRequest request,
                            final Consumer<TranscriptionResult> resultConsumer)
    {
        // Try to create the client, which can throw an IOException
        try
        {
            SpeechClient client = SpeechClient.create();

            RecognitionConfig config = getRecognitionConfig(request);

            ByteString audioBytes = ByteString.copyFrom(request.getAudio());
            RecognitionAudio audio = RecognitionAudio.newBuilder()
                    .setContent(audioBytes)
                    .build();

            RecognizeResponse recognizeResponse =
                    client.recognize(config, audio);

            client.close();

            StringBuilder builder = new StringBuilder();
            for (SpeechRecognitionResult result :
                    recognizeResponse.getResultsList())
            {
                builder.append(result.toString());
                builder.append(" ");
            }

            String transcription = builder.toString().trim();
            resultConsumer.accept(
                    new TranscriptionResult(
                            null,
                            UUID.randomUUID(),
                            false,
                            request.getLocale().toLanguageTag(),
                            0,
                            new TranscriptionAlternative(transcription)));
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    @Override
    public StreamingRecognitionSession initStreamingSession()
        throws UnsupportedOperationException
    {
        return new GoogleCloudStreamingRecognitionSession();
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
     * Initialize speechContexts if needed, by getting all the phrases used
     * by the action handlers to detect commands to handle.
     * Inserts all speechContexts to the <tt>RecognitionConfig.Builder</tt>.
     * @param builder the builder where to add speech contexts.
     */
    private static void addSpeechContexts(RecognitionConfig.Builder builder)
    {
        if (speechContexts == null)
        {
            speechContexts = new ArrayList<>();
            ActionServicesHandler.getInstance().getPhrases()
                .stream().map(ph -> speechContexts.add(
                    SpeechContext.newBuilder().addPhrases(ph).build()));
        }

        speechContexts.stream().map(ctx -> builder.addSpeechContexts(ctx));
    }

    /**
     * A Transcription session for transcribing streams for Google Cloud
     * speech-to-text API
     */
    public class GoogleCloudStreamingRecognitionSession
        implements StreamingRecognitionSession
    {

        /**
         * The SpeechClient which will be used to initiate and to end the
         * session
         */
        private SpeechClient client;

        /**
         * A manager which acts as a ApiStreamObserver which will send new audio
         * request to be transcribed
         */
        private RequestApiStreamObserverManager requestManager;

        /**
         * A single thread which is used to sent all requests to the API.
         * This is needed to reliably sent the first request to the service
         */
        private ExecutorService service = Executors.newSingleThreadExecutor();

        /**
         * Create a new session with the Google Cloud API
         */
        private GoogleCloudStreamingRecognitionSession()
        {
            try
            {
                this.client = SpeechClient.create();
                this.requestManager
                    = new RequestApiStreamObserverManager(client);
            }
            catch(Exception e)
            {
                e.printStackTrace();
            }
        }

        @Override
        public void sendRequest(final TranscriptionRequest request)
        {
            this.service.submit(() -> {
                try
                {
                    requestManager.sentRequest(request);
                }
                catch(Exception e)
                {
                    logger.warn("Not able to send request", e);
                }
            });
            logger.trace("queued request");
        }

        @Override
        public boolean ended()
        {
            return service.isShutdown();
        }

        @Override
        public void end()
        {
            try
            {
                client.close();
                requestManager.stop();
                service.shutdown();
                // Note that we can't close the responseObserver yet
                // as new results can still come in
            }
            catch(Exception e)
            {
                e.printStackTrace();
            }
        }

        @Override
        public void addTranscriptionListener(TranscriptionListener listener)
        {
            requestManager.addListener(listener);
        }

    }

    /**
     * A Manager for RequestApiStreamObserver instances.
     * It will make sure a RequestApiStreamObserver will only be used for a
     * minute, as that is the maximum amount of time supported by the Google API
     */
    private static class RequestApiStreamObserverManager
    {
        /**
         * The SpeechClient which will be used to initiate the session
         */
        private SpeechClient client;

        /**
         * List of TranscriptionListeners which will be notified when a
         * result comes in
         */
        private final List<TranscriptionListener> listeners = new ArrayList<>();

        /**
         * The ApiStreamObserver which will send new audio request to be
         * transcribed
         */
        private ApiStreamObserver<StreamingRecognizeRequest>
            currentRequestObserver;

        /**
         * Lock used to access the currentRequestObserver
         */
        private final Object currentRequestObserverLock = new Object();

        /**
         * Thread used to terminate a session when no new requests are coming in
         */
        private TerminatingSessionThread terminatingSessionThread;

        /**
         * Whether this manager has stopped and will not make new sessions
         * anymore
         */
        private boolean stopped = false;

        /**
         * Create a new RequestApiStreamObserverManager, which will try
         * to mimic a streaming session of indefinite lenth
         *
         * @param client the SpeechClient with which to open new sessions
         */
        RequestApiStreamObserverManager(SpeechClient client)
        {
            this.client = client;
        }

        /**
         * Create a new ApiStreamObserver by instantiating it and sending the
         * first request, which contains the configuration
         *
         * @param config the configuration of the session
         * @return the ApiStreamObserver
         */
        private ApiStreamObserver<StreamingRecognizeRequest> createObserver(
            RecognitionConfig config)
        {
            // Each observer gets its own responseObserver to be able to
            // to get an unique ID
            ResponseApiStreamingObserver<StreamingRecognizeResponse>
                responseObserver =
                new ResponseApiStreamingObserver<StreamingRecognizeResponse>(
                    this,
                    config.getLanguageCode());

            // StreamingRecognitionConfig which will hold information
            // about the streaming session, including the RecognitionConfig
            StreamingRecognitionConfig streamingRecognitionConfig =
                StreamingRecognitionConfig.newBuilder()
                    .setConfig(config)
                    .setInterimResults(RETRIEVE_INTERIM_RESULTS)
                    .setSingleUtterance(SINGLE_UTTERANCE_ONLY)
                    .build();

            // StreamingCallable manages sending the audio and receiving
            // the results
            StreamingCallable<StreamingRecognizeRequest,
                StreamingRecognizeResponse> callable = client
                .streamingRecognizeCallable();

            // An ApiObserver which will be used to send all requests
            // The responses will be delivered to the responseObserver
            // which is already created
            ApiStreamObserver<StreamingRecognizeRequest> requestObserver
                = callable.bidiStreamingCall(responseObserver);

            // Sent the first request which needs to **only** contain the
            // StreamingRecognitionConfig
            requestObserver.onNext(
                StreamingRecognizeRequest.newBuilder()
                    .setStreamingConfig(streamingRecognitionConfig)
                    .build());

            // Create the thread which will cancel this session when
            // it is not receiving audio
            terminatingSessionThread
                = new TerminatingSessionThread(this,
                                                STREAMING_SESSION_TIMEOUT_MS);
            terminatingSessionThread.start();

            return requestObserver;
        }

        /**
         * Sent a request to the streaming observer to be transcribed
         *
         * @param request the request to transcribe
         */
        void sentRequest(TranscriptionRequest request)
        {
            if(stopped)
            {
                logger.warn("not able to send request because" +
                    " manager was already stopped");
                return;
            }

            // If the first request with the config has been sent,
            // all other requests need to contain **only** the audio
            // ByteString
            byte[] audio = request.getAudio();
            ByteString audioBytes = ByteString.copyFrom(audio);

            synchronized(currentRequestObserverLock)
            {
                if(currentRequestObserver == null)
                {
                    if (logger.isDebugEnabled())
                        logger.debug("Created a new session");

                    currentRequestObserver
                        = createObserver(getRecognitionConfig(request));
                }

                currentRequestObserver.onNext(
                    StreamingRecognizeRequest.newBuilder()
                        .setAudioContent(audioBytes)
                        .build());

                terminatingSessionThread.interrupt();
            }
            logger.trace("Sent a request");
        }

        /**
         * Add a listener to the list of listeners to be notified when a new
         * result comes in
         *
         * @param listener the listener to add
         */
        void addListener(TranscriptionListener listener)
        {
            listeners.add(listener);
        }

        /**
         * Get the {@link TranscriptionListener} added to this
         * {@link TranscriptionService.StreamingRecognitionSession}
         *
         * @return the list of {@link TranscriptionListener}
         */
        public List<TranscriptionListener> getListeners()
        {
            return listeners;
        }

        /**
         * Stop the manager
         */
        public void stop()
        {
            stopped = true;
            terminateCurrentSession();
        }

        /**
         * Close the currentRequestObserver if there is one
         */
        void terminateCurrentSession()
        {
            synchronized(currentRequestObserverLock)
            {
                if(currentRequestObserver != null)
                {
                    if (logger.isDebugEnabled())
                        logger.debug("Terminated current session");

                    currentRequestObserver.onCompleted();
                    currentRequestObserver = null;
                }

                if(terminatingSessionThread != null &&
                    terminatingSessionThread.isAlive())
                {
                    terminatingSessionThread.setStopIfInterrupted(true);
                    terminatingSessionThread.interrupt();
                    terminatingSessionThread = null;
                }
            }
        }
    }

    /**
     * This ResponseApiStreamingObserver is used in the
     * StreamingRecognitionSession to retrieve incoming
     * StreamingRecognizeResponses when the Google Cloud API has received
     * enough audio packets to successfully transcribe
     *
     * @param <T> This observer will only ever be used for instances of
     *            StreamingRecognizeResponse
     */
    private static class ResponseApiStreamingObserver
        <T extends StreamingRecognizeResponse>
        implements ApiStreamObserver<T>
    {
        /**
         * The manager which is used to send new audio requests. Should be
         * notified when a final result comes in to be able to start a new
         * session
         */
        private RequestApiStreamObserverManager requestManager;

        /**
         * The language of the speech being provided in the current session
         */
        private String languageTag;

        /**
         * A {@link UUID} which identifies the results (interim and final) of
         * the current session
         */
        private UUID messageID;

        /**
         * Create a ResponseApiStreamingObserver which listens for transcription
         * results
         *
         * @param manager the manager of requests
         */
        ResponseApiStreamingObserver(RequestApiStreamObserverManager manager,
                                     String languageTag)
        {
            this.requestManager = manager;
            this.languageTag = languageTag;

            messageID = UUID.randomUUID();
        }

        @Override
        public void onNext(StreamingRecognizeResponse message)
        {
            if (logger.isDebugEnabled())
                logger.debug("Received a StreamingRecognizeResponse");
            if(message.hasError())
            {
                // it is expected to get an error if the 60 seconds are exceeded
                // without any speech in the audio OR if someone muted their mic
                // and no new audio is coming in
                // thus we cancel the current session and start a new one
                // when new audio comes in
                if (logger.isDebugEnabled())
                    logger.debug(
                        "Received error from StreamingRecognizeResponse: "
                        + message.getError().getMessage());
                requestManager.terminateCurrentSession();
                return;
            }

            // This will happen when SINGLE_UTTERANCE is set to true
            // and the server has detected the end of the user's speech
            // utterance.
            if(isEndOfSingleUtteranceMessage(message) ||
                message.getResultsCount() == 0)
            {
                if (logger.isDebugEnabled())
                    logger.debug(
                        "Received a message with an empty results list");

                requestManager.terminateCurrentSession();
                return;
            }

            List<StreamingRecognitionResult> results = message.getResultsList();

            // If there is a result with is_final=true, it's always the first
            // and there is only ever 1
            StreamingRecognitionResult finalResult = results.get(0);
            if(!finalResult.getIsFinal())
            {
                // handle the interim results and continue waiting for
                // final result
                for(StreamingRecognitionResult interimResult : results)
                {
                    handleResult(interimResult);
                }
                return;
            }

            // should always contains one result
            List<SpeechRecognitionAlternative> alternatives
                = finalResult.getAlternativesList();

            // If empty, the session has reached it's time limit and
            // nothing new was said, but there should be an error in the message
            // so this is never supposed to happen
            if(alternatives.isEmpty())
            {
                logger.warn("Received a list of alternatives which" +
                    " was empty");
                requestManager.terminateCurrentSession();
                return;
            }

            handleResult(finalResult);

            requestManager.terminateCurrentSession();
        }

        /**
         * Get whether a {@link StreamingRecognizeResponse} has an
         * {@link StreamingRecognizeResponse#speechEventType_} of
         * {@link StreamingRecognizeResponse.SpeechEventType#
         * END_OF_SINGLE_UTTERANCE}
         *
         * @param message the message to check
         * @return true if the message has the eventType
         * {@link StreamingRecognizeResponse.SpeechEventType
         * #END_OF_SINGLE_UTTERANCE}, false otherwise
         */
        private boolean isEndOfSingleUtteranceMessage(
            StreamingRecognizeResponse message)
        {
            return message.getSpeechEventType().
                equals(StreamingRecognizeResponse.SpeechEventType.
                    END_OF_SINGLE_UTTERANCE);
        }

        /**
         * Handle a single {@link StreamingRecognitionResult} by creating
         * a {@link TranscriptionResult} based on the result and notifying all
         * all registered {@link TranscriptionListener}s
         *
         * @param result the result to handle
         */
        private void handleResult(StreamingRecognitionResult result)
        {
            List<SpeechRecognitionAlternative> alternatives
                = result.getAlternativesList();

            if(alternatives.isEmpty())
            {
                return;
            }

            TranscriptionResult transcriptionResult = new TranscriptionResult(
                null,
                this.messageID,
                !result.getIsFinal(),
                this.languageTag,
                result.getStability());

            for(SpeechRecognitionAlternative alternative : alternatives)
            {
                transcriptionResult.addAlternative(
                    new TranscriptionAlternative(
                        alternative.getTranscript(),
                        alternative.getConfidence()));
            }

            sent(transcriptionResult);
        }

        @Override
        public void onError(Throwable t)
        {
            logger.warn("Received an error from the Google Cloud API", t);
            requestManager.terminateCurrentSession();
        }

        @Override
        public void onCompleted()
        {
            for(TranscriptionListener listener : requestManager.getListeners())
            {
                listener.completed();
            }
        }

        /**
         * Send a TranscriptionResult to each TranscriptionListener
         *
         * @param result the result to sent
         */
        private void sent(TranscriptionResult result)
        {
            for(TranscriptionListener listener : requestManager.getListeners())
            {
                listener.notify(result);
            }

            // notify for a final transcription result, by providing it to all
            // action handlers
            if (!result.isInterim())
            {
                ActionServicesHandler.getInstance()
                    .notifyActionServices(result);
            }
        }
    }

    /**
     * This thread is used to cancel a RequestObserver when no new audio is
     * being given. The thread needs to be kept-alive by using the
     * {@link TerminatingSessionThread#interrupt()} ()} method, which resets the
     * timer to 0
     */
    private static class TerminatingSessionThread
        extends Thread
    {

        /**
         * The manager which will be told to terminate the current session
         * when the specified amount of time has passed
         */
        private RequestApiStreamObserverManager manager;

        /**
         * The amount of ms after which the manager should be told to terminate
         * the session
         */
        private int terminateAfter;

        /**
         * If this is set to true, interrupting the thread will kill the thread
         * instead of resetting the counter
         */
        private boolean stopIfInterrupted = false;

        /**
         * Create a Thread which will tell the given manager to terminate its
         * thread as soon as the given amount of ms has passed, unless
         * {@link TerminatingSessionThread#interrupt()} ()} has been called
         *
         * @param manager the manager
         * @param ms the amount of time in ms
         */
        TerminatingSessionThread(RequestApiStreamObserverManager manager,
                                        int ms)
        {
            this.manager = manager;
            this.terminateAfter = ms;
        }

        @Override
        public void run()
        {
            try
            {
                sleep(terminateAfter);
                manager.terminateCurrentSession();
            }
            catch(InterruptedException e)
            {
                if(!stopIfInterrupted)
                {
                    run();
                }
            }
        }

        /**
         * Set whether the thread should kill itself when interrupted or reset
         * the counter
         *
         * @param stopIfInterrupted If true, thread will kill itself when
         *                          interrupted, otherwise counter will be reset
         */
        void setStopIfInterrupted(boolean stopIfInterrupted)
        {
            this.stopIfInterrupted = stopIfInterrupted;
        }
    }

}
