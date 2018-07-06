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

import java.util.*;

/**
 * A TranslationResult created after translating a {@link TranscriptionResult}
 * using a {@link TranslationService}.
 *
 * @author Praveen Kumar Gupta
 */
public class TranslationResult {

    /**
     * The language tag, of this result's text.
     */
    private String language;

    /**
     * A {@link UUID} to identify this result. It is expected to have the
     * same UUID as that of the original {@link TranscriptionResult}.
     */
    private UUID messageID;

    /**
     * The {@link Participant} whose audio's translation is represented
     * by this {@link TranslationResult}.
     */
    private Participant participant;

    /**
     * The translated final transcript message in the given language.
     */
    private String text;

    /**
     * Initializes a {@link TranslationResult} object with the participant,
     * messageID and translated text, language.
     *
     * @param result
     * @param language
     * @param translation
     */
    public TranslationResult(TranscriptionResult result,
                             String language,
                             String translation)
    {
        this.participant = result.getParticipant();
        this.messageID = result.getMessageID();
        this.language = language;
        this.text = translation;
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
     * Get the {@link UUID}(messageID) of this message.
     *
     * @return the UUID
     */
    public UUID getMessageID()
    {
        return messageID;
    }

    /**
     * Get the name of the participant saying the audio
     *
     * @return the name
     */
    public String getName()
    {
        return participant == null
                ? Participant.UNKNOWN_NAME
                : participant.getName();
    }

    /**
     * Get this {@link TranslationResult}'s {@link Participant}.
     *
     * @return the {@link Participant}.
     */
    public Participant getParticipant()
    {
        return participant;
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
}
