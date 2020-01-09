package org.jitsi.jigasi.transcription;

import com.google.api.gax.rpc.*;
import com.google.cloud.speech.v1.*;
import com.google.protobuf.*;
import com.timgroup.statsd.*;
import org.jitsi.jigasi.*;
import org.jitsi.jigasi.transcription.action.*;
import org.jitsi.utils.logging.*;

import java.util.*;
import java.util.concurrent.*;

/**
 * A Transcription session for transcribing streams for Google Cloud
 * speech-to-text API
 */
class GoogleCloudStreamingRecognitionSession
    implements TranscriptionService.StreamingRecognitionSession
{
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
     * The logger for this class
     */
    private final static Logger logger
        = Logger.getLogger(GoogleCloudTranscriptionService.class);

    /**
     * The SpeechClient which will be used to initiate and to end the
     * session
     */
    private SpeechClient client;

    /**
     * Extra string added to every log.
     */
    private final String debugName;

    /**
     * A manager which acts as a ApiStreamObserver which will send new audio
     * request to be transcribed
     */
    private RequestApiStreamObserverManager requestManager;

    /**
     * The parent {@code GoogleCloudTranscriptionService} instance.
     */
    private final GoogleCloudTranscriptionService service;

    /**
     * A single thread which is used to sent all requests to the API.
     * This is needed to reliably sent the first request to the service
     */
    private ExecutorService executorService = Executors.newSingleThreadExecutor();

    /**
     * Create a new session with the Google Cloud API
     */
    GoogleCloudStreamingRecognitionSession(
            GoogleCloudTranscriptionService service, String debugName)
    {
        this.debugName = debugName;
        this.service = service;

        try
        {
            this.client = SpeechClient.create();
            this.requestManager = new RequestApiStreamObserverManager(client);
        }
        catch(Exception e)
        {
            logger.error(debugName + ": error creating stream observer", e);
        }
    }

    @Override
    public void sendRequest(final TranscriptionRequest request)
    {
        this.executorService.submit(() -> {
            try
            {
                requestManager.sentRequest(request);
            }
            catch(Exception e)
            {
                logger.warn(debugName + ": not able to send request", e);
            }
        });
        logger.trace(debugName + ": queued request");
    }

    @Override
    public boolean ended()
    {
        return executorService.isShutdown();
    }

    @Override
    public void end()
    {
        try
        {
            client.close();
            requestManager.stop();
            executorService.shutdown();
            // Note that we can't close the responseObserver yet
            // as new results can still come in
        }
        catch(Exception e)
        {
            logger.error(debugName + ": error ending session", e);
        }
    }

    @Override
    public void addTranscriptionListener(TranscriptionListener listener)
    {
        requestManager.addListener(listener);
    }

    /**
     * Class to keep track of the cost of using the Google Cloud speech-to-text
     * API.
     */
    private class GoogleCloudCostLogger
    {
        /**
         * The length of a cost interval of the google cloud speech-to-text API
         */
        private final static int INTERVAL_LENGTH_MS = 15000;

        /**
         * The aspect to log the information to.
         */
        private final static String ASPECT_INTERVAL
                = "google_cloud_speech_15s_intervals";

        /**
         * The client to send statistics to
         */
        private final StatsDClient client = JigasiBundleActivator.getDataDogClient();

        /**
         * Keep track of the time already send
         */
        private long summedTime = 0;

        /**
         * Tell the {@link GoogleCloudCostLogger} that a certain length of audio
         * was send.
         *
         * @param ms the length of the audio chunk sent to the API
         */
        synchronized void increment(long ms)
        {
            if(ms < 0)
            {
                return;
            }

            summedTime += ms;
        }

        /**
         * Tell the logger a session has closed, meaning the total interval
         * length can now be computed
         */
        synchronized void sessionEnded()
        {
            // round up to 15 second intervals
            int intervals15s = 1 + (int) (summedTime  / INTERVAL_LENGTH_MS);

            if(client != null)
            {
                client.count(ASPECT_INTERVAL, intervals15s);
            }

            logger.info(debugName + ": sent " + summedTime + "ms to speech API, " +
                                "for a total of " + intervals15s + " intervals");

            summedTime = 0;
        }

    }

    /**
     * A Manager for RequestApiStreamObserver instances.
     * It will make sure a RequestApiStreamObserver will only be used for a
     * minute, as that is the maximum amount of time supported by the Google API
     */
    private class RequestApiStreamObserverManager
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
         * Used to log the cost of every request which is send
         */
        private final GoogleCloudCostLogger costLogger;

        /**
         * Create a new RequestApiStreamObserverManager, which will try
         * to mimic a streaming session of indefinite length
         *
         * @param client the SpeechClient with which to open new sessions
         */
        RequestApiStreamObserverManager(SpeechClient client)
        {
            this.client = client;
            this.costLogger = new GoogleCloudCostLogger();
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
                    new ResponseApiStreamingObserver<>(
                            this,
                            config.getLanguageCode());

            // StreamingRecognitionConfig which will hold information
            // about the streaming session, including the RecognitionConfig
            StreamingRecognitionConfig streamingRecognitionConfig =
                    StreamingRecognitionConfig.newBuilder()
                            .setConfig(config)
                            .setInterimResults(RETRIEVE_INTERIM_RESULTS)
                            .setSingleUtterance(!service.isVideoModelEnabled() &&
                                                        SINGLE_UTTERANCE_ONLY)
                            .build();

            // StreamingCallable manages sending the audio and receiving
            // the results
            BidiStreamingCallable<StreamingRecognizeRequest,
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
                = new TerminatingSessionThread(
                        this, STREAMING_SESSION_TIMEOUT_MS);

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
                logger.warn(debugName + ": not able to send request because" +
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
                        logger.debug(debugName + ": created a new session");

                    currentRequestObserver
                        = createObserver(service.getRecognitionConfig(request));
                }

                costLogger.increment(request.getDurationInMs());

                currentRequestObserver.onNext(
                        StreamingRecognizeRequest.newBuilder()
                                .setAudioContent(audioBytes)
                                .build());

                terminatingSessionThread.interrupt();
            }
            logger.trace(debugName + ": sent a request");
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
        List<TranscriptionListener> getListeners()
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
                if (currentRequestObserver != null)
                {
                    if (logger.isDebugEnabled())
                        logger.debug(debugName + ": terminated current session");

                    currentRequestObserver.onCompleted();
                    currentRequestObserver = null;

                    costLogger.sessionEnded();
                }

                if (terminatingSessionThread != null &&
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

    /**
     * This ResponseApiStreamingObserver is used in the
     * StreamingRecognitionSession to retrieve incoming
     * StreamingRecognizeResponses when the Google Cloud API has received
     * enough audio packets to successfully transcribe
     *
     * @param <T> This observer will only ever be used for instances of
     *            StreamingRecognizeResponse
     */
    private class ResponseApiStreamingObserver
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
                logger.debug(debugName + ": received a StreamingRecognizeResponse");
            if(message.hasError())
            {
                // it is expected to get an error if the 60 seconds are exceeded
                // without any speech in the audio OR if someone muted their mic
                // and no new audio is coming in
                // thus we cancel the current session and start a new one
                // when new audio comes in
                if (logger.isDebugEnabled())
                    logger.debug(
                            debugName + ": received error from StreamingRecognizeResponse: "
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
                            debugName + ": received a message with an empty results list");

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
                logger.warn(debugName + ": received a list of alternatives which" +
                                    " was empty");
                requestManager.terminateCurrentSession();
                return;
            }

            handleResult(finalResult);

            requestManager.terminateCurrentSession();
        }

        /**
         * Get whether a {@link StreamingRecognizeResponse} has an
         * {@link StreamingRecognizeResponse.SpeechEventType} of
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
            logger.warn(debugName + ": received an error from the Google Cloud API", t);
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
}
