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

import com.google.api.gax.grpc.ApiStreamObserver;
import com.google.api.gax.grpc.StreamingCallable;
import com.google.cloud.speech.spi.v1.SpeechClient;
import com.google.cloud.speech.v1.*;
import com.google.protobuf.ByteString;
import org.jitsi.util.Logger;

import javax.media.format.AudioFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

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
     * ExecutorService which will manage the async requests of audio
     * transcriptions. CashedThreadPool is chosen because sending
     * audio chunks are small short-lived async tasks.
     */
    private ExecutorService executorService = Executors.newCachedThreadPool();

    /**
     * Create a TranscriptionService which will send audio to the google cloud
     * platform to get a transcription
     */
    public GoogleCloudTranscriptionService()
    {
    }

    /**
     * Sends audio as an array of bytes to speech-to-text API of google cloud
     *
     * @param request the TranscriptionRequest which holds the audio to be sent
     * @param resultConsumer a Consumer which will handle the
     *                       TranscriptionResult
     */
    @Override
    public void sent(final TranscriptionRequest request,
                     final Consumer<TranscriptionResult> resultConsumer)
    {
        executorService.submit(() ->
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
                resultConsumer.accept(new TranscriptionResult(transcription));
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        });
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
         * The ApiStreamObserver which will retrieve new incoming transcription
         * results
         */
        private ResponseApiStreamingObserver<StreamingRecognizeResponse>
            responseObserver = new ResponseApiStreamingObserver<>();

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
                    = new RequestApiStreamObserverManager(client,
                                                          responseObserver);
            }
            catch(Exception e)
            {
                e.printStackTrace();
            }
        }

        @Override
        public void give(final TranscriptionRequest request)
        {
            this.service.submit(() -> requestManager.sentRequest(request));
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
            responseObserver.addListener(listener);
        }

    }

    /**
     * A Manager for RequestApiStreamObserver instances.
     * It will make sure a RequestApiStreamObserver will only be used for a
     * minute, as that is the maximum amount of time supported by the Google API
     * Every 50 seconds, a new observer is created, which will overlap with
     * the last 5 seconds of the previous observer. Each observer will last 55
     * seconds
     */
    private static class RequestApiStreamObserverManager
    {
        /**
         * The SpeechClient which will be used to initiate the session
         */
        private SpeechClient client;

        /**
         * The ApiStreamObserver which will retrieve new incoming transcription
         * results
         */
        private ResponseApiStreamingObserver<StreamingRecognizeResponse>
            responseObserver;

        /**
         * The ApiStreamObserver which will send new audio request to be
         * transcribed
         */
        private ApiStreamObserver<StreamingRecognizeRequest>
            currentRequestObserver;

        /**
         * The upcoming ApiStreamObserver which will send new audio request to be
         * transcribed
         */
        private ApiStreamObserver<StreamingRecognizeRequest>
            upcomingRequestObserver;

        /**
         * The timestamp from when the **current**
         */
        private long creationTimeStamp;

        /**
         * Create a new RequestApiStreamObserverManager, which will try
         * to mimic a streaming session of indefinite lenth
         *
         * @param client the SpeechClient with which to open new sessions
         */
        RequestApiStreamObserverManager(SpeechClient client,
                                        ResponseApiStreamingObserver
                                            <StreamingRecognizeResponse>
                                            responseObserver )
        {
            this.client = client;
            this.responseObserver = responseObserver;
        }

        /**
         * Create a new ApiStreamObserver by instantialing it and sending the first
         * request, which contains the configuration
         *
         * @param config the configuration of the session
         * @return the ApiStreamObserver, which will be "open" for the coming
         * 55 seconds
         */
        public ApiStreamObserver<StreamingRecognizeRequest> createObserver(
            RecognitionConfig config)
        {
            // StreamingRecognitionConfig which will hold information
            // about the streaming session, including the RecognitionConfig
            StreamingRecognitionConfig streamingRecognitionConfig =
                StreamingRecognitionConfig.newBuilder()
                    .setConfig(config)
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

            return requestObserver;
        }

        /**
         * Sent an audio request to the streaming sessions
         *
         * @param request the audio to send
         */
        private void sendAudioRequest(TranscriptionRequest request)
        {
            // If the first request with the config has been sent,
            // all other requests need to contain **only** the audio ByteString
            byte[] audio = request.getAudio();
            ByteString audioBytes = ByteString.copyFrom(audio);

            currentRequestObserver.onNext(
                StreamingRecognizeRequest.newBuilder()
                    .setAudioContent(audioBytes)
                    .build());

            if(upcomingRequestObserver != null)
            {
                upcomingRequestObserver.onNext(
                    StreamingRecognizeRequest.newBuilder()
                        .setAudioContent(audioBytes)
                        .build());
            }

            logger.trace("Sent a request");
        }

        /**
         * Sent a request to the streaming observer to be transcribed
         *
         * @param request the request to transcribe
         */
        public void sentRequest(TranscriptionRequest request)
        {
            handleTimeStamp(request);
            sendAudioRequest(request);
        }

        /**
         * Manage sessions based on time passed. If 50 seconds have passed, wind
         * up a new session. If 55 seconds have passed, replace the current one
         *
         * @param request a request such that the congigation can be retrieved
         *                if necessary
         */
        private void handleTimeStamp(TranscriptionRequest request)
        {
            //initial creation
            if(currentRequestObserver == null)
            {
                logger.debug("Creating initial Session");
                currentRequestObserver
                    = createObserver(getRecognitionConfig(request));
                creationTimeStamp = System.currentTimeMillis();
                return;
            }

            long msPassed = System.currentTimeMillis() - creationTimeStamp;
            if(msPassed > 50000 && upcomingRequestObserver == null)
            {
                logger.debug("50 seconds have passed, creating upcoming" +
                    "session");
                upcomingRequestObserver
                    = createObserver(getRecognitionConfig(request));
            }
            else if(msPassed > 55000)
            {
                logger.debug("55 seconds have passed, retiring current" +
                    "session");
                currentRequestObserver.onCompleted();
                currentRequestObserver = upcomingRequestObserver;
                upcomingRequestObserver = null;
                creationTimeStamp = System.currentTimeMillis() - 5000;
            }
        }

        /**
         * Stop the manager
         */
        public void stop()
        {
            if(currentRequestObserver != null)
            {
                currentRequestObserver.onCompleted();
            }
            if(upcomingRequestObserver != null)
            {
                upcomingRequestObserver.onCompleted();
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
         * List of TranscriptionListeners which will be notified when a
         * result comes in
         */
        private final List<TranscriptionListener> listeners = new ArrayList<>();

        @Override
        public void onNext(StreamingRecognizeResponse message)
        {
            logger.debug("received a result");
            StringBuilder builder = new StringBuilder();
            for(StreamingRecognitionResult result :
                message.getResultsList())
            {
                if(result.getAlternativesCount() > 0)
                {
                    builder.append(result.getAlternatives(0).
                        getTranscript());
                    builder.append(" ");
                }
            }

            String transcription = builder.toString().trim();
            sent(new TranscriptionResult(transcription));
        }

        @Override
        public void onError(Throwable t)
        {
            t.printStackTrace();
        }

        @Override
        public void onCompleted()
        {
            for(TranscriptionListener listener : listeners)
            {
                listener.completed();
            }
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
         * Send a TranscriptionResult to each TranscriptionListener
         *
         * @param result the result to sent
         */
        private void sent(TranscriptionResult result)
        {
            for(TranscriptionListener listener : listeners)
            {
                listener.notify(result);
            }
        }

    }

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

}
