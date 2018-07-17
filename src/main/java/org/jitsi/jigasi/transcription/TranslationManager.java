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

import org.jitsi.jigasi.*;

import java.util.*;

/**
 * This class manages the translations to be done by the transcriber.
 *
 * @author Praveen Kumar Gupta
 */
public class TranslationManager
    implements TranscriptionListener
{

    /**
     * Property name for getting a target language for translation.
     */
    public final static String P_NAME_DEFAULT_TARGET_LANGUAGE
        = "org.jitsi.jigasi.transcription.DEFAULT_TARGET_LANGUAGE";

    /**
     * Whether to translate text before sending results in the target language.
     */
    public final static String DEFAULT_TARGET_LANGUAGE
        = "";

    /**
     * List of target languages for translating the transcriptions.
     */
    private ArrayList<String> languages  = new ArrayList<>();

    /**
     * List of listeners to be notified about a new TranslationResult.
     */
    private ArrayList<TranslationResultListener> listeners = new ArrayList<>();

    /**
     * The translationService to be used for translations.
     */
    private final TranslationService translationService;

    /**
     * Initializes the translationManager with a TranslationService
     * and adds the default target language to the list.
     *
     * @param service to be used by the TranslationManger
     */
    public TranslationManager(TranslationService service)
    {
        translationService = service;
        String defaultTargetLanguage = getTargetLanguage();

        if(!defaultTargetLanguage.isEmpty())
        {
            addTargetLanguage(defaultTargetLanguage);
        }
    }

    /**
     * Adds a {@link TranslationResultListener} to the list of
     * listeners to be notified of a TranslationResult.
     *
     * @param listener to be added.
     */
    public void addListener(TranslationResultListener listener)
    {
        listeners.add(listener);
    }

    /**
     * Adds a language tag to the list of target languages for
     * translation.
     *
     * @param language to be added to the list
     */
    public void addTargetLanguage(String language)
    {
        languages.add(language);
    }

    /**
     * Translates the received {@link TranscriptionResult} into required languages
     * and returns a list of {@link TranslationResult}s.
     *
     * @param result the TranscriptionResult notified to the TranslationManager
     * @return list of TranslationResults
     */
    public ArrayList<TranslationResult> getTranslations(
        TranscriptionResult result)
    {
        ArrayList<TranslationResult> translatedResults
            = new ArrayList<>();
        for(String language : languages)
        {
            String translatedText = translationService.translate(
                result.getAlternatives().iterator().next().getTranscription(),
                result.getLanguage().substring(0,2),
                language);

            translatedResults.add(new TranslationResult(
                result,
                language,
                translatedText));
        }
        return translatedResults;
    }

    /**
     * Translates the received {@link TranscriptionResult} into the
     * target languages and notifies the {@link TranslationResultListener}'s
     * of the {@link TranslationResult}s.
     *
     * @param result the result which has come in.
     */
    @Override
    public void notify(TranscriptionResult result)
    {
        if(!result.isInterim())
        {
            ArrayList<TranslationResult> translations
                = getTranslations(result);
            for (TranslationResultListener listener : listeners)
            {
                for (TranslationResult translation : translations)
                    listener.notify(translation);
            }
        }
    }

    @Override
    public void completed()
    {
        languages.clear();
    }

    /**
     * Get the default target language for translation.
     *
     * @return "" if disabled, otherwise the target language tag.
     */
    private String getTargetLanguage()
    {
        return JigasiBundleActivator.getConfigurationService()
            .getString(P_NAME_DEFAULT_TARGET_LANGUAGE, DEFAULT_TARGET_LANGUAGE);
    }
}
