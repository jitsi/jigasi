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

import org.json.simple.*;

import java.time.*;
import java.util.*;

/**
 * This TranscriptHandler uses JSON as the underlying data structure of the
 * transcript. There are 4 kinds of JSON objects:
 *
 * 1. "final_transcript" object: contains all data regarding a conference which
 * was transcribed: start time, end time, room name, initial members, all events
 *
 * 2. "event" object: this object is used to store information regarding a
 * single transcript event. This includes speech, join/leave and raise hand
 * If it is a speech event, it includes the speech-to-text result
 * which is stored as an json-array of alternatives
 *
 * 3. "alternatives" object: This object stores one possible speech-to-text
 * result. It only has 2 fields: the text and the confidence
 *
 * 4. "Participant" object: This object stores the information of a participant:
 * the name and the (j)id
 *
 */
public class LocalJsonTranscriptHandler
    extends AbstractTranscriptHandler<JSONObject>
{

    // "final transcript" JSON object fields

    /**
     * This fields stores the room name of the conference as a string
     */
    public final static String JSON_KEY_FINAL_TRANSCRIPT_ROOM_NAME
        = "room_name";

    /**
     * This field stores all the events as an JSON array
     */
    public final static String JSON_KEY_FINAL_TRANSCRIPT_EVENTS = "events";

    /**
     * This field stores "Participant" objects of the initial members as an
     * JSON array
     */
    public final static String JSON_KEY_FINAL_TRANSCRIPT_INITIAL_PARTICIPANTS
        = "initial_participants";

    /**
     * This field stores the start time of the transcript as a string
     */
    public final static String JSON_KEY_FINAL_TRANSCRIPT_START_TIME =
        "start_time";

    /**
     * This field stores the end time of the transcript as a string
     */
    public final static String JSON_KEY_FINAL_TRANSCRIPT_END_TIME =
        "end_time";


    // "event" JSON object fields

    /**
     * This fields stores the the type of event. Can be
     * {@link Transcript.TranscriptEventType#JOIN},
     * {@link Transcript.TranscriptEventType#LEAVE},
     * {@link Transcript.TranscriptEventType#RAISE_HAND} or
     * {@link Transcript.TranscriptEventType#SPEECH}
     */
    public final static String JSON_KEY_EVENT_EVENT_TYPE = "event";

    /**
     * This field stores the time the event took place as a string
     */
    public final static String JSON_KEY_EVENT_TIMESTAMP = "timestamp";

    /**
     * This field stores the  the participant who caused the event as
     * a Participant object
     */
    public final static String JSON_KEY_EVENT_PARTICIPANT = "participant";

    /**
     * This field stores the alternative JSON objects as a JSON array
     */
    public final static String JSON_KEY_EVENT_TRANSCRIPT = "transcript";

    /**
     * This field stores the language of the transcript as a string
     */
    public final static String JSON_KEY_EVENT_LANGUAGE = "language";

    /**
     * This field stores a unique id for every message as a string.
     * Can be used to update results if "is_interim" is or was true
     */
    public final static String JSON_KEY_EVENT_MESSAGE_ID = "message_id";

    /**
     * This field stores whether the speech-to-text result is an interim result,
     * which means it will be updated in the future, as either true or false
     */
    public final static String JSON_KEY_EVENT_IS_INTERIM = "is_interim";

    // "alternative" JSON object fields

    /**
     * This field stores the text of a speech-to-text result as a string
     */
    public final static String JSON_KEY_ALTERNATIVE_TEXT = "text";

    /**
     * This fields stores the confidence of the speech-to-text result as a
     * number between 0 and 1
     */
    public final static String JSON_KEY_ALTERNATIVE_CONFIDENCE = "confidence";

    // "participant" JSON object fields

    /**
     * This fields stores the name of a participant as a string
     */
    public final static String JSON_KEY_PARTICIPANT_NAME = "name";

    /**
     * This fields stores the (j)id of a participant as a string
     */
    public final static String JSON_KEY_PARTICIPANT_ID = "id";

    @Override
    public TranscriptHandler.Formatter<JSONObject> format()
    {
        return new JSONFormatter();
    }

    @Override
    public JSONObject formatTranscriptionResult(TranscriptionResult result)
    {
        JSONObject object = new JSONObject();

        SpeechEvent event = new SpeechEvent(Instant.now(), result);

        addEventDescriptions(object, event);
        addAlternatives(object, event);

        return object;
    }

    @Override
    public void publish(JSONObject transcript)
    {
        new LocalTxtTranscriptHandler().publish(transcript.toString());
    }

    @Override
    protected JSONObject formatSpeechEvent(SpeechEvent e)
    {
        JSONObject object = new JSONObject();
        addEventDescriptions(object, e);
        addAlternatives(object, e);
        return object;
    }

    @Override
    protected JSONObject formatJoinEvent(TranscriptEvent e)
    {
        JSONObject object = new JSONObject();
        addEventDescriptions(object, e);
        return object;
    }

    @Override
    protected JSONObject formatLeaveEvent(TranscriptEvent e)
    {
        JSONObject object = new JSONObject();
        addEventDescriptions(object, e);
        return object;
    }

    @Override
    protected JSONObject formatRaisedHandEvent(TranscriptEvent e)
    {
        JSONObject object = new JSONObject();
        addEventDescriptions(object, e);
        return object;
    }

    /**
     * Make a given JSON object the "event" json object by adding the fields
     * eventType, timestamp, participant name and participant ID to the give
     * object
     *
     * @param jsonObject the JSON object to add the fields to
     * @param e the event which holds the information to add to the JSON object
     */
    @SuppressWarnings("unchecked")
    private void addEventDescriptions(JSONObject jsonObject, TranscriptEvent e)
    {
        jsonObject.put(JSON_KEY_EVENT_EVENT_TYPE, e.getEvent().toString());
        jsonObject.put(JSON_KEY_EVENT_TIMESTAMP, e.getTimeStamp().toString());

        JSONObject participant = new JSONObject();
        participant.put(JSON_KEY_PARTICIPANT_NAME, e.getName());
        participant.put(JSON_KEY_PARTICIPANT_ID, e.getID());

        jsonObject.put(JSON_KEY_EVENT_PARTICIPANT, participant);
    }

    /**
     * Make a given JSON object the "event" json object by adding the fields
     * transcripts, is_interim, messageID and langiage to the given object.
     * Assumes that
     * {@link this#addEventDescriptions(JSONObject, TranscriptEvent)}
     * has been or will be called on the same given JSON object
     *
     * @param jsonObject the JSON object to add the fields to
     * @param e the event which holds the information to add to the JSON object
     */
    @SuppressWarnings("unchecked")
    private void addAlternatives(JSONObject jsonObject, SpeechEvent e)
    {
        TranscriptionResult result = e.getResult();
        JSONArray alternativeJSONArray = new JSONArray();

        for(TranscriptionAlternative alternative : result.getAlternatives())
        {
            JSONObject alternativeJSON = new JSONObject();

            alternativeJSON.put(JSON_KEY_ALTERNATIVE_TEXT,
                alternative.getTranscription());
            alternativeJSON.put(JSON_KEY_ALTERNATIVE_CONFIDENCE,
                alternative.getConfidence());

            alternativeJSONArray.add(alternativeJSON);
        }

        jsonObject.put(JSON_KEY_EVENT_TRANSCRIPT, alternativeJSONArray);
        jsonObject.put(JSON_KEY_EVENT_LANGUAGE, e.getResult().getLanguage());
        jsonObject.put(JSON_KEY_EVENT_IS_INTERIM, e.getResult().isInterim());
        jsonObject.put(JSON_KEY_EVENT_MESSAGE_ID,
            e.getResult().getMessageID().toString());
    }

    /**
     * Make a given object the "final_transcript" JSON object by adding the
     * fields roomName, startTime, endTime, initialParticipants and events to
     * the given object.
     *
     * @param jsonObject the object to add the fields to
     * @param roomName the room name
     * @param names the names of the initial participants
     * @param start the start time
     * @param end the end time
     * @param events a collection of "event" json objects
     */
    @SuppressWarnings("unchecked")
    private void addTranscriptDescription(JSONObject jsonObject,
                                          String roomName,
                                          Collection<Participant> participants,
                                          Instant start,
                                          Instant end,
                                          Collection<JSONObject> events)
    {
        if(roomName != null && !roomName.isEmpty())
        {
            jsonObject.put(JSON_KEY_FINAL_TRANSCRIPT_ROOM_NAME, roomName);
        }
        if(start != null)
        {
            jsonObject.put(JSON_KEY_FINAL_TRANSCRIPT_START_TIME,
                start.toString());
        }
        if(end != null)
        {
            jsonObject.put(JSON_KEY_FINAL_TRANSCRIPT_END_TIME, end.toString());
        }
        if(participants != null && !participants.isEmpty())
        {
            JSONArray participantArray = new JSONArray();

            for(Participant participant : participants)
            {
                JSONObject pJSON = new JSONObject();

                pJSON.put(JSON_KEY_PARTICIPANT_NAME, participant.getName());
                pJSON.put(JSON_KEY_PARTICIPANT_ID, participant.getId());

                participantArray.add(pJSON);
            }

            jsonObject.put(JSON_KEY_FINAL_TRANSCRIPT_INITIAL_PARTICIPANTS,
                participantArray);
        }
        if(events != null && !events.isEmpty())
        {
            JSONArray eventArray = new JSONArray();
            eventArray.addAll(events);
            jsonObject.put(JSON_KEY_FINAL_TRANSCRIPT_EVENTS, eventArray);
        }
    }

    /**
     *
     */
    private class JSONFormatter
        extends BaseFormatter
    {
        @Override
        @SuppressWarnings("unchecked")
        public JSONObject finish()
        {
            JSONObject transcript = new JSONObject();

            addTranscriptDescription(
                transcript,
                super.roomName,
                super.initialMembers,
                super.startInstant,
                super.endInstant,
                super.getSortedEvents());

            return transcript;
        }
    }

}
