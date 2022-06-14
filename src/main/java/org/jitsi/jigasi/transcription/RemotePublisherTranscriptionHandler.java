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

import org.jitsi.jigasi.*;
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
     * Property name to determine whether to send the interim results
     */
    private final static String P_NAME_ENABLE_INTERIM_RESULTS
        = "org.jitsi.jigasi.transcription.ENABLE_INTERIM_RESULTS";

    /**
     * The default value for the property ENABLE_INTERIM_RESULTS
     */
    private final static boolean DEFAULT_VALUE_ENABLE_INTERIM_RESULTS = false;

    /**
     * List of remote services to notify for transcriptions.
     */
    private List<String> urls = new ArrayList<>();

    /**
     * Whether to send interim non-final results
     */
    private boolean enableInterimResults;

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

        enableInterimResults = JigasiBundleActivator.getConfigurationService()
            .getBoolean(P_NAME_ENABLE_INTERIM_RESULTS, DEFAULT_VALUE_ENABLE_INTERIM_RESULTS);
    }

    @Override
    public void publish(ChatRoom room, TranscriptionResult result)
    {
        if (!enableInterimResults && result.isInterim())
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
                event.getTimeStamp().toString());
        }

        for (String url : urls)
        {
            Util.postJSON(url, object);
        }
    }
}
