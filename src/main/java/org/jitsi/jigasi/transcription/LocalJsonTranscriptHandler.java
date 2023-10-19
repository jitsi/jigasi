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
import org.json.*;

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
 * When sending a single {@link TranscriptionResult} to the {@link ChatRoom},
 * a special JSON object is required. It needs 2 fields:
 *
 * 1. jitsi-meet-muc-msg-topic: which in our case will be a string
 *    "transcription-result"
 * 2. payload: which will be the "event" object described in point 2 above
 *
 * @author Nik Vaessen
 * @author Damian Minkov
 */
public class LocalJsonTranscriptHandler
    extends AbstractTranscriptPublisher<JSONObject>
{

    // "final transcript" JSON object fields

    /**
     * This fields stores the room name of the conference as a string
     */
    public final static String JSON_KEY_FINAL_TRANSCRIPT_ROOM_NAME
        = "room_name";

    /**
     * This fields stores the room url of the conference as a string
     */
    public final static String JSON_KEY_FINAL_TRANSCRIPT_ROOM_URL
        = "room_url";

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
     * This fields stores the type of event. Can be
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
     * This field stores the participant who caused the event as
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

    /**
     * This feild stores the stability value (between 0 and 1) of an interim
     * result, which indicates the likelihood that the result will change.
     */
    public final static String JSON_KEY_EVENT_STABILITY = "stability";

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

    /**
     * This fields stores the email of a participant as a string
     */
    public final static String JSON_KEY_PARTICIPANT_EMAIL = "email";

    /**
     * This fields stores the URL of the avatar of a participant as a string
     */
    public final static String JSON_KEY_PARTICIPANT_AVATAR_URL = "avatar_url";

    /**
     * This fields stores the identity username of a participant as a string
     */
    public final static String JSON_KEY_PARTICIPANT_IDENTITY_USERNAME
        = "identity_name";

    /**
     * This fields stores the identity user id of a participant as a string
     */
    public final static String JSON_KEY_PARTICIPANT_IDENTITY_USERID
        = "identity_id";

    /**
     * This fields stores the identity group id of a participant as a string
     */
    public final static String JSON_KEY_PARTICIPANT_IDENTITY_GROUPID
        = "identity_group_id";

    // JSON object to send to MUC

    /**
     * This fields stores the type of the muc message as a string
     */
    public final static String JSON_KEY_TYPE = "type";

    /**
     * This field stores the value of the type of the muc message  for
     * a transcription result to be sent.
     */
    public final static String JSON_VALUE_TYPE_TRANSCRIPTION_RESULT
        = "transcription-result";

    /**
     * This field stores the value of the type of muc message for
     * a translation result to be sent.
     */
    public final static String JSON_VALUE_TYPE_TRANSLATION_RESULT
        = "translation-result";

    @Override
    public JSONFormatter getFormatter()
    {
        return new JSONFormatter();
    }

    @Override
    public void publish(ChatRoom room, TranscriptionResult result)
    {
        JSONObject eventObject = createTranscriptionJSONObject(result);

        super.sendJsonMessage(room, eventObject);
    }

    @Override
    public void publish(ChatRoom room, TranslationResult result)
    {
        JSONObject eventObject = createTranslationJSONObject(result);

        super.sendJsonMessage(room, eventObject);
    }

    /**
     * Creates a json object representing the <tt>TranscriptionResult</>.
     * @param result the object to use to produce json.
     * @return json object representing the <tt>TranscriptionResult</>.
     */
    @SuppressWarnings("unchecked")
    public static JSONObject createTranscriptionJSONObject(
        TranscriptionResult result)
    {
        JSONObject eventObject = new JSONObject();
        SpeechEvent event = new SpeechEvent(result);

        addEventDescriptions(eventObject, event);
        addAlternatives(eventObject, event);

        eventObject.put(JSON_KEY_TYPE, JSON_VALUE_TYPE_TRANSCRIPTION_RESULT);

        return eventObject;
    }

    /**
     *Creates a json object representing the <tt>TranslationResult</tt>.
     *
     * @param result the object to be used to produce json.
     * @return json object representing the <tt>TranslationResult</tt>.
     */
    @SuppressWarnings("unchecked")
    private static JSONObject createTranslationJSONObject(
        TranslationResult result)
    {
        JSONObject eventObject = new JSONObject();
        SpeechEvent event = new SpeechEvent(result.getTranscriptionResult());

        addEventDescriptions(eventObject, event);

        eventObject.put(JSON_KEY_TYPE, JSON_VALUE_TYPE_TRANSLATION_RESULT);
        eventObject.put(JSON_KEY_EVENT_LANGUAGE, result.getLanguage());
        eventObject.put(JSON_KEY_ALTERNATIVE_TEXT, result.getTranslatedText());
        eventObject.put(JSON_KEY_EVENT_MESSAGE_ID,
                result.getTranscriptionResult().getMessageID().toString());

        return eventObject;
    }
    @Override
    public Promise getPublishPromise()
    {
        return new JSONPublishPromise();
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
    public static void addEventDescriptions(
        JSONObject jsonObject, TranscriptEvent e)
    {
        jsonObject.put(JSON_KEY_EVENT_EVENT_TYPE, e.getEvent().toString());
        jsonObject.put(JSON_KEY_EVENT_TIMESTAMP, e.getTimeStamp().toEpochMilli());

        JSONObject participantJson = new JSONObject();

        addParticipantDescription(participantJson, e.getParticipant());

        jsonObject.put(JSON_KEY_EVENT_PARTICIPANT, participantJson);
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
    private static void addAlternatives(JSONObject jsonObject, SpeechEvent e)
    {
        TranscriptionResult result = e.getResult();
        JSONArray alternativeJSONArray = new JSONArray();

        for (TranscriptionAlternative alternative : result.getAlternatives())
        {
            JSONObject alternativeJSON = new JSONObject();

            alternativeJSON.put(JSON_KEY_ALTERNATIVE_TEXT,
                alternative.getTranscription());
            alternativeJSON.put(JSON_KEY_ALTERNATIVE_CONFIDENCE,
                alternative.getConfidence());

            alternativeJSONArray.put(alternativeJSON);
        }

        jsonObject.put(JSON_KEY_EVENT_TRANSCRIPT, alternativeJSONArray);
        jsonObject.put(JSON_KEY_EVENT_LANGUAGE, result.getLanguage());
        jsonObject.put(JSON_KEY_EVENT_IS_INTERIM, result.isInterim());
        jsonObject.put(JSON_KEY_EVENT_MESSAGE_ID,
            result.getMessageID().toString());
        jsonObject.put(JSON_KEY_EVENT_STABILITY, result.getStability());
    }


    /**
     * Make a given JSON object the "participant" JSON object
     *
     * @param pJSON the given JSON object to fill with the participant info
     * @param participant the participant whose information to use
     */
    @SuppressWarnings("unchecked")
    private static void addParticipantDescription(JSONObject pJSON,
                                                  Participant participant)
    {
        pJSON.put(JSON_KEY_PARTICIPANT_NAME, participant.getName());
        pJSON.put(JSON_KEY_PARTICIPANT_ID, participant.getId());

        // adds email if it exists
        String email = participant.getEmail();
        if (email != null)
        {
            pJSON.put(JSON_KEY_PARTICIPANT_EMAIL, email);
        }

        // adds avatar-url if it exists
        String avatarUrl = participant.getAvatarUrl();
        if (avatarUrl != null)
        {
            pJSON.put(JSON_KEY_PARTICIPANT_AVATAR_URL, avatarUrl);
        }

        // add identity information if it exists
        String identityUsername = participant.getIdentityUserName();
        if (identityUsername != null)
        {
            pJSON.put(JSON_KEY_PARTICIPANT_IDENTITY_USERNAME, identityUsername);
        }

        String identityUserId = participant.getIdentityUserId();
        if (identityUserId != null)
        {
            pJSON.put(JSON_KEY_PARTICIPANT_IDENTITY_USERID, identityUserId);
        }

        String identityGroupId = participant.getIdentityGroupId();
        if (identityGroupId != null)
        {
            pJSON.put(JSON_KEY_PARTICIPANT_IDENTITY_GROUPID, identityGroupId);
        }
    }

    /**
     * Make a given object the "final_transcript" JSON object by adding the
     * fields roomName, startTime, endTime, initialParticipants and events to
     * the given object.
     *
     * @param jsonObject the object to add the fields to
     * @param roomName the room name
     * @param participants the initial participants
     * @param start the start time
     * @param end the end time
     * @param events a collection of "event" json objects
     */
    @SuppressWarnings("unchecked")
    private void addTranscriptDescription(JSONObject jsonObject,
                                          String roomName,
                                          String roomUrl,
                                          Collection<Participant> participants,
                                          Instant start,
                                          Instant end,
                                          Collection<JSONObject> events)
    {
        if (roomName != null && !roomName.isEmpty())
        {
            jsonObject.put(JSON_KEY_FINAL_TRANSCRIPT_ROOM_NAME, roomName);
        }
        if (roomUrl != null && !roomUrl.isEmpty())
        {
            jsonObject.put(JSON_KEY_FINAL_TRANSCRIPT_ROOM_URL, roomUrl);
        }
        if (start != null)
        {
            jsonObject.put(JSON_KEY_FINAL_TRANSCRIPT_START_TIME,
                start.toString());
        }
        if (end != null)
        {
            jsonObject.put(JSON_KEY_FINAL_TRANSCRIPT_END_TIME, end.toString());
        }
        if (participants != null && !participants.isEmpty())
        {
            JSONArray participantArray = new JSONArray();

            for (Participant participant : participants)
            {
                JSONObject pJSON = new JSONObject();

                addParticipantDescription(pJSON, participant);

                participantArray.put(pJSON);
            }

            jsonObject.put(JSON_KEY_FINAL_TRANSCRIPT_INITIAL_PARTICIPANTS,
                participantArray);
        }
        if (events != null && !events.isEmpty())
        {
            JSONArray eventArray = new JSONArray();

            events.forEach(eventArray::put);

            jsonObject.put(JSON_KEY_FINAL_TRANSCRIPT_EVENTS, eventArray);
        }
    }

    /**
     * Formats a transcript into the "final_transcript" object
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
                super.roomUrl,
                super.initialMembers,
                super.startInstant,
                super.endInstant,
                super.getSortedEvents());

            return transcript;
        }
    }

    private class JSONPublishPromise
        extends BasePromise
    {

        /**
         * Filename of the .json file which will contain the transcript
         */
        private final String fileName
            = generateHardToGuessTimeString("transcript", ".json");

        /**
         * {@inheritDoc}
         */
        @Override
        protected void doPublish(Transcript transcript)
        {
            JSONObject t
                = transcript.getTranscript(LocalJsonTranscriptHandler.this);

            saveTranscriptStringToFile(getDirPath(), fileName,
                t.toString());
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
