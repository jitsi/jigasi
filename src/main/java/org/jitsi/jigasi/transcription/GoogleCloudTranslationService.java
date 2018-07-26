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

import com.google.cloud.translate.*;
import com.google.cloud.translate.Translate.*;

/**
 * Implements a {@link TranslationService} which will use Google Cloud
 * translate API to translate the given text from one language to another.
 *
 * Requires google cloud sdk on the machine running the JVM running
 * this application. See
 * https://cloud.google.com/translate/docs/quickstart
 * for details
 *
 * @author Praveen Kumar Gupta
 */
public class GoogleCloudTranslationService
    implements TranslationService
{

    /**
    * The translation model to be used for translation.
    * Possible values are {@code base} and {@code nmt}
    */
    private static final TranslateOption model = TranslateOption.model("nmt");

    /**
    * Translation service object for accessing the cloud translate API.
    */
    private static final Translate translator
        = TranslateOptions.getDefaultInstance().getService();

    /**
     * {@inheritDoc}
     */
    @Override
    public String translate(String sourceText,
                            String sourceLang, String targetLang)
    {
        TranslateOption srcLang = TranslateOption.sourceLanguage(sourceLang);
        TranslateOption tgtLang = TranslateOption.targetLanguage(targetLang);

        Translation translation = translator.translate(
            sourceText, srcLang, tgtLang, model);

        return translation.getTranslatedText();
    }
}