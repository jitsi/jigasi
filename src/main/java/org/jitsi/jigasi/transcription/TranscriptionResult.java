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
 * by a speech-to-text service. It holds the request which result an instance
 * of this class represents
 *
 * @author Nik Vaessen
 * @author Boris Grozev
 */
public class TranscriptionResult
{
    /**
     * The {@link Participant} whose audio this {@link TranscriptionResult} is
     * a transcription of.
     */
    private Participant participant;

    /**
     * The alternative transcriptions of this {@link TranscriptionResult}.
     */
    private final Collection<TranscriptionAlternative> alternatives
        = new LinkedList<>();

    /**
     * Create a TranscriptionResult of a TranscriptionRequest which
     * will hold the text of the audio
     *
     * @param participant the participant whose audio was transcribed.
     * @param alternatives the alternative transcriptions.
     */
    public TranscriptionResult(
            Participant participant,
            Collection<TranscriptionAlternative> alternatives)
    {
        this.participant = participant;
        if (alternatives != null)
        {
            this.alternatives.addAll(alternatives);
        }
    }

    /**
     * Create a TranscriptionResult of a TranscriptionRequest which
     * will hold the text of the audio
     *
     * @param participant the participant whose audio was transcribed.
     * @param alternative the single alternative transcription to add.
     */
    public TranscriptionResult(
        Participant participant,
        TranscriptionAlternative alternative)
    {
        this.participant = participant;
        alternatives.add(alternative);
    }

    /**
     * Create a TranscriptionResult of a TranscriptionRequest which
     * will hold the text of the audio
     *
     * @param alternative the single alternative transcription to add.
     */
    public TranscriptionResult(TranscriptionAlternative alternative)
    {
        participant = null;
        alternatives.add(alternative);
    }

    /**
     * Create a TranscriptionResult of a TranscriptionRequest which
     * will hold the text of the audio
     *
     * @param alternatives the alternative transcriptions.
     */
    public TranscriptionResult(
        Collection<TranscriptionAlternative> alternatives)
    {
        this(null, alternatives);
    }

    /**
     * Create a TranscriptionResult of a TranscriptionRequest which
     * will hold the text of the audio
     *
     * @param participant the participant whose audio was transcribed.
     */
    public TranscriptionResult(Participant participant)
    {
        this.participant = participant;
    }

    /**
     * Create a TranscriptionResult of a TranscriptionRequest which
     * will hold the text of the audio
     */
    public TranscriptionResult()
    {
        participant = null;
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
     * {@inheritDoc}
     * </p>
     * In this default implementation we include all alternative transcriptions.
     */
    @Override
    public String toString()
    {
        String name = getName();
        if (name == null)
        {
            name = "Unknown"; //TODO use jid
        }

        StringBuilder sb = new StringBuilder(name);
        sb.append(": ");
        for (TranscriptionAlternative alternative : alternatives)
        {
            sb.append("\n[").append(alternative.getConfidence()).append("] ")
                    .append(alternative.getTranscription());
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
}
