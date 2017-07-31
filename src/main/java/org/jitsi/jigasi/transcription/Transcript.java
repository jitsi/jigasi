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
import java.time.format.*;
import java.util.*;

/**
 * A transcript of a conference. An instance of this class will hold the
 * complete transcript once a conference is over
 *
 * @author Nik Vaessen
 */
public class Transcript
    implements TranscriptionListener
{
    /**
     * Formats an Instant to <hour:minute:second> in the timezone the machine
     * is running on
     */
    private static final DateTimeFormatter timeFormatter
        = DateTimeFormatter
        .ofLocalizedTime(FormatStyle.MEDIUM)
        .withZone(ZoneOffset.UTC);

    /**
     * Formats an Instant to a date in the preferred way of the Zone the
     * machine is running in
     */
    private static final DateTimeFormatter dateFormatter
        = DateTimeFormatter
        .ofLocalizedDate(FormatStyle.MEDIUM)
        .withZone(ZoneOffset.UTC);

    /**
     * Formats an Instant to a date and time in the timezone the machine is
     * running in
     */
    private static final DateTimeFormatter dateTimeFormatter
        = DateTimeFormatter
        .ofLocalizedDateTime(FormatStyle.MEDIUM)
        .withZone(ZoneOffset.UTC);

    /**
     * The list of all received speechEvents
     */
    private final List<SpeechEvent> speechEvents = new LinkedList<>();

    /**
     * The list of all received
     */
    private final List<TranscriptEvent> joinedEvents = new LinkedList<>();

    /**
     * The list of all received
     */
    private final List<TranscriptEvent> leftEvents = new LinkedList<>();

    /**
     * The list of all received
     */
    private final List<TranscriptEvent> raisedHandEvents = new LinkedList<>();

    /**
     * An event without a name, which specifies when transcription started
     */
    private TranscriptEvent started;

    /**
     *  An event without a name, which specifies when transcription ended
     */
    private TranscriptEvent ended;

    /**
     * A list of names of participants who are present when the transcription
     * started
     */
    private List<String> initialParticipantNames = new LinkedList<>();

    /**
     * The name of the room of the conference this transcript belongs to
     */
    private String roomName;

    /**
     * Create a Transcript object which can store events related to a conference
     * which can then be formatted into an actual transcript by a
     * {@link TranscriptHandler}
     *
     * Speech can be stored using the TranscriptionListener
     * Other events, such as people joining and leaving, can be stored using
     * their instance methods
     *
     * @param roomName the name of the room of the conference if this transcript
     */
    Transcript(String roomName)
    {
        this.roomName = roomName;
    }

    /**
     * Create a Transcript object which can store events related to a conference
     * which can then be formatted into an actual transcript by a
     * {@link TranscriptHandler}
     *
     * Speech can be stored using the TranscriptionListener
     * Other events, such as people joining and leaving, can be stored using
     * their instance methods
     *
     */
    Transcript()
    {
        this.roomName = "";
    }

    @Override
    public void notify(TranscriptionResult result)
    {
        if(started != null)
        {
            SpeechEvent speechEvent = new SpeechEvent(Instant.now(), result);
            speechEvents.add(speechEvent);
        }
    }

    @Override
    public void completed()
    {
        // nothing do to
    }

    /**
     * Notify the transcript that the conference started at this exact moment
     * Will be ignored if already told the conference started
     *
     * @param initialNames the names of the participants currently in the room
     */
    public void started(List<String> initialNames)
    {
        if(started == null)
        {
            this.started = new TranscriptEvent(Instant.now());
            this.initialParticipantNames.addAll(initialNames);
        }
    }


    /**
     * Notify the transcript that the conference started at this exact moment
     * Will be ignored if already told the conference started
     *
     * @param roomName the roomName of the conference
     * @param initialNames the names of the participants currently in the room
     */
    protected void started(String roomName, List<String> initialNames)
    {
        if(started == null)
        {
            this.roomName = roomName;
            this.started = new TranscriptEvent(Instant.now());
            this.initialParticipantNames.addAll(initialNames);
        }
    }

    /**
     * Notify the transcript that the conference ended at this exact moment
     * Will be ignored if this transcript has not been told the conference
     * started or if it already ended
     */
    protected void ended()
    {
        if(started != null && ended == null)
        {
            this.ended = new TranscriptEvent(Instant.now());
        }
    }

    /**
     * Notify the transcript that someone joined at this exact moment
     * Will be ignored if this transcript was told the conference ended
     *
     * @param name the name of that person
     */
    public void notifyJoined(String name)
    {
        if(started != null && ended == null)
        {
            joinedEvents.add(new TranscriptEvent(Instant.now(), name));
        }
    }

    /**
     * Notify the transcript that someone left at this exact moment
     * Will be ignored if this transcript was told the conference ended
     *
     * @param name the name of that person
     */
    public void notifyLeft(String name)
    {
        if(started != null && ended == null)
        {
            leftEvents.add(new TranscriptEvent(Instant.now(), name));
        }
    }

    /**
     * Notify the transcript that someone raised their hand at this exact moment
     * Will be ignored if this transcript was told the conference ended
     *
     * @param name the name of that person
     */
    public void notifyRaisedHand(String name)
    {
        if(started != null && ended == null)
        {
            raisedHandEvents.add(new TranscriptEvent(Instant.now(), name));
        }
    }

    /**
     * Get a formatted transcript of the events stored by this object
     *
     * @param handler the handler being able to format
     * @param <T> the type in which the transcript will be stored
     * @return a formatted transcript with type T
     */
    public <T> T getTranscript(TranscriptHandler<T> handler)
    {
        return handler.format()
            .startedOn(started)
            .initialParticipants(initialParticipantNames)
            .tookPlaceInRoom(roomName)
            .speechEvents(speechEvents)
            .raiseHandEvents(raisedHandEvents)
            .joinEvents(joinedEvents)
            .leaveEvents(leftEvents)
            .endedOn(ended)
            .finish();
    }

    /**
     * Save the events stored by this transcript using the given handler
     *
     * @param handler the handler which is able to format and store the
     *                transcript
     * @param <T> the type the handler uses to format the transcript
     */
    public <T> void saveTranscript(TranscriptHandler<T> handler)
    {
        handler.publish(getTranscript(handler));
    }

    /**
     * Describe an TranscriptEvent which took place at a certain time and
     * can belong to a named person
     */
    public class TranscriptEvent
        implements Comparable<TranscriptEvent>
    {
        /**
         * The time when the event took place
         */
        private Instant timeStamp;

        /**
         * The name of the person who caused this event
         */
        private String name;

        /**
         * Create a TranscriptEvent which has a TimeStamp and a name
         *
         * @param timeStamp the time the event took place
         * @param name  the name of who caused this event
         */
        TranscriptEvent(Instant timeStamp, String name)
        {
            this.timeStamp = timeStamp;
            this.name = name;
        }

        /**
         * Create a TranscriptEvent which has a TimeStamp and a name
         *
         * @param timeStamp the time the event took place
         */
        TranscriptEvent(Instant timeStamp)
        {
            this(timeStamp, "");
        }

        /**
         * Get the Instant of when this event took place
         *
         * @return the instant
         */
        public Instant getTimeStamp()
        {
            return timeStamp;
        }

        /**
         * Get the name of the person who caused this event
         *
         * @return the name as a String
         */
        public String getName()
        {
            return name;
        }

        /**
         * Events can be compared by the TimeStamp they took place. When another
         * event took place earlier than this one, it is compared as smaller
         * (< 0), when it took place at exactly the same time, they are equal
         * (0), and when another event took place later, it is bigger (> 0)
         *
         * @return negative int when smaller, 0 when equal, positive int when
         * bigger
         * @throws NullPointerException when other is null
         */
        @Override
        public int compareTo(TranscriptEvent other)
            throws NullPointerException
        {
            return this.timeStamp.compareTo(other.timeStamp);
        }

        /**
         * Overwritten to have expected behaviour with compareTo method
         */
        @Override
        public boolean equals(Object obj)
        {
            return obj instanceof TranscriptEvent &&
                this.timeStamp.equals(((TranscriptEvent) obj).timeStamp);
        }

        /**
         * Get a string representing the hours, minutes and seconds of when this
         * event took place, in the time-zone of the machine running the app
         *
         * @return a time string
         */
        public String getTimeString()
        {
            return timeFormatter.format(this.timeStamp);
        }

        /**
         * Get a string representing the date this event took place, in the
         * time-zone and format of the machine running the app
         *
         * @return a date string
         */
        public String getDateString()
        {
            return dateFormatter.format(this.timeStamp);
        }

        /**
         * Get a string representing the date and hours, minutes and seconds of
         * when this event took place, in the time-zone of the machine running
         * the app
         *
         * @return a date time string
         */
        public String getDateTimeString()
        {
            return dateTimeFormatter.format(this.timeStamp);
        }

    }

    /**
     * A SpeechEvent extends a normal TranscriptEvent by including a
     * TranscriptionResult
     */
    public class SpeechEvent
        extends TranscriptEvent
    {
        /**
         * The transcriptionResult
         */
        private TranscriptionResult result;

        /**
         * Create a ResultHolder with a given TranscriptionResult and timestamp
         *
         * @param timeStamp the time when the result was received
         * @param result the result which was received
         */
        private SpeechEvent(Instant timeStamp, TranscriptionResult result)
        {
            super(timeStamp, result.getName());
            this.result = result;
        }

        /**
         * Get the TranscriptionResult this holder is holding
         *
         * @return the result
         */
        public TranscriptionResult getResult()
        {
            return result;
        }

    }

}
