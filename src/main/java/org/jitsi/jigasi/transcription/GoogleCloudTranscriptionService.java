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

import com.google.api.gax.rpc.*;
import com.google.auth.oauth2.*;
import com.google.cloud.speech.v1.*;
import com.google.protobuf.*;
import com.timgroup.statsd.*;
import org.jitsi.jigasi.*;
import org.jitsi.jigasi.transcription.action.*;
import org.jitsi.utils.logging.*;

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
     * Property name to determine whether to use the Google Speech API's
     * video model
     */
    private final static String P_NAME_USE_VIDEO_MODEL
        = "org.jitsi.jigasi.transcription.USE_VIDEO_MODEL";

    /**
     * The default value for the property USE_VIDEO_MODEL
     */
    private final static boolean DEFAULT_VALUE_USE_VIDEO_MODEL = false;

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
     * List of <tt>SpeechContext</tt>s to be inserted in
     * the <tt>RecognitionConfig</tt>. This is a list of phrases to be used as
     * a dictionary to assist the speech recognition.
     */
    private List<SpeechContext> speechContexts = null;

    /**
     * Whether to use the more expensive video model when making
     * requests.
     */
    private boolean useVideoModel;

    /**
     * Creates the RecognitionConfig the Google service uses based
     * on the TranscriptionRequest
     *
     * @param request the transcriptionRequest which will need to be transcribed
     * @return the config based on the audio contained in the request
     * @throws UnsupportedOperationException when this service cannot process
     * the given request
     */
    RecognitionConfig getRecognitionConfig(TranscriptionRequest request)
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

        // set the model to use. It will default to a cheaper model with
        // lower performance when not set.
        if(useVideoModel)
        {
            if(logger.isDebugEnabled())
            {
                logger.debug("Using the more expensive video model");
            }
            builder.setModel("video");
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
     * Create a TranscriptionService which will send audio to the google cloud
     * platform to get a transcription
     */
    public GoogleCloudTranscriptionService()
    {
        useVideoModel = JigasiBundleActivator.getConfigurationService()
            .getBoolean(P_NAME_USE_VIDEO_MODEL, DEFAULT_VALUE_USE_VIDEO_MODEL);
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
            logger.error("Error sending single req", e);
        }
    }

    @Override
    public StreamingRecognitionSession initStreamingSession(
            Participant participant)
        throws UnsupportedOperationException
    {
        return new GoogleCloudStreamingRecognitionSession(this, participant.getDebugName());
    }

    boolean isVideoModelEnabled() {
        return this.useVideoModel;
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
    private void addSpeechContexts(RecognitionConfig.Builder builder)
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
}
