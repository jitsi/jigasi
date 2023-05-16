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

import java.time.*;
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
    final private UUID messageID;

    /**
     * The time when the audio started for this result.
     */
    final private Instant timeStamp;


    /**
     * Whether this result is an interim result, and thus it should be expected
     * that other {@link TranscriptionResult}s have the same
     * {@link this#messageID} value
     */
    final private boolean isInterim;

    /**
     * The language, expected to be represented as a BCP-47 tag, of this
     * result's text.
     */
    final private String language;

    /**
     * The stability of this result. It is an estimate of the likelihood that
     * the recognizer will not change its guess about this interim result.
     * Values range from 0.0 (completely unstable) to 1.0 (completely stable).
     * This field is only provided for interim results.
     * The default of 0.0 is a sentinel value indicating stability was not set.
     */
    final private double stability;

    /**
     * Create a TranscriptionResult
     *
     * @param participant the participant whose audio was transcribed.
     * @param isInterim whether this result is an interim result
     * @param messageID uuid of this result
     * @param timeStamp the timestamp when the audio for this transcript started.
     * @param language the language of the text of this result
     * @param alternative the single alternative to add
     * @param stability the stability if this result. Only > 0 when
     * {@link this#isInterim()} is true
     */
    public TranscriptionResult(
        Participant participant,
        UUID messageID,
        Instant timeStamp,
        boolean isInterim,
        String language,
        double stability,
        TranscriptionAlternative alternative)
    {
        this(participant, messageID, timeStamp, isInterim, language, stability);
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
     * @param timeStamp the timestamp when the audio for this transcript started.
     * @param language the language of the text of this result
     * @param alternatives the alternative transcriptions.
     * @param stability the stability if this result. Only > 0 when
     * {@link this#isInterim()} is true
     */
    public TranscriptionResult(
        Participant participant,
        UUID messageID,
        Instant timeStamp,
        boolean isInterim,
        String language,
        double stability,
        Collection<TranscriptionAlternative> alternatives)
    {
        this(participant, messageID, timeStamp, isInterim, language, stability);
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
     * @param timeStamp the timestamp when the audio for this transcript started.
     * @param language the language of the text of this result
     * @param stability the stability if this result. Only > 0 when
     * {@link this#isInterim()} is true
     */
    public TranscriptionResult(
        Participant participant,
        UUID messageID,
        Instant timeStamp,
        boolean isInterim,
        String language,
        double stability)
    {
        this.participant = participant;
        this.messageID = messageID;
        this.timeStamp = timeStamp;
        this.isInterim = isInterim;
        this.language = language;
        this.stability = stability;
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
     * Get the stability of the result. This value is only > 0 when
     * {@link this#isInterim()} is true.
     * Values range from 0.0 (completely unstable) to 1.0 (completely stable).
     * This field is only provided for interim results.
     * The default of 0.0 is a sentinel value indicating stability was not set.
     *
     * @return a value between 0 and 1 indicated the likelihood that this result
     * will change for further results. 0 means that the value is not actually
     * set
     */
    public double getStability()
    {
        return stability;
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

    /**
     * Returns the real timestamp of this result.
     * @return the timestamp when the audio for this transcription started.
     */
    public Instant getTimeStamp()
    {
        return timeStamp;
    }
}
