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
 * An abstract TranscriptHandler which implements the basic storage of the
 * formatting, such that only the abstract methods actually dealing with the
 * content need to be implemented.
 *
 * @author Nik Vaessen
 */
public abstract class AbstractTranscriptHandler<T>
    implements TranscriptHandler<T>
{

    /**
     * Format a speech event to the used format
     *
     * @param e the speech event
     * @return the SpeechEvent formatted in the desired type
     */
    protected abstract T formatSpeechEvent(SpeechEvent e);

    /**
     * Format a join event to the used format
     *
     * @param e the join event
     * @return the join event formatted in the desired type
     */
    protected abstract T formatJoinEvent(TranscriptEvent e);

    /**
     * Format a leave event to the used format
     *
     * @param e the join event
     * @return the leave event formatted in the desired type
     */
    protected abstract T formatLeaveEvent(TranscriptEvent e);

    /**
     * Format a raised hand event to the used format
     *
     * @param e the raised hand event
     * @return the raised hand event formatted in the desired type
     */
    protected abstract T formatRaisedHandEvent(TranscriptEvent e);

    /**
     *
     */
    public abstract class BaseFormatter
        implements TranscriptHandler.Formatter<T>
    {
        /**
         * The instant when the conference started
         */
        protected Instant startInstant;

        /**
         * The instant when the conference ended
         */
        protected Instant endInstant;

        /**
         * A string of the room name
         */
        protected String roomName;

        /**
         * A list of initial participant names
         */
        protected List<Participant> initialMembers = new LinkedList<>();

        /**
         * A map which maps a timestamp to the given event type
         */
        protected Map<TranscriptEvent, T> formattedEvents = new HashMap<>();

        @Override
        public TranscriptHandler.Formatter<T> startedOn(TranscriptEvent event)
        {
            if(event != null && event.getEvent().equals(
                Transcript.TranscriptEventType.START))
            {
                this.startInstant = event.getTimeStamp();
            }
            return this;
        }

        @Override
        public TranscriptHandler.Formatter<T> tookPlaceInRoom(String roomName)
        {
            if(roomName != null)
            {
                this.roomName = roomName;
            }
            return this;
        }

        @Override
        public TranscriptHandler.Formatter<T> initialParticipants(
            List<Participant> participants)
        {
            this.initialMembers.addAll(participants);
            return this;
        }

        @Override
        public TranscriptHandler.Formatter<T> speechEvents(
            List<SpeechEvent> events)
        {
            for(SpeechEvent e : events)
            {
                if(e.getEvent().equals(Transcript.TranscriptEventType.SPEECH))
                {
                    formattedEvents.put(e, formatSpeechEvent(e));
                }
            }
            return this;
        }

        @Override
        public TranscriptHandler.Formatter<T> joinEvents(
            List<TranscriptEvent> events)
        {
            for(TranscriptEvent e : events)
            {
                if(e.getEvent().equals(Transcript.TranscriptEventType.JOIN))
                {
                    formattedEvents.put(e, formatJoinEvent(e));
                }
            }
            return this;
        }

        @Override
        public TranscriptHandler.Formatter<T> leaveEvents(
            List<TranscriptEvent> events)
        {
            for(TranscriptEvent e : events)
            {
                if(e.getEvent().equals(Transcript.TranscriptEventType.LEAVE))
                {
                    formattedEvents.put(e, formatLeaveEvent(e));
                }
            }
            return this;
        }

        @Override
        public TranscriptHandler.Formatter<T> raiseHandEvents(
            List<TranscriptEvent> events)
        {
            for(TranscriptEvent e : events)
            {
                if(e.getEvent().equals(
                    Transcript.TranscriptEventType.RAISE_HAND))
                {
                    formattedEvents.put(e, formatRaisedHandEvent(e));
                }
            }
            return this;
        }

        @Override
        public TranscriptHandler.Formatter<T> endedOn(
            TranscriptEvent event)
        {
            if(event != null && event.getEvent().equals(
                Transcript.TranscriptEventType.END))
            {
                this.endInstant = event.getTimeStamp();
            }
            return this;
        }

        /**
         * Get all the events which were added to this formatter in their
         * formatted version, sorted earliest to latest event
         *
         * @return the sorted list
         */
        protected List<T> getSortedEvents()
        {
            List<TranscriptEvent> sortedKeys =
                new ArrayList<>(formattedEvents.keySet());
            Collections.sort(sortedKeys);

            List<T> sortedEvents = new ArrayList<>(sortedKeys.size());
            for(TranscriptEvent event : sortedKeys)
            {
                sortedEvents.add(formattedEvents.get(event));
            }

            return sortedEvents;
        }

        @Override
        public abstract T finish();
    }

}
