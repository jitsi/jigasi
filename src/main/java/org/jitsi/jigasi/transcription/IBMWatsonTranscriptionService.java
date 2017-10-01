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

import com.ibm.watson.developer_cloud.http.*;
import com.ibm.watson.developer_cloud.speech_to_text.v1.*;
import com.ibm.watson.developer_cloud.speech_to_text.v1.model.*;
import com.ibm.watson.developer_cloud.speech_to_text.v1.model.Transcript;
import com.ibm.watson.developer_cloud.speech_to_text.v1.websocket.*;
import org.jitsi.jigasi.*;
import org.jitsi.util.*;

import javax.media.format.*;
import java.io.*;
import java.util.*;
import java.util.function.*;

/**
 * Implements a TranscriptionService which will use IBM watson's
 * speech-to-text API to transcribe audio
 *
 * Requires IBM Watson sdk on the machine running the JVM running
 * this application.
 *
 * @author Nik Vaessen
 */
public class IBMWatsonTranscriptionService
    implements TranscriptionService
{

    /**
     * The logger for this class
     */
    private final static Logger logger
        = Logger.getLogger(IBMWatsonTranscriptionService.class);

    /**
     * Property name for username in credential JSON of IBM Watson
     * speech-to-text service.
     */
    public final static String P_NAME_USERNAME
        = "org.jitsi.jigasi.transcription.IBM.CREDENTIALS_USERNAME";

    /**
     * Property name for password in credential JSON of IBM Watson
     * speech-to-text service.
     */
    public final static String P_NAME_PASSWORD
        = "org.jitsi.jigasi.transcription.IBM.CREDENTIALS_PASSWORD";

    /**
     * The maximum amount of alternative transcriptions desired.
     */
    private final static int MAXIMUM_DESIRED_ALTERNATIVES = 0;

    /**
     * The amount of seconds before IBM watson will time out the session when
     * the given audio does not contain any speech.
     * -1 will keep connection open forever. (until manually closed)
     */
    private final static int TIMEOUT_NO_SPEECH = 60; // seconds

    /**
     * Build the {@link RecognizeOptions} object which needs be passed along with
     * the audio based on a {@link TranscriptionRequest}.
     *
     * @param request the request to build the {@link RecognizeOptions} for
     * @param inter whether interum results should be sent. Note that interim
     *              the final transcript of an utterance is also interim!
     * @return the {@link RecognizeOptions}
     */
    private static RecognizeOptions getRecognitionOptions(
        TranscriptionRequest request, boolean inter)
    {
        RecognizeOptions.Builder builder = new RecognizeOptions.Builder();

        AudioFormat format = request.getFormat();
        switch(format.getEncoding())
        {
            case "LINEAR":
                builder.contentType(HttpMediaType.AUDIO_PCM + "; rate="
                    + (int)format.getSampleRate());
                break;
            case "FLAC":
                builder.contentType(HttpMediaType.AUDIO_FLAC);
                break;
            default:
                throw new IllegalArgumentException("Given AudioFormat " +
                    "has unexpected" +
                    "encoding");
        }

        if(!request.getLocale().toLanguageTag().equals("en-US"))
        {
            throw new IllegalArgumentException("Currently only english is " +
                "implemented by IBMWatsonTranscriptionService.");
        }

        builder.maxAlternatives(MAXIMUM_DESIRED_ALTERNATIVES);
        builder.inactivityTimeout(TIMEOUT_NO_SPEECH);
        builder.interimResults(inter);

        return builder.build();
    }

    /**
     * The class connecting to IBM Watson speech-to-text service
     */
    private SpeechToText service;

    /**
     * Create a new {@link TranscriptionService} based on IBM watson's speech-
     * to-text SDK
     */
    public IBMWatsonTranscriptionService()
    {
        String username = JigasiBundleActivator.getConfigurationService()
            .getString(P_NAME_USERNAME);

        String password = JigasiBundleActivator.getConfigurationService()
            .getString(P_NAME_PASSWORD);

        service = new SpeechToText(username, password);
    }

    @Override
    public boolean supportsFragmentTranscription()
    {
        return true;
    }

    @Override
    public void sendSingleRequest(TranscriptionRequest request,
                                  Consumer<TranscriptionResult> resultConsumer)
        throws UnsupportedOperationException
    {
        ByteArrayInputStream stream
            = new ByteArrayInputStream(request.getAudio());

        service.recognizeUsingWebSocket(
            stream,
            getRecognitionOptions(request, false),
            new BaseRecognizeCallback()
            {
                @Override
                public void onTranscription(SpeechResults speechResults)
                {
                    if(!speechResults.isFinal())
                    {
                        return;
                    }

                    TranscriptionResult result =  new TranscriptionResult(
                        null,
                        UUID.randomUUID(),
                        false,
                        request.getLocale().toLanguageTag(),
                        0);

                    List<Transcript> transcripts = speechResults.getResults();

                    if(transcripts.isEmpty())
                    {
                        throw new IllegalArgumentException("Received a result "+
                            "without valid list of transcripts");
                    }

                    Transcript transcript = transcripts.get(0);

                    for(SpeechAlternative alternative :
                            transcripts.get(0).getAlternatives())
                    {

                        result.addAlternative(new TranscriptionAlternative(
                            alternative.getTranscript(),
                            alternative.getConfidence()));
                    }

                    resultConsumer.accept(result);
                }
            });
    }

    @Override
    public boolean supportsStreamRecognition()
    {
        return true;
    }

    @Override
    public StreamingRecognitionSession initStreamingSession()
        throws UnsupportedOperationException
    {
        return new IBMWatsonStreamingRecognitionSession();
    }

    @Override
    public boolean isConfiguredProperly()
    {
        return true; // there does not seem to be any nice way to check if
                     // given username and password are valid
    }

    /**
     * Implemenent a {@link TranscriptionService.StreamingRecognitionSession}
     * for IBM Watson's speech-to-text API which can continuously accept new
     * audio fragments.
     */
    public class IBMWatsonStreamingRecognitionSession
        extends BaseRecognizeCallback
        implements TranscriptionService.StreamingRecognitionSession
    {

        /**
         * List of {@link TranscriptionListener}s to notify when a new result
         * comes in
         */
        private List<TranscriptionListener> listeners = new ArrayList<>();

        /**
         * The {@link OutputStream} where we write new audio to transcribe into.
         * Should be <t>null</t> when a new connection to the sdk has to be made
         * when new audio comes in
         */
        private PipedOutputStream outputStream;

        /**
         * Flag which will be set to true once this session is ended and will
         * no longer accept new audio
         */
        private boolean closed = false;

        /**
         * Create a {@link TranscriptionService.StreamingRecognitionSession}
         * for IBM Watson's speech-to-text API which can continuously accept new
         * audio fragments.
         */
        private IBMWatsonStreamingRecognitionSession()
        {
        }

        @Override
        public void sendRequest(TranscriptionRequest request)
        {
            if(closed)
            {
                return;
            }

            // Create a new connection to the API whenever outputStream is
            // undefined
            if(outputStream == null)
            {
                try
                {
                    outputStream = new PipedOutputStream();
                    service.recognizeUsingWebSocket(
                        new PipedInputStream(outputStream),
                        getRecognitionOptions(request, true),
                        this);
                }
                catch(IOException e)
                {
                    outputStream = null;
                    e.printStackTrace();
                }
            }

            try
            {
                outputStream.write(request.getAudio());
            }
            catch(IOException e)
            {
                e.printStackTrace();
            }
        }

        @Override
        public void end()
        {
            closeCurrentOutputStream();
            closed = true;
        }

        @Override
        public boolean ended()
        {
            return closed;
        }

        @Override
        public void addTranscriptionListener(TranscriptionListener listener)
        {
            listeners.add(listener);
        }

        @Override
        public void onTranscription(SpeechResults speechResults)
        {
            List<Transcript> transcripts = speechResults.getResults();

            if(transcripts.size() != 1)
            {
                logger.warn("SpeechResults object is not expected to " +
                    "have a Transcript list size of " + transcripts.size());
                return;
            }

            Transcript transcript = transcripts.get(0);

            boolean isFinalResult= transcript.isFinal();

            TranscriptionResult result = new TranscriptionResult(
                null,
                UUID.randomUUID(),
                !isFinalResult,
                "en-us",
                0);

            for(SpeechAlternative alternative: transcript.getAlternatives())
            {
                result.addAlternative(
                    new TranscriptionAlternative(
                        alternative.getTranscript(),
                        isFinalResult ? alternative.getConfidence() : 0));
                        // throws nullpointer when not final result
            }

            for(TranscriptionListener listener : listeners)
            {
                listener.notify(result);
            }
        }

        @Override
        public void onError(Exception e)
        {
            logger.error("Received error from IBM Watson Speech-to-Text" +
                " service ", e);
            closeCurrentOutputStream();
        }

        @Override
        public void onDisconnected()
        {
            if(logger.isTraceEnabled())
            {
                logger.trace("Closing current outputStream as IBM is " +
                    "disconnecting current connection");
            }

            closeCurrentOutputStream();
        }

        @Override
        public void onTranscriptionComplete()
        {
            if(logger.isTraceEnabled())
            {
                logger.trace("Trainscripti");
            }
        }

        @Override
        public void onConnected()
        {
            if(logger.isTraceEnabled())
            {
                logger.trace("Connection to speech-to-text service" +
                    " established");
            }
            super.onConnected();
        }

        @Override
        public void onInactivityTimeout(RuntimeException runtimeException)
        {
            if(logger.isTraceEnabled())
            {
                logger.trace("Inactivity time-out: ", runtimeException);
            }
        }

        @Override
        public void onListening()
        {
            if(logger.isTraceEnabled())
            {
                logger.trace("IBM endpoint is listening to audio");
            }
        }

        /**
         * Closes the current {@link PipedOutputStream}
         */
        private void closeCurrentOutputStream()
        {
            try
            {
                if(outputStream != null)
                {
                    outputStream.close();
                }
            }
            catch(IOException e)
            {
                e.printStackTrace();
            }
            finally
            {
                outputStream = null;
            }
        }
    }

}
