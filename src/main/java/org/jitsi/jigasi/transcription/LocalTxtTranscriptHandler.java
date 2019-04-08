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
import org.jitsi.utils.logging.*;

import java.time.*;
import java.time.format.*;
import java.util.*;

/**
 * Handler for formatting a transcript as a .txt file and storing it locally
 *
 * @author Nik Vaessen
 */
public class LocalTxtTranscriptHandler
    extends AbstractTranscriptPublisher<String>
{

    /**
     * The logger of this class
     */
    private static final Logger logger = Logger.getLogger(Transcript.class);

    /**
     * The maximum amount of characters on a single line in the
     * transcript
     */
    private static final int MAX_LINE_WIDTH = 80;

    /**
     * The delimiter character which is used to separate the header and footer
     * from the transcript
     */
    private static final char DELIMITER = '_';

    /**
     * The new line character
     */
    private static final String NEW_LINE = System.lineSeparator();

    /**
     * Get the delimiter of the header and footer with a width spanning maximum
     * length of a line
     *
     * @return the delimiter for the header and footer with the maximum line
     * length
     */
    private static String getDelimiter()
    {
        return String.join("", Collections.nCopies(MAX_LINE_WIDTH,
            Character.toString(DELIMITER))) + NEW_LINE;
    }

    /**
     * The format of the potential header added to the transcript
     *
     * The first string is the start date
     * The second string is the room name
     * The third string is the start time time
     * The fourth string is each participant name separated by a newline
     * The fifth string is the start time
     */
    private static final String UNFORMATTED_HEADER_ROOM_NAME_KNOWN
        = "Transcript of conference held at %s in room %s%n" +
        "Initial people present at %s:%n%s%n" +
        "Transcript, started at %s:%n";

    /**
     * The format of the potential header added to the transcript
     *
     * The first string is the start date
     * The second string is the start time time
     * The third string is each participant name separated by a newline
     * The fourth string is the start time
     */
    private static final String UNFORMATTED_HEADER
        = "Transcript of conference held at %s%n" +
        "Initial people present at %s:%n%n%s%n" +
        "Transcript, started at %s:%n";

    /**
     * The format of the potential footer added to the transcript
     *
     * The first string is a date and time
     */
    private static final String UNFORMATTED_FOOTER
        = "%nEnd of transcript at %s";

    /**
     * The base of a transcript event
     *
     * The first string is the time
     * The second string is the name
     */
    private static final String UNFORMATTED_EVENT_BASE = "<%s> %s";

    /**
     * The format of a participant saying something in the transcript
     *
     * The first string is the transcript
     */
    private static final String UNFORMATTED_SPEECH = ": %s";

    /**
     * The format of a participant joining in the transcript
     *
     * The first string is the time
     * The second string is the name
     */
    private static final String UNFORMATTED_JOIN
        = UNFORMATTED_EVENT_BASE + " joined the conference";

    /**
     * The format of a participant leaving in the transcript
     *
     * The first string is the time
     * The second string is the name
     */
    private static final String UNFORMATTED_LEAVE
        = UNFORMATTED_EVENT_BASE + " left the conference";

    /**
     * The format of a participant raising their hand in the transcript
     *
     * The first string is the time
     * The second string is the name
     */
    private static final String UNFORMATTED_RAISED_HAND
        = UNFORMATTED_EVENT_BASE + " raised their hand";

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
     * Publish final results to the chatroom in plain text. Interim results
     * are not handled as it would flood the chatroom with senseless results.
     *
     * {@inheritDoc}
     */
    @Override
    public void publish(ChatRoom chatRoom, TranscriptionResult result)
    {
        if(result.isInterim())
        {
            return;
        }

        String name = result.getName();
        String transcription = result.getAlternatives().iterator()
            .next().getTranscription();

        String toSend = name + ": " + transcription;
        super.sendMessage(chatRoom, toSend);
    }

    /**
     * Translated Speech-to-text results can be published to the Chatroom
     * as plain-text here.
     *
     * {@inheritDoc}
     */
    @Override
    public void publish(ChatRoom chatRoom, TranslationResult result)
    {
        /*
         * We do not send the translated speech-to-text results to the Chatroom
         * as it will flood the Chatroom with messages.
         */
    }

    @Override
    public Promise getPublishPromise()
    {
        return new TxtPublishPromise();
    }

    @Override
    public BaseFormatter getFormatter()
    {
        return new TxtFormatter();
    }

    @Override
    protected String formatSpeechEvent(SpeechEvent e)
    {
        String name = e.getName();
        String timeStamp = timeFormatter.format(e.getTimeStamp());
        String transcription = e.getResult().getAlternatives().iterator().next()
            .getTranscription();

        String base = String.format(UNFORMATTED_EVENT_BASE, timeStamp, name);
        String speech = String.format(UNFORMATTED_SPEECH, transcription);
        String formatted
            = base + String.format(UNFORMATTED_SPEECH, transcription);

        return formatToMaximumLineLength(formatted, MAX_LINE_WIDTH,
            base.length() + (speech.length() - transcription.length()))
            + NEW_LINE;
    }

    @Override
    protected String formatJoinEvent(TranscriptEvent e)
    {
        String name = e.getName();
        String timeStamp = timeFormatter.format(e.getTimeStamp());

        return String.format(UNFORMATTED_JOIN, timeStamp, name) + NEW_LINE;
    }

    @Override
    protected String formatLeaveEvent(TranscriptEvent e)
    {
        String name = e.getName();
        String timeStamp = timeFormatter.format(e.getTimeStamp());

        return String.format(UNFORMATTED_LEAVE, timeStamp, name) + NEW_LINE;
    }

    @Override
    protected String formatRaisedHandEvent(TranscriptEvent e)
    {
        String name = e.getName();
        String timeStamp = timeFormatter.format(e.getTimeStamp());

        return String.format(UNFORMATTED_RAISED_HAND, timeStamp, name)
            + NEW_LINE;
    }

    /**
     * Create a header for the transcript, which will contain the date, name
     * of the conference room, and the initial people present.
     *
     * @param startInstant the instant the conference started
     * @param roomName the room name to put in the header
     * @param initialMembers the list of people present to put in the header
     */
    private String createHeader(Instant startInstant,
                                String roomName,
                                List<Participant> initialMembers)
    {
        String initialMembersString;
        if(initialMembers == null || initialMembers.isEmpty())
        {
            initialMembersString = "";
        }
        else
        {
            List<String> memberNames = new ArrayList<>();
            initialMembers.forEach((p) -> memberNames.add(p.getName()));

            initialMembersString = "\t" + String.join("\n\t", memberNames)
                + NEW_LINE;
        }

        String header;
        String dateString = dateFormatter.format(startInstant);
        String timeString = timeFormatter.format(startInstant);
        if(roomName == null)
        {
            header = String.format(UNFORMATTED_HEADER,
                dateString, timeString, initialMembersString,
                timeString);
        }
        else
        {
            header = String.format(UNFORMATTED_HEADER_ROOM_NAME_KNOWN,
                dateString, roomName, timeString, initialMembersString,
                timeString);
        }

        return header + getDelimiter();
    }

    /**
     * Create a footer for the transcript, which will contain the time the
     * transcript ended
     *
     * the time added to the footer is the moment this method was called
     *
     * The footer of the transcript can only be created once. Only the first
     * call will result in the creation of the footer, following calls will be
     * ignored
     *
     * @param endInstant the instant when the conference ended
     */
    private String createFooter(Instant endInstant)
    {
        String dateTimeString = dateTimeFormatter.format(endInstant);

        return getDelimiter() + NEW_LINE
            + String.format(UNFORMATTED_FOOTER, dateTimeString);
    }

    /**
     * Format a String such that it will not have a line longer than the
     * given maximum length
     *
     * This method assumes that a word will never be longer than the
     * (expected maximum length - the amount of spaces after enter).
     * If this is the case, it will exceed the limit
     *
     * @param toFormat the String to format
     * @param maximumLength the maximum length the string is allowed to have
     * @param spacesAfterEnter the amount of spaces to have after a new line
     * @return the given string formatted such that the given parameters hold
     */
    private static String formatToMaximumLineLength(String toFormat,
                                                    int maximumLength,
                                                    int spacesAfterEnter)
    {
        boolean endWithSeparator = toFormat.endsWith(NEW_LINE);

        //split to get each token separated by one or more spaces
        String[] tokens = toFormat.split(" +");

        if(tokens.length == 0)
        {
            return "";
        }
        else if(tokens.length == 1)
        {
            return tokens[0];
        }

        StringBuilder formattedBuilder = new StringBuilder();
        int currentLineLength = 0;
        for(String currentToken: tokens)
        {
            // first we check if adding a new token will exceed the limit or
            // if the current token is a newline character
            // when this is the case, we need to append a newline in the
            // formatted string to not exceed the limit
            if(currentLineLength + currentToken.length() > maximumLength ||
                NEW_LINE.equals(currentToken))
            {
                formattedBuilder.append(NEW_LINE);
                if(spacesAfterEnter > 0)
                {
                    formattedBuilder.append(
                        String.join("",
                            Collections.nCopies(spacesAfterEnter, " ")));
                }
                currentLineLength = spacesAfterEnter;
            }

            // otherwise we can safely add the token to the formatted string,
            // unless even after placing an enter the word is to long to fit
            // but we assume this can never happen
            formattedBuilder.append(currentToken);
            currentLineLength += currentToken.length();

            // if we aren't at the end of a line, we have to put a space between
            // each token
            if(currentLineLength < maximumLength)
            {
                formattedBuilder.append(" ");
                currentLineLength += 1;
            }
        }

        // if the given string ended with a newline character, so does the
        // formatted string
        if(endWithSeparator)
        {
            formattedBuilder.append(NEW_LINE);
        }

        return formattedBuilder.toString();
    }

    /**
     * Builds a string which can be written into a .txt file to store a
     * final transcript
     */
    private class TxtFormatter
        extends BaseFormatter
    {
        @Override
        public String finish()
        {
            String header = createHeader(super.startInstant, super.roomName,
                super.initialMembers);
            String footer = createFooter(super.endInstant);

            final StringBuilder builder = new StringBuilder();
            builder.append(header);

            List<TranscriptEvent> sortedKeys =
                new ArrayList<>(super.formattedEvents.keySet());
            Collections.sort(sortedKeys);

            for(String event : super.getSortedEvents())
            {
                builder.append(event);
            }

            builder.append(footer);

            return builder.toString();
        }
    }

    private class TxtPublishPromise
        extends BasePromise
    {

        /**
         * Filename of the .txt file which will contain the transcript
         */
        private final String fileName
            = generateHardToGuessTimeString("transcript", ".txt");

        /**
         * {@inheritDoc}
         */
        @Override
        protected void doPublish(Transcript transcript)
        {
            String t =
                transcript.getTranscript(LocalTxtTranscriptHandler.this);

            saveTranscriptStringToFile(getDirPath(), fileName, t);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getDescription()
        {
            return String.format("Transcript will be saved in %s/%s/%s%n",
                getBaseURL(), getDirPath(), fileName);
        }
    }
}
