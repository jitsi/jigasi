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

import net.java.sip.communicator.service.protocol.*;
import org.jitsi.jigasi.*;
import org.jitsi.util.*;

import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;

/**
 * An abstract TranscriptHandler which implements the basic storage for the
 * formatting, such that only the abstract methods actually dealing with the
 * content need to be implemented.
 *
 * @author Nik Vaessen
 */
public abstract class AbstractTranscriptPublisher<T>
    implements TranscriptPublisher,
               TranscriptionResultPublisher
{
    /**
     * Property name for the directory in which the final transcripts should be
     * stored
     */
    public final static String P_NAME_TRANSCRIPT_DIRECTORY
        = "org.jitsi.jigasi.transcription.AbstractTranscriptPublisher." +
        "DIRECTORY";

    /**
     * Property name for the basic URL which the server will use to serve the
     * final transcript
     */
    public final static String P_NAME_TRANSCRIPT_BASE_URL
        = "org.jitsi.jigasi.transcription.AbstractTranscriptPublisher." +
        "BASE_URL";

    /**
     * The property name for the boolean value whether the URL should be
     * advertised or not
     */
    public final static String P_NAME_ADVERTISE_URL
        =  "org.jitsi.jigasi.transcription.AbstractTranscriptPublisher." +
        "ADVERTISE_URL";

    /**
     * The default for the url
     */
    public final static String TRANSCRIPT_BASE_URL_DEFAULT_VALUE
        = "http://localhost/transcripts/";

    /**
     * The default value for the directory to save the final transcripts in
     * is the current working directory
     */
    public final static String TRANSCRIPT_DIRECTORY_DEFAULT_VALUE
        = "/var/lib/jigasi/transcripts";

    /**
     * By default do not advertise the URL
     */
    public final static boolean ADVERTISE_URL_DEFAULT_VALUE = false;

    /**
     * The logger of this class
     */
    private static final Logger logger
        = Logger.getLogger(AbstractTranscriptPublisher.class);

    /**
     * Get a file name for a transcript, which includes the time and some ID
     * to make it hard to guess
     *
     * @return the file name
     */
    protected static String generateHardToGuessFileName()
    {
        return "transcript_" + UUID.randomUUID() + "_" + Instant.now();
    }

    /**
     * Send a message to the muc room
     *
     * @param chatRoom the chatroom to send the message to
     * @param message the message to send
     */
    protected void sendMessage(ChatRoom chatRoom, T message)
    {
        if (chatRoom == null)
        {
            logger.error("Cannot sent message as chatRoom is null");
            return;
        }

        String messageString = message.toString();
        Message chatRoomMessage = chatRoom.createMessage(messageString);
        try
        {
            chatRoom.sendMessage(chatRoomMessage);
            logger.debug("Sending message: \"" + messageString + "\"");
        }
        catch (OperationFailedException e)
        {
            logger.warn("Failed to send message " + messageString, e);
        }
    }

    /**
     * Save a transcript in a file
     *
     * @param transcript the transcript to save
     */
    protected void saveTranscriptToFile(String fileName, T transcript)
    {
        File logDir = Paths.get(getLogDirPath()).toFile();

        // Try to make the directory
        if(!logDir.exists())
        {
            if(!logDir.mkdir())
            {
                logger.warn("Was not able to safe a transcript because" +
                    " unable to make a directory called " + logDir);
                return;
            }
        }

        // If there is a file with the directory name, we can't make the
        // directory and thus we cannot save the transcript
        if(logDir.exists() && !logDir.isDirectory())
        {
            logger.warn("Was not able to safe a transcript because" +
                " there is a file called " + logDir);
            return;
        }

        File t = new File(logDir, fileName);
        try(FileWriter writer = new FileWriter(t))
        {
            writer.write(transcript.toString());
            logger.info("Wrote final transcript to " + t);
        }
        catch(IOException e)
        {
            logger.warn("Unable to write transcript to file " + t, e);
        }
    }

    /**
     * Get the string representing the path of the directory wherein the
     * final transcripts should be stored
     *
     * @return the path as a String
     */
    public String getLogDirPath()
    {
        return JigasiBundleActivator.getConfigurationService()
            .getString(P_NAME_TRANSCRIPT_DIRECTORY,
                TRANSCRIPT_DIRECTORY_DEFAULT_VALUE);
    }

    /**
     * Get the string representing the base URL the server will use to host the
     * formatted {@link Transcript}
     *
     * @return the base URL as a String
     */
    protected String getBaseURL()
    {
       return JigasiBundleActivator.getConfigurationService()
           .getString(P_NAME_TRANSCRIPT_BASE_URL,
               TRANSCRIPT_BASE_URL_DEFAULT_VALUE);
    }

    /**
     * Get whether the URL should be advertises when a
     * {@link TranscriptionGatewaySession} joins a room
     *
     * @return true when the URL should be advertises, false otherwise
     */
    protected boolean advertiseURL()
    {
        return JigasiBundleActivator.getConfigurationService()
            .getBoolean(P_NAME_ADVERTISE_URL,
                ADVERTISE_URL_DEFAULT_VALUE);
    }

    /**
     * Get a new {@link BaseFormatter}
     *
     * @return a new instance of the {@link BaseFormatter}
     */
    abstract BaseFormatter getFormatter();

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
     * A formatter to give information stored in a {@link Transcript} to this
     * {@link TranscriptPublisher}
     */
    public abstract class BaseFormatter
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

        /**
         * Format a transcript which includes when it started.
         * Ignored when the given event does not have the event type
         * {@link Transcript.TranscriptEventType#START}
         *
         * @param event a event without a name which has the timestamp of when
         *              the conference started
         * @return this formatter
         */
        BaseFormatter startedOn(TranscriptEvent event)
        {
            if(event != null && event.getEvent().equals(
                Transcript.TranscriptEventType.START))
            {
                this.startInstant = event.getTimeStamp();
            }
            return this;
        }

        /**
         * Format a transcript which includes the room name
         *
         * @param roomName the name of the room
         * @return this formatter
         */
        BaseFormatter tookPlaceInRoom(String roomName)
        {
            if(roomName != null)
            {
                this.roomName = roomName;
            }
            return this;
        }

        /**
         * Format a transcript which includes the list of initial participant
         *
         * @param participants the list of participants
         * @return this formatter
         */
        BaseFormatter initialParticipants(List<Participant> participants)
        {
            this.initialMembers.addAll(participants);
            return this;
        }

        /**
         * Format a transcript which includes what everyone who was transcribed
         * said. Ignored when the given event does not have the event type
         * {@link Transcript.TranscriptEventType#SPEECH}
         *
         * @param events a list of events containing the transcriptions
         * @return this formatter
         */
        BaseFormatter speechEvents(List<SpeechEvent> events)
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

        /**
         * Format a transcript which includes when anyone joined the conference.
         * Ignored when the given event does not have the event type
         * {@link Transcript.TranscriptEventType#JOIN}
         *
         * @param events a list of events containing the transcriptions
         * @return this formatter
         */
        BaseFormatter joinEvents(List<TranscriptEvent> events)
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

        /**
         * Format a transcript which includes when anyone left the conference.
         * Ignored when the given event does not have the event type
         * {@link Transcript.TranscriptEventType#LEAVE}
         *
         * @param events a list of events containing the transcriptions
         * @return this formatter
         */
        BaseFormatter leaveEvents(List<TranscriptEvent> events)
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


        /**
         * Format a transcript which includes when anyone raised their hand
         * to speak. Ignored when the given event does not have the event type
         * {@link Transcript.TranscriptEventType#RAISE_HAND}
         *
         * @param events a list of events containing the transcriptions
         * @return this formatter
         */
        BaseFormatter raiseHandEvents(List<TranscriptEvent> events)
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

        /**
         * Format a transcript which includes when it ended. Ignored when the
         * given event does not have the event type
         * {@link Transcript.TranscriptEventType#END}
         *
         * @param event a event without a name which has the timestamp of when
         *              the conference ended
         * @return this formatter
         */
        BaseFormatter endedOn(TranscriptEvent event)
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

        /**
         * Finish the formatting by returning the formatted transcript
         *
         * @return the transcript
         */
        abstract T finish();
    }

    public abstract class BasePromise
        implements Promise
    {

        @Override
        public boolean hasDescription()
        {
            return advertiseURL();
        }

        @Override
        public String getDescription()
        {
            return "Transcript will be available after the conference at " +
                getBaseURL() + getFileName() + ".\n";
        }

        /**
         * Get the file name which will be used to store the {@link Transcript}
         *
         * @return the file name as a string
         */
        protected abstract String getFileName();
    }

}
