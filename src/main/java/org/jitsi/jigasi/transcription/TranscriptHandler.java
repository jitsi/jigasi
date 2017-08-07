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
 * A TranscriptHandler can build a Transcript in a desired format and
 * publish it to a desired location
 *
 * @param <T> the type wherein the transcript will be made
 *
 * @author Nik Vaessen
 */
public interface TranscriptHandler<T>
{
    /**
     * Start building a formatted transcript
     *
     * @return the builder
     */
    Formatter<T> format();

    /**
     * Format a single result into
     *
     * @param result the result
     * @return the formatted result
     */
    T formatTranscriptionResult(TranscriptionResult result);

    /**
     * Publish a formatted transcript to a desired location
     *
     * @param transcript the formatted transcript to publish
     */
    void publish(T transcript);

    /**
     * The Formatter which used a fluent API
     *
     * @param <T> the type wherein the transcript will be formatted
     */
    interface Formatter<T>
    {
        /**
         * Format a transcript which includes when it started.
         * Ignored when the given event does not have the event type
         * {@link Transcript.TranscriptEventType#START}
         *
         * @param event a event without a name which has the timestamp of when
         *              the conference started
         * @return this formatter
         */
        Formatter<T> startedOn(TranscriptEvent event);

        /**
         * Format a transcript which includes the room name
         *
         * @param roomName the name of the room
         * @return this formatter
         */
        Formatter<T> tookPlaceInRoom(String roomName);

        /**
         * Format a transcript which includes the list of initial participant
         *
         * @param participants the list of participants
         * @return this formatter
         */
        Formatter<T> initialParticipants(List<Participant> participants);

        /**
         * Format a transcript which includes what everyone who was transcribed
         * said. Ignored when the given event does not have the event type
         * {@link Transcript.TranscriptEventType#SPEECH}
         *
         * @param events a list of events containing the transcriptions
         * @return this formatter
         */
        Formatter<T> speechEvents(List<SpeechEvent> events);

        /**
         * Format a transcript which includes when anyone joined the conference.
         * Ignored when the given event does not have the event type
         * {@link Transcript.TranscriptEventType#JOIN}
         *
         * @param events a list of events containing the transcriptions
         * @return this formatter
         */
        Formatter<T> joinEvents(List<TranscriptEvent> events);

        /**
         * Format a transcript which includes when anyone left the conference.
         * Ignored when the given event does not have the event type
         * {@link Transcript.TranscriptEventType#LEAVE}
         *
         * @param events a list of events containing the transcriptions
         * @return this formatter
         */
        Formatter<T> leaveEvents(List<TranscriptEvent> events);

        /**
         * Format a transcript which includes when anyone raised their hand
         * to speak. Ignored when the given event does not have the event type
         * {@link Transcript.TranscriptEventType#RAISE_HAND}
         *
         * @param events a list of events containing the transcriptions
         * @return this formatter
         */
        Formatter<T> raiseHandEvents(List<TranscriptEvent> events);

        /**
         * Format a transcript which includes when it ended. Ignored when the
         * given event does not have the event type
         * {@link Transcript.TranscriptEventType#END}
         *
         * @param event a event without a name which has the timestamp of when
         *              the conference ended
         * @return this formatter
         */
        Formatter<T> endedOn(TranscriptEvent event);

        /**
         * Finish the formatting by returning the formatted transcript
         *
         * @return the transcript
         */
        T finish();
    }
}
