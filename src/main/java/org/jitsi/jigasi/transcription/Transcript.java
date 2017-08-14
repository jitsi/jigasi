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
 * A transcript of a conference. An instance of this class will hold the
 * complete transcript once a conference is over
 *
 * @author Nik Vaessen
 */
public class Transcript
    implements TranscriptionListener
{
    /**
     * Events which can take place in the transcript
     */
    public enum TranscriptEventType
    {
        START,
        SPEECH,
        JOIN,
        LEAVE,
        RAISE_HAND,
        END
    }

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
     * A list of participants who are present when the transcription
     * started
     */
    private List<Participant> initialParticipantNames = new LinkedList<>();

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
     * @param initialParticipants the names of the participants currently in
     *                            the room
     */
    public void started(List<Participant> initialParticipants)
    {
        if(started == null)
        {
            this.started
                = new TranscriptEvent(Instant.now(), TranscriptEventType.START);
            this.initialParticipantNames.addAll(initialParticipants);
        }
    }


    /**
     * Notify the transcript that the conference started at this exact moment
     * Will be ignored if already told the conference started
     *
     * @param roomName the roomName of the conference
     * @param initialParticipants the  participants currently in the room
     */
    protected void started(String roomName,
                           List<Participant> initialParticipants)
    {
        if(started == null)
        {
            this.roomName = roomName;
            this.started
                = new TranscriptEvent(Instant.now(), TranscriptEventType.START);
            this.initialParticipantNames.addAll(initialParticipants);
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
            this.ended
                = new TranscriptEvent(Instant.now(), TranscriptEventType.END);
        }
    }

    /**
     * Notify the transcript that someone joined at this exact moment.
     * Will be ignored if this transcript was told the conference ended
     *
     * @param participant the participant who joined
     */
    public void notifyJoined(Participant participant)
    {
        if(started != null && ended == null)
        {
            joinedEvents.add(new TranscriptEvent(Instant.now(), participant,
                TranscriptEventType.JOIN));
        }
    }

    /**
     * Notify the transcript that someone left at this exact moment.
     * Will be ignored if this transcript was told the conference ended
     *
     * @param participant the participant who left
     */
    public void notifyLeft(Participant participant)
    {
        if(started != null && ended == null)
        {
            leftEvents.add(new TranscriptEvent(Instant.now(), participant,
                TranscriptEventType.LEAVE));
        }
    }

    /**
     * Notify the transcript that someone raised their hand at this exact moment
     * Will be ignored if this transcript was told the conference ended
     *
     * @param participant the participant who raised their hand
     * */
    public void notifyRaisedHand(Participant participant)
    {
        if(started != null && ended == null)
        {
            raisedHandEvents.add(new TranscriptEvent(Instant.now(), participant,
                TranscriptEventType.RAISE_HAND));
        }
    }

    /**
     * Get a formatted transcript of the events stored by this object
     *
     * @param publisher a publisher which has a formatter to create a transcript
     *                  in the desired type
     * @param <T> the type in which the transcript will be stored
     * @return a formatted transcript with type T
     */
    public <T> T getTranscript(AbstractTranscriptPublisher<T> publisher)
    {
        return publisher.getFormatter()
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

}
