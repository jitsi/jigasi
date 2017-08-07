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

import java.util.*;

/**
 * A TranscriptionResult which is created when audio has been transcribed
 * by a speech-to-text service.
 *
 * @author Nik Vaessen
 * @author Boris Grozev
 */
public class TranscriptionResult
{
    /**
     * The {@link Participant} whose audio this {@link TranscriptionResult} is
     * a transcription of. Can be null.
     */
    private Participant participant;

    /**
     * The alternative transcriptions of this {@link TranscriptionResult}.
     */
    private final List<TranscriptionAlternative> alternatives
        = new LinkedList<>();

    /**
     * A {@link UUID} to identify this result. If {@link this#isInterim} is
     * true new result, which can either also be interim or final and which
     * belong to this result, are expected to have the same UUID
     */
    private UUID messageID;

    /**
     * Whether this result is an interim result, and thus it should be expected
     * that other {@link TranscriptionResult}s have the same
     * {@link this#messageID} value
     */
    private boolean isInterim;

    /**
     * The language, expected to be represented as a BCP-47 tag, of this
     * result's text.
     */
    private String language;

    /**
     * Create a TranscriptionResult
     *
     * @param participant the participant whose audio was transcribed.
     * @param isInterim whether this result is an interim result
     * @param messageID uuid of this result
     * @param language the language of the text of this result
     * @param alternative the single alternative to add
     */
    public TranscriptionResult(
        Participant participant,
        UUID messageID,
        boolean isInterim,
        String language,
        TranscriptionAlternative alternative)
    {
        this(participant, messageID, isInterim, language);
        if (alternative != null)
        {
            this.alternatives.add(alternative);
        }
    }

    /**
     * Create a TranscriptionResult
     *
     * @param participant the participant whose audio was transcribed.
     * @param isInterim whether this result is an interim result
     * @param messageID uuid of this result
     * @param language the language of the text of this result
     * @param alternatives the alternative transcriptions.
     */
    public TranscriptionResult(
        Participant participant,
        UUID messageID,
        boolean isInterim,
        String language,
        Collection<TranscriptionAlternative> alternatives)
    {
        this(participant, messageID, isInterim, language);
        if (alternatives != null)
        {
            this.alternatives.addAll(alternatives);
        }
    }

    /**
     * Create a TranscriptionResult
     *
     * @param participant the participant whose audio was transcribed.
     * @param isInterim whether this result is an interim result
     * @param messageID uuid of this result
     * @param language the language of the text of this result
     */
    public TranscriptionResult(
        Participant participant,
        UUID messageID,
        boolean isInterim,
        String language)
    {
        this.participant = participant;
        this.messageID = messageID;
        this.isInterim = isInterim;
        this.language = language;
    }

    /**
     * Adds an alternative transcription to this {@link TranscriptionResult}.
     * @param alternative the alternative.
     */
    public void addAlternative(TranscriptionAlternative alternative)
    {
        alternatives.add(alternative);
    }

    /**
     * @return all alternative transcription of this {@link TranscriptionResult}.
     */
    public Collection<TranscriptionAlternative> getAlternatives()
    {
        return alternatives;
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
     * Get the {@link UUID} of this message
     *
     * @return the UUID
     */
    public UUID getMessageID()
    {
        return messageID;
    }

    /**
     * Get whether this result is an interim result
     *
     * @return true if interim result, false otherwise
     */
    public boolean isInterim()
    {
        return isInterim;
    }

    /**
     * Get the language tag of this TranscriptionAlternative's transcription
     * text
     *
     * @return the language tag as a String
     */
    public String getLanguage()
    {
        return language;
    }

    /**
     * {@inheritDoc}
     * </p>
     * In this default implementation we include all alternative transcriptions.
     */
    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder(getName());
        sb.append(": ");
        if (!alternatives.isEmpty())
        {
            sb.append('[')
                .append(alternatives.get(0).getConfidence())
                .append("] ")
                .append(alternatives.get(0).getTranscription());
        }
        return sb.toString();
    }

    /**
     * Set this {@link TranscriptionResult}'s {@link Participant}.
     *
     * @param participant the {@link Participant}.
     */
    public void setParticipant(Participant participant)
    {
        this.participant = participant;
    }

    /**
     * Get this {@link TranscriptionResult}'s {@link Participant}.
     *
     * @return the {@link Participant}.
     */
    public Participant getParticipant()
    {
        return participant;
    }
}
