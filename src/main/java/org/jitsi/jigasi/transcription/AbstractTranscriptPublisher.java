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

import net.java.sip.communicator.impl.protocol.jabber.*;
import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.protocol.Message;
import org.jitsi.jigasi.*;
import org.jitsi.service.libjitsi.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.service.neomedia.device.*;
import org.jitsi.service.neomedia.recording.*;
import org.jitsi.utils.logging.*;

import java.io.*;
import java.nio.charset.*;
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
        = "org.jitsi.jigasi.transcription.DIRECTORY";

    /**
     * Property name for the basic URL which the server will use to serve the
     * final transcript
     */
    public final static String P_NAME_TRANSCRIPT_BASE_URL
        = "org.jitsi.jigasi.transcription.BASE_URL";

    /**
     * The property name for the boolean value whether the URL should be
     * advertised or not
     */
    public final static String P_NAME_ADVERTISE_URL
        =  "org.jitsi.jigasi.transcription.ADVERTISE_URL";

    /**
     * The property name for the boolean value whether the audio of a conference
     * should be recorded alongside a transcription
     */
    public final static String P_NAME_RECORD_AUDIO
        = "org.jitsi.jigasi.transcription.RECORD_AUDIO";

    /**
     * The property name for the format which should be used to record the audio
     * in. Only wav is currently supported.
     */
    public final static String P_NAME_RECORD_AUDIO_FORMAT
        = "org.jitsi.jigasi.transcription.RECORD_AUDIO_FORMAT";

    /**
     * The property name for the boolean value whether scripts should be
     * executed when
     * {@link AbstractTranscriptPublisher.BasePromise#publish(Transcript)}
     * has been called
     */
    public final static String P_NAME_EXECUTE_SCRIPTS
        = "org.jitsi.jigasi.transcription.EXECUTE_SCRIPTS";

    /**
     * The property name for the string which acts a separator between the
     * paths given in the string of paths
     * {@link this#P_NAME_SCRIPTS_TO_EXECUTE_LIST}
     */
    public final static String P_NAME_SCRIPTS_TO_EXECUTE_LIST_SEPARATOR
        = "org.jitsi.jigasi.transcription.SCRIPTS_TO_EXECUTE_LIST_SEPARATOR";

    /**
     * The property name for the list to the paths of the scripts
     * which will need to be executed. The list is created by splitting the
     * string by the SEPARATOR string given by the property
     * {@link this#P_NAME_SCRIPTS_TO_EXECUTE_LIST_SEPARATOR}
     */
    public final static String P_NAME_SCRIPTS_TO_EXECUTE_LIST
        = "org.jitsi.jigasi.transcription.SCRIPTS_TO_EXECUTE_LIST";

    /**
     * The default for the url
     */
    public final static String TRANSCRIPT_BASE_URL_DEFAULT_VALUE
        = "http://localhost/";

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
     * By default do not record the audio
     */
    public final static boolean RECORD_AUDIO_DEFAULT_VALUE = false;

    /**
     * By default when recording audio the format to store it as is WAV
     */
    public final static String RECORD_AUDIO_FORMAT_DEFAULT_VALUE = "wav";

    /**
     * By default do not execute scripts
     */
    public final static boolean EXECUTE_SCRIPTS_DEFAULT_VALUE = false;

    /**
     * By default paths of scripts are separated by a ","
     */
    public final static String SCRIPTS_TO_EXECUTE_LIST_SEPARATOR_DEFAULT_VALUE
        = ",";

    /**
     * By default execute the example script
     */
    public final static String SCRIPTS_TO_EXECUTE_LIST_DEFAULT_VALUE
        = "script/example_handle_transcript_directory.sh";

    /**
     * The logger of this class
     */
    private static final Logger logger
        = Logger.getLogger(AbstractTranscriptPublisher.class);

    /**
     * Aspect for successful upload of transcript
     */
    private static final String DD_ASPECT_SUCCESS = "upload_success";

    /**
     * Aspect for failed upload of transcript
     */
    private static final String DD_ASPECT_FAIL = "upload_fail";

    /**
     * Get a string which contains a time stamp and a random UUID, with an
     * optional pre- and suffix attached.
     *
     * @return the generated string
     */
    protected static String generateHardToGuessTimeString(String prefix,
                                                          String suffix)
    {
        prefix = prefix == null || prefix.isEmpty() ?
            "":
            prefix + "_";

        suffix = suffix == null || suffix.isEmpty()?
            "":
            suffix;

        return String.format("%s%s_%s%s", prefix, Instant.now(),
            UUID.randomUUID(), suffix);
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
            logger.error("Cannot send message as chatRoom is null");
            return;
        }

        String messageString = message.toString();
        Message chatRoomMessage = chatRoom.createMessage(messageString);
        try
        {
            chatRoom.sendMessage(chatRoomMessage);
            if (logger.isTraceEnabled())
                logger.trace("Sending message: \"" + messageString + "\"");
        }
        catch (OperationFailedException e)
        {
            logger.warn("Failed to send message " + messageString, e);
        }
    }

    /**
     * Send a json-message to the muc room
     *
     * @param chatRoom the chatroom to send the message to
     * @param jsonMessage the json message to send
     */
    protected void sendJsonMessage(ChatRoom chatRoom, T jsonMessage)
    {
        if (chatRoom == null)
        {
            logger.error("Cannot send message as chatRoom is null");
            return;
        }

        if (!(chatRoom instanceof ChatRoomJabberImpl))
        {
            logger.error("Cannot send message as chatRoom is not an" +
                "instance of ChatRoomJabberImpl");
            return;
        }

        if (!chatRoom.isJoined())
        {
            if (logger.isDebugEnabled())
            {
                logger.debug("Skip sending message to room which we left!");
            }
            return;
        }

        String messageString = jsonMessage.toString();
        try
        {
            ((ChatRoomJabberImpl)chatRoom).sendJsonMessage(messageString);
            if (logger.isTraceEnabled())
                logger.trace("Sending json message: \"" + messageString + "\"");
        }
        catch (OperationFailedException e)
        {
            logger.warn("Failed to send json message " + messageString, e);
        }
    }
    /**
     * Save a transcript given as a String to subdirectory of getLogDirPath()
     * with the given directory name and the given file name
     *
     * @param directoryName the name of the subdirectory directory
     * @param fileName the name of the file
     * @param transcript the transcript to save
     */
    protected void saveTranscriptStringToFile(String directoryName,
                                        String fileName,
                                        String transcript)
    {
        Path rootDirPath = Paths.get(getLogDirPath());
        Path subDirectoryPath = Paths.get(rootDirPath.toString(),
            directoryName);

        // Try to make the root directory
        if (!createDirectoryIfNotExist(rootDirPath))
        {
            return;
        }

        // Now try to make the subdirectory directory
        if (!createDirectoryIfNotExist(subDirectoryPath))
        {
            return;
        }

        // and finally we can save the transcript
        File t = new File(subDirectoryPath.toString(), fileName);
        try(FileWriter writer = new FileWriter(t, StandardCharsets.UTF_8))
        {
            writer.write(transcript);
            logger.info("Wrote final transcript to " + t);
        }
        catch(IOException e)
        {
            logger.warn("Unable to write transcript to file " + t, e);
        }
    }

    /**
     * Create a directory at a specific path if it's not created
     *
     * @param path the path as a string of the directory which should be
     * created
     * @return True when the directory was created or already exists, false
     * otherwise
     */
    protected static boolean createDirectoryIfNotExist(Path path)
    {
        File dir = path.toFile();

        // Try to make the directory
        if (!dir.exists())
        {
            if (!dir.mkdirs())
            {
                logger.warn("Was unable to make a directory called " + dir);
                return false;
            }
        }

        // If there is a file with the directory name, we can't make the
        // directory and thus we cannot save the transcript
        if (dir.exists() && !dir.isDirectory())
        {
            logger.warn("Was unable to make a directory because" +
                " there is a file called " + dir);
            return false;
        }

        return true;
    }

    /**
     * Get the string representing the path of the directory wherein the
     * final transcripts should be stored
     *
     * @return the path as a String
     */
    public static String getLogDirPath()
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
     * Get whether an audio mix of each conference which is transcribed
     * should be recorded
     *
     * @return true when the mix should be recorded, false otherwise
     */
    protected boolean shouldRecordAudio()
    {
        return JigasiBundleActivator.getConfigurationService()
            .getBoolean(P_NAME_RECORD_AUDIO,
                RECORD_AUDIO_DEFAULT_VALUE);
    }

    /**
     * Get in which format the audio mix should be recorded
     *
     * @return the audio format
     */
    protected String getRecordingAudioFormat()
    {
        return JigasiBundleActivator.getConfigurationService()
            .getString(P_NAME_RECORD_AUDIO_FORMAT,
                RECORD_AUDIO_FORMAT_DEFAULT_VALUE);
    }

    /**
     * Get whether there any scripts need to be executed after a
     * {@link Transcript} is published by a call to
     * {@link BasePromise#publish(Transcript)}
     *
     * @return whether to execute one ore more scripts.
     */
    protected boolean shouldExecuteScripts()
    {
        return JigasiBundleActivator.getConfigurationService()
            .getBoolean(P_NAME_EXECUTE_SCRIPTS,
                EXECUTE_SCRIPTS_DEFAULT_VALUE);
    }

    /**
     * Get all the (relative) paths to the scripts to execute as a String.
     *
     * @return the list of all (relative) paths
     */
    protected List<String> getPathsToScriptsToExecute()
    {
        String paths = JigasiBundleActivator.getConfigurationService()
            .getString(P_NAME_SCRIPTS_TO_EXECUTE_LIST,
                SCRIPTS_TO_EXECUTE_LIST_DEFAULT_VALUE);

        return Arrays.asList(paths.split(
            getPathsToScriptsToExecuteSeparator()));
    }

    /**
     * Get which separator is used for a string of multiple paths
     *
     * @return the separator
     */
    protected String getPathsToScriptsToExecuteSeparator()
    {
        return JigasiBundleActivator.getConfigurationService()
            .getString(P_NAME_SCRIPTS_TO_EXECUTE_LIST_SEPARATOR,
                SCRIPTS_TO_EXECUTE_LIST_SEPARATOR_DEFAULT_VALUE);
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
         * A string of the room url
         */
        protected String roomUrl;

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
            if (event != null && event.getEvent().equals(
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
            if (roomName != null)
            {
                this.roomName = roomName;
            }
            return this;
        }

        /**
         * Format a transcript which includes a room url
         *
         * @param url the url of the room
         * @return this formatter
         */
        BaseFormatter tookPlaceAtUrl(String url)
        {
            if (url != null)
            {
                this.roomUrl = url;
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
            for (SpeechEvent e : events)
            {
                if (e.getEvent().equals(Transcript.TranscriptEventType.SPEECH))
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
            for (TranscriptEvent e : events)
            {
                if (e.getEvent().equals(Transcript.TranscriptEventType.JOIN))
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
            for (TranscriptEvent e : events)
            {
                if (e.getEvent().equals(Transcript.TranscriptEventType.LEAVE))
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
            for (TranscriptEvent e : events)
            {
                if (e.getEvent().equals(
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
            if (event != null && event.getEvent().equals(
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
            for (TranscriptEvent event : sortedKeys)
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
        /**
         * Whether {@link this#publish(Transcript)} has already been called once
         */
        private boolean published = false;

        /**
         * A unique directory name to store/publish the transcript into
         */
        private final String dirName = generateHardToGuessTimeString("", "");

        /**
         * The file name which will be used to record the audio file to.
         * Stays null when {@link this#shouldRecordAudio()} returns False.
         */
        private String audioFileName;

        /**
         * The recorder which will be used to record the audio, if required.
         * Stays null when {@link this#shouldRecordAudio()} returns False.
         */
        private Recorder recorder;

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean hasDescription()
        {
            return advertiseURL();
        }

        /**
         * Get the directory path wherein files can be stored, such as
         * a representation of the {@link Transcript} which will need to be
         * published
         *
         * @return the file name as a string
         */
        protected String getDirPath()
        {
            return dirName;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void maybeStartRecording(MediaDevice device)
        {
            if (shouldRecordAudio())
            {
                // we need to make sure the directory exists already
                // so the recorder can write to it
                createDirectoryIfNotExist(Paths.get(getLogDirPath(), dirName));

                String format = getRecordingAudioFormat();
                this.audioFileName =
                   generateHardToGuessTimeString("",
                       String.format(".%s", format));

                String audioFilePath = Paths.get(getLogDirPath(), dirName,
                    audioFileName).toString();

                this.recorder
                    = LibJitsi.getMediaService().createRecorder(device);
                try
                {
                    this.recorder.start(format, audioFilePath);
                }
                catch (MediaException | IOException e)
                {
                    logger.error("Could not start recording", e);
                }
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public final synchronized void publish(Transcript transcript)
        {
            if (published)
            {
                return;
            }
            else
            {
                published = true;
            }

            if (this.recorder != null)
            {
                this.recorder.stop();
            }

            doPublish(transcript);

            maybeExecuteBashScripts();
        }

        /**
         * Abstract method which needs to be implemented by children to publish
         * the transcript and/or recording
         *
         * @param transcript the Transcript file which needs to be published
         */
        protected abstract void doPublish(Transcript transcript);

        /**
         * Get the filename which was used to store the audio recording to
         *
         * @return the filename
         */
        public String getAudioRecordingFileName()
        {
            return audioFileName;
        }

        /**
         * Execute all given scripts by
         * {@link this#getPathsToScriptsToExecute()} ()} when
         * {@link this#shouldExecuteScripts()} ()} returns true
         */
        private void maybeExecuteBashScripts()
        {
            if (shouldExecuteScripts())
            {
                Path absDirPath =
                    Paths.get(getLogDirPath(), dirName).toAbsolutePath();

                for (String scriptPath : getPathsToScriptsToExecute())
                {
                    try
                    {
                        logger.info("executing " + scriptPath +
                        " with arguments '" + absDirPath + "'");

                        new ProcessBuilder(scriptPath.toString(), absDirPath.toString()).start();
                    }
                    catch (IOException e)
                    {
                        logger.error("Could not execute " + scriptPath, e);
                    }
                }
            }

        }
    }

}
