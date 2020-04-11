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
 * complete transcript once a conference is over in original and language
 * for which translations are requested.
 *
 * @author Nik Vaessen
 */
public class Transcript
    implements TranscriptionListener,
               TranslationResultListener
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
        WILL_END,
        END
    }

    /**
     * The list of all received speechEvents
     */
    private final List<SpeechEvent> speechEvents = new LinkedList<>();

    /**
     * The map of language tag to the list of speechEvents in that language.
     */
    private final Map<String, LinkedList<TranslatedSpeechEvent>> translatedSpeechEvents
        = new HashMap<>();

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
     * The url of the conference this transcript belongs to
     */
    private String roomUrl;

    /**
     * The translation manager to be used for translating the transcriptions
     */
    private TranslationManager translationManager;

    /**
     * Create a Transcript object which can store events related to a conference
     * which can then be formatted into an actual transcript by a
     * {@link TranscriptHandler}
     *
     * Speech can be stored using the TranscriptionListener
     * Other events, such as people joining and leaving, can be stored using
     * their instance methods
     *
     * @param roomName the name of the room of the conference
     * @param roomUrl the url of the room of the conference
     */
    Transcript(String roomName, String roomUrl)
    {
        this.roomName = roomName;
        this.roomUrl = roomUrl;
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
        this.roomUrl = "";
    }

    /**
     * The transcript gets notified when a new result comes in. Any final
     * {@link TranscriptionResult} should be stored in the final transcript.
     *
     * @param result the result which has come in
     */
    @Override
    public void notify(TranscriptionResult result)
    {
        if(started != null && !result.isInterim())
        {
            SpeechEvent speechEvent = new SpeechEvent(Instant.now(), result);
            speechEvents.add(speechEvent);
        }
    }

    /**
     * Sets the translation manager to be used by this {@link Transcript}
     * for translations
     *
     * @param translationManager to be used
     */
    public void setTranslationManager(TranslationManager translationManager)
    {
        this.translationManager = translationManager;
    }

    /**
     * Initialises an empty list to hold the Translated results in the given
     * language as a key in the map
     *
     * @param language the target language for creating the final
     * translated transcript
     */
    public void addTranslationLanguage(String language)
    {
        if(language == null || language.isEmpty())
            return;

        translatedSpeechEvents.putIfAbsent(language, new LinkedList<>());
    }

    /**
     * Returns the list of languages to prepare the final transcript in.
     *
     * @return set of languages for translations
     */
    public Set<String> getTranslationLanguages()
    {
        return translatedSpeechEvents.keySet();
    }

    @Override
    public void notify(TranslationResult result)
    {
        if(started != null)
        {
            TranslatedSpeechEvent translatedSpeechEvent
                = new TranslatedSpeechEvent(Instant.now(), result);
            translatedSpeechEvents.get(result.getLanguage())
                .add(translatedSpeechEvent);
        }
    }


    @Override
    public void completed()
    {
        // nothing do to
    }

    @Override
    public void failed(FailureReason reason)
    {
        // whatever
    }

    /**
     * Notify the transcript that the conference started at this exact moment
     * Will be ignored if already told the conference started
     *
     * @param initialParticipants the names of the participants currently in
     *                            the room
     * @return the newly created <tt>TranscriptEvent</tt> or null.
     */
    public TranscriptEvent started(List<Participant> initialParticipants)
    {
        if(started == null)
        {
            this.started
                = new TranscriptEvent(Instant.now(), TranscriptEventType.START);
            this.initialParticipantNames.addAll(initialParticipants);

            return this.started;
        }

        return null;
    }


    /**
     * Notify the transcript that the conference started at this exact moment
     * Will be ignored if already told the conference started
     *
     * @param roomName the roomName of the conference
     * @param roomUrl the url of the conference
     * @param initialParticipants the  participants currently in the room
     * @return the newly created <tt>TranscriptEvent</tt> or null.
     */
    protected TranscriptEvent started(String roomName,
                                      String roomUrl,
                                      List<Participant> initialParticipants)
    {
        if(started == null)
        {
            this.roomName = roomName;
            this.roomUrl = roomUrl;
            this.started
                = new TranscriptEvent(Instant.now(), TranscriptEventType.START);
            this.initialParticipantNames.addAll(initialParticipants);

            return this.started;
        }

        return null;
    }

    /**
     * Notify the transcript that the conference ended at this exact moment
     * Will be ignored if this transcript has not been told the conference
     * started or if it already ended.
     * @return the newly created <tt>TranscriptEvent</tt> or null.
     */
    protected TranscriptEvent ended()
    {
        if(started != null && ended == null)
        {
            this.ended
                = new TranscriptEvent(Instant.now(), TranscriptEventType.END);
        }

        return this.ended;
    }

    /**
     * Notify the transcript that the conference will end.
     * Will be ignored if this transcript has not been told the conference
     * started or if it already ended.
     * @return the newly created <tt>TranscriptEvent</tt> or null.
     */
    public TranscriptEvent willEnd()
    {
        if(started != null && ended == null)
        {
            return new TranscriptEvent(
                Instant.now(), TranscriptEventType.WILL_END);
        }

        return null;
    }

    /**
     * Notify the transcript that someone joined at this exact moment.
     * Will be ignored if this transcript was told the conference ended
     *
     * @param participant the participant who joined.
     * @return the newly created <tt>TranscriptEvent</tt> or null.
     */
    public synchronized TranscriptEvent notifyJoined(Participant participant)
    {
        if(started != null && ended == null)
        {
            // do not duplicate join events, can happen on conference start
            // because of WaitForConferenceMemberThread in transcript gw session
            for (TranscriptEvent ev : joinedEvents)
            {
                if (ev.getParticipant().equals(participant))
                    return null;
            }

            TranscriptEvent event = new TranscriptEvent(
                Instant.now(), participant, TranscriptEventType.JOIN);
            joinedEvents.add(event);

            return event;
        }

        return null;
    }

    /**
     * Notify the transcript that someone left at this exact moment.
     * Will be ignored if this transcript was told the conference ended
     *
     * @param participant the participant who left.
     * @return the newly created <tt>TranscriptEvent</tt> or null.
     */
    public TranscriptEvent notifyLeft(Participant participant)
    {
        if(started != null && ended == null)
        {
            TranscriptEvent event = new TranscriptEvent(
                Instant.now(), participant, TranscriptEventType.LEAVE);
            leftEvents.add(event);

            return event;
        }

        return null;
    }

    /**
     * Notify the transcript that someone raised their hand at this exact moment
     * Will be ignored if this transcript was told the conference ended
     *
     * @param participant the participant who raised their hand.
     * @return the newly created <tt>TranscriptEvent</tt> or null.
     * */
    public TranscriptEvent notifyRaisedHand(Participant participant)
    {
        if(started != null && ended == null)
        {
            TranscriptEvent event = new TranscriptEvent(
                Instant.now(), participant, TranscriptEventType.RAISE_HAND);
            raisedHandEvents.add(event);

            return event;
        }

        return null;
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
        return getRoomEvents(publisher)
               .speechEvents(speechEvents)
               .finish();
    }

    /**
     * Get a formatted transcript of events stored by this object in the target
     * language.
     *
     * @param publisher a publisher which has a formatter to create a transcript
     *                  in the desired type.
     * @param language the target language of the transcript.
     * @param <T> the type in which the transcript will be stored.
     * @return a formatted transcript with type T in the target language.
     */
    public <T> T getTranslatedTranscript(
        AbstractTranscriptPublisher<T> publisher, String language)
    {
        List<TranslatedSpeechEvent> translatedSpeechEventsList;

        if(speechEvents.size() != translatedSpeechEvents.get(language).size())
        {
            Iterator<TranslatedSpeechEvent> translatedSpeechEventIterator
                = translatedSpeechEvents.get(language).iterator();
            System.out.println(translatedSpeechEvents.get(language));
            translatedSpeechEventsList = new LinkedList<>();

            TranslatedSpeechEvent currentMessage
                = translatedSpeechEventIterator.next();

            for (SpeechEvent speechEvent : speechEvents)
            {
                if(currentMessage == null ||
                   currentMessage.getResult().getTranscriptionResult().getMessageID()
                           != speechEvent.getResult().getMessageID())
                {
                    // Speech event is not present in the target language
                    translatedSpeechEventsList.add(new TranslatedSpeechEvent(
                        speechEvent.getTimeStamp(),
                        translationManager.getSingleTranslation(
                            speechEvent.getResult(), language)));
                }
                else
                {
                    // Speech event is already present in the target language
                    translatedSpeechEventsList.add(currentMessage);
                    currentMessage = translatedSpeechEventIterator.hasNext() ?
                        translatedSpeechEventIterator.next() : null;
                }
            }

        }
        else
        {
            translatedSpeechEventsList = translatedSpeechEvents.get(language);
        }

        return getRoomEvents(publisher)
            .transcriptLanguage(language)
            .translatedSpeechEvents(translatedSpeechEventsList)
            .finish();
    }

    /**
     * Gets a formatter of type T with details of the room events.
     *
     * @param publisher a publisher which has a formatter to create a transcript
     *                  in the desired type.
     * @param <T> the type in which the room details are to be formatted.
     * @return a formatter for the transcript with type T.
     */
    public <T> AbstractTranscriptPublisher<T>.BaseFormatter
               getRoomEvents(AbstractTranscriptPublisher<T> publisher)
    {
        return publisher.getFormatter()
            .startedOn(started)
            .initialParticipants(initialParticipantNames)
            .tookPlaceInRoom(roomName)
            .tookPlaceAtUrl(roomUrl)
            .raiseHandEvents(raisedHandEvents)
            .joinEvents(joinedEvents)
            .leaveEvents(leftEvents)
            .endedOn(ended);
    }
}
