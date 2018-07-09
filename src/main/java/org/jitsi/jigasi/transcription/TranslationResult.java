/*
 * Jigasi, the JItsi GAteway to SIP.
 *
 * Copyright @ 2018 Atlassian Pty Ltd
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

/**
 * A TranslationResult created after translating a {@link TranscriptionResult}
 * using a {@link TranslationService}.
 *
 * @author Praveen Kumar Gupta
 */
public class TranslationResult
{

    /**
     * The language tag of this result's text.
     */
    private String language;

    /**
     * The translated final transcript message in the given language.
     */
    private String text;

    /**
     * {@link TranscriptionResult} whose translation in a particular language
     * is represented by this {@link TranslationResult} object.
     */
    private TranscriptionResult transcriptionResult;

    /**
     * Initializes a {@link TranslationResult} object with the participant,
     * messageID and translated text, language.
     *
     * @param result the {@link TranscriptionResult} for this translation.
     * @param lang the target language for translation.
     * @param translation the translated text in the target language.
     */
    public TranslationResult(TranscriptionResult result,
                             String lang,
                             String translation)
    {
        transcriptionResult = result;
        language = lang;
        text = translation;
    }

    /**
     * Get the language tag of this {@link TranslationResult}.
     *
     * @return the language tag as a String
     */
    public String getLanguage()
    {
        return language;
    }

    /**
     * Get the translated text.
     *
     * @return the translated text.
     */
    public String getTranslatedText()
    {
        return text;
    }

    /**
     * Get the {@link TranscriptionResult} whose translation is represented
     * by this object.
     *
     * @return transcriptionResult
     */
    public TranscriptionResult getTranscriptionResult()
    {
        return transcriptionResult;
    }
}
