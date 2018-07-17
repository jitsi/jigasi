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
 * This interface allows for translation text from the source language to the
 * target language.
 *
 * @author Praveen Kumar Gupta
 */
public interface TranslationService
{

    /**
     * Translates the given text from the source language to target language.
     *
     * @param sourceText the text to be translated.
     * @param sourceLang the language of the text to be translated.
     * @param targetLang the target language for translating the text.
     * @return the translated string of the text.
     */
    String translate(String sourceText, String sourceLang, String targetLang);
}
