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

/**
 * Describe an TranscriptEvent which took place at a certain time and
 * can belong to a named person
 *
 * @author Nik Vaessen
 */
public class TranscriptEvent
    implements Comparable<TranscriptEvent>
{
    /**
     * The time when the event took place
     */
    private Instant timeStamp;

    /**
     * The participant who caused this event. Will be null if
     * {@link this#event} is {@link Transcript.TranscriptEventType#START} or
     * {@link Transcript.TranscriptEventType#END}
     */
    private Participant participant;

    /**
     * The type of event this object is representing
     */
    private Transcript.TranscriptEventType event;

    /**
     * Create a TranscriptEvent which has a TimeStamp and a name
     *
     * @param timeStamp the time the event took place
     * @param participant the name of who caused this event
     * @param event the event this object will describe
     */
    TranscriptEvent(Instant timeStamp, Participant participant,
                    Transcript.TranscriptEventType event)
    {
        this.timeStamp = timeStamp;
        this.participant = participant;
        this.event = event;
    }

    /**
     * Create a TranscriptEvent which has a TimeStamp. This is only for
     * events {@link Transcript.TranscriptEventType#START} and
     * {@link Transcript.TranscriptEventType#END}
     *
     * @param timeStamp the time the event took place
     * @param event either {@link Transcript.TranscriptEventType#START} or
     * {@link Transcript.TranscriptEventType#END}
     * @throws IllegalArgumentException when event is not correct type
     */
    TranscriptEvent(Instant timeStamp, Transcript.TranscriptEventType event)
    {
        if(Transcript.TranscriptEventType.END.equals(event)
            || Transcript.TranscriptEventType.WILL_END.equals(event)
            || Transcript.TranscriptEventType.START.equals(event))
        {
            this.timeStamp = timeStamp;
            this.event = event;
        }
        else
        {
            throw new IllegalArgumentException("TranscriptEvent " + event +
                " needs a participant");
        }
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
        return participant.getName();
    }

    /**
     * Get the id (jid) of the person who caused this event
     *
     * @return the id as a String
     */
    public String getID()
    {
        return participant.getId();
    }

    /**
     * Get what kind of event this is

     * @return the kind of event
     */
    public Transcript.TranscriptEventType getEvent()
    {
        return event;
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
     * Returns the participant for this event if any.
     * @return the participant for this event if any.
     */
    public Participant getParticipant()
    {
        return participant;
    }
}