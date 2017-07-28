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
    protected abstract T formatSpeechEvent(Transcript.SpeechEvent e);

    /**
     * Format a join event to the used format
     *
     * @param e the join event
     * @return the join event formatted in the desired type
     */
    protected abstract T formatJoinEvent(Transcript.TranscriptEvent e);

    /**
     * Format a leave event to the used format
     *
     * @param e the join event
     * @return the leave event formatted in the desired type
     */
    protected abstract T formatLeaveEvent(Transcript.TranscriptEvent e);

    /**
     * Format a raised hand event to the used format
     *
     * @param e the raised hand event
     * @return the raised hand event formatted in the desired type
     */
    protected abstract T formatRaisedHandEvent(Transcript.TranscriptEvent e);

    /**
     *
     */
    public abstract class BaseFormatter
        implements TranscriptHandler.Formatter<T>
    {
        /**
         * A string of the date of when the conference started
         */
        protected String startDate;

        /**
         * A string of the the time of when the conference started
         */
        protected String startTime;

        /**
         * A string of the room name
         */
        protected String roomName;

        /**
         * A list of initial participant names
         */
        protected List<String> initialMembers = new LinkedList<>();

        /**
         * A string of the date and time the conference ended
         */
        protected String endDateAndTime;

        /**
         * A map which maps a timestamp to the given event type
         */
        protected Map<Transcript.TranscriptEvent, T> formattedEvents
            = new HashMap<>();

        @Override
        public TranscriptHandler.Formatter<T> startedOn(
            Transcript.TranscriptEvent event)
        {
            if(event != null)
            {
                this.startDate = event.getDateString();
                this.startTime = event.getTimeString();
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
            List<String> names)
        {
            this.initialMembers.addAll(names);
            return this;
        }

        @Override
        public TranscriptHandler.Formatter<T> speechEvents(
            List<Transcript.SpeechEvent> events)
        {
            for(Transcript.SpeechEvent e : events)
            {
                formattedEvents.put(e, formatSpeechEvent(e));
            }
            return this;
        }

        @Override
        public TranscriptHandler.Formatter<T> joinEvents(
            List<Transcript.TranscriptEvent> events)
        {
            for(Transcript.TranscriptEvent e : events)
            {
                formattedEvents.put(e, formatJoinEvent(e));
            }
            return this;
        }

        @Override
        public TranscriptHandler.Formatter<T> leaveEvents(
            List<Transcript.TranscriptEvent> events)
        {
            for(Transcript.TranscriptEvent e : events)
            {
                formattedEvents.put(e, formatLeaveEvent(e));
            }
            return this;
        }

        @Override
        public TranscriptHandler.Formatter<T> raiseHandEvents(
            List<Transcript.TranscriptEvent> events)
        {
            for(Transcript.TranscriptEvent e : events)
            {
                formattedEvents.put(e, formatRaisedHandEvent(e));
            }
            return this;
        }

        @Override
        public TranscriptHandler.Formatter<T> endedOn(
            Transcript.TranscriptEvent event)
        {
            if(event != null)
            {
                this.endDateAndTime = event.getDateTimeString();
            }
            return this;
        }

        @Override
        public abstract T finish();
    }

}
