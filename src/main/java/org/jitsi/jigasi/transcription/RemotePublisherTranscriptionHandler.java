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

import java.util.*;

/**
 * Pushes transcriptions to remote services.
 *
 * @author Damian Minkov
 */
public class RemotePublisherTranscriptionHandler
    extends LocalJsonTranscriptHandler
    implements TranscriptionEventListener
{
    /**
     * List of remote services to notify for transcriptions.
     */
    private List<String> urls = new ArrayList<>();

    /**
     * Constructs RemotePublisherTranscriptionHandler, initializing its config.
     *
     * @param urlsStr String containing urls of remote services, separated
     * by ','.
     */
    public RemotePublisherTranscriptionHandler(String urlsStr)
    {
        super();

        // initialize tokens
        StringTokenizer tokens = new StringTokenizer(urlsStr, ",");
        while (tokens.hasMoreTokens())
        {
            urls.add(tokens.nextToken().trim());
        }
    }

    @Override
    public void publish(ChatRoom room, TranscriptionResult result)
    {
        if (result.isInterim())
            return;

        JSONObject eventObject = createTranscriptionJSONObject(result);

        eventObject.put(
            LocalJsonTranscriptHandler
                .JSON_KEY_FINAL_TRANSCRIPT_ROOM_NAME,
            result.getParticipant().getTranscriber().getRoomName());

        // adds event type to the encapsulating object to be consistent
        // with the events we push to the remote service
        eventObject.put(
            LocalJsonTranscriptHandler
                .JSON_KEY_EVENT_EVENT_TYPE,
            Transcript.TranscriptEventType.SPEECH.toString());

        for (String url : urls)
        {
            Util.postJSON(url, eventObject);
        }
    }

    @Override
    public void notify(Transcriber transcriber, TranscriptEvent event)
    {
        JSONObject object = new JSONObject();
        object.put(
            JSON_KEY_FINAL_TRANSCRIPT_ROOM_NAME,
            transcriber.getRoomName());

        if (event.getEvent() == Transcript.TranscriptEventType.JOIN
            || event.getEvent() == Transcript.TranscriptEventType.LEAVE)
        {
            addEventDescriptions(object, event);
        }
        else if (event.getEvent() == Transcript.TranscriptEventType.START
            || event.getEvent() == Transcript.TranscriptEventType.END)
        {
            object.put(JSON_KEY_EVENT_EVENT_TYPE,
                event.getEvent().toString());
            object.put(JSON_KEY_EVENT_TIMESTAMP,
                event.getTimeStamp().toEpochMilli());
        }

        for (String url : urls)
        {
            Util.postJSON(url, object);
        }
    }
}
