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
package org.jitsi.jigasi.transcription.action;

import net.java.sip.communicator.util.osgi.ServiceUtils;
import org.jitsi.jigasi.transcription.*;
import org.jitsi.service.configuration.*;
import org.jitsi.utils.logging.Logger;
import org.json.*;
import org.osgi.framework.*;

import java.util.*;
import java.util.regex.*;
import java.util.stream.*;

/**
 * Loads from the configuration all action handlers configured.
 * Action handler is represented by two properties:
 * org.jitsi.jigasi.transcription.action.action_name.PHRASE=Jitsi kick
 * org.jitsi.jigasi.transcription.action.action_name.URL=https://jitsi.net/kick
 * Where action_name is the name of the handler {@link ActionHandler},
 * PHRASE is the phrase that will be detected (case ignored) in
 * the transcription and that transcription (excluding everything up to the
 * end of the phrase) will be sent to a service specified in the URL property.
 *
 * Multiple actions can be defined.
 *
 * @author Damian Minkov
 */
public class ActionServicesHandler
{
    /**
     * The logger for this class
     */
    private final static Logger logger
        = Logger.getLogger(ActionServicesHandler.class);

    /**
     * The action handlers property prefix.
     */
    private static final String ACTION_PROPS_PREFIX
        = "org.jitsi.jigasi.transcription.action";

    /**
     * The property for the phrase of an action.
     */
    private static final String ACTION_PHRASE_PROP_NAME = "PHRASE";

    /**
     * The address of the service to handle detected phrase.
     */
    private static final String ACTION_URL_PROP_NAME = "URL";

    /**
     * The single instance of this services handler.
     */
    private static ActionServicesHandler serviceHandlerInstance = null;

    /**
     * List of configured actions.
     */
    private List<ActionHandler> actions = new ArrayList<>();

    /**
     * Map of patterns to detect and corresponding action.
     */
    private Map<Pattern, ActionHandler> patterns = new HashMap<>();

    /**
     * Set of all conferences we had detected an action and service was
     * notified for it.
     */
    private Map<String, List<ActionHandler>> actionSources = new HashMap<>();

    /**
     * Constructs this single instance of actions service handler and
     * initialize all configured actions.
     * @param ctx the bundle context
     */
    private ActionServicesHandler(BundleContext ctx)
    {
        ConfigurationService config =
            ServiceUtils.getService(ctx, ConfigurationService.class);

        List<String> actionProps =
            config.getPropertyNamesByPrefix(ACTION_PROPS_PREFIX, false);

        Set<String> actionNames = new HashSet<>();
        for (String prop : actionProps)
        {
            prop = prop.substring(ACTION_PROPS_PREFIX.length() + 1);
            prop = prop.substring(0, prop.indexOf('.'));
            actionNames.add(prop);
        }

        for (String actionName : actionNames)
        {
            String ph = config.getString(ACTION_PROPS_PREFIX
                + "." + actionName + "." + ACTION_PHRASE_PROP_NAME);
            String url = config.getString(ACTION_PROPS_PREFIX
                + "." + actionName + "." + ACTION_URL_PROP_NAME);

            ActionHandler handler = new ActionHandler(actionName, ph, url);
            actions.add(handler);
            patterns.put(
                Pattern.compile(Pattern.quote(ph), Pattern.CASE_INSENSITIVE),
                handler);
        }
    }

    /**
     * Returns action service handler instance.
     * @return action service handler instance.
     */
    public static ActionServicesHandler getInstance()
    {
        return serviceHandlerInstance;
    }

    /**
     * Creates the new instance and returns it.
     * @param ctx
     * @return
     */
    public static ActionServicesHandler init(BundleContext ctx)
    {
        return serviceHandlerInstance = new ActionServicesHandler(ctx);
    }

    /**
     * Stops the actions handler.
     */
    public void stop()
    {
        // do nothing for now
    }

    /**
     * Return all phrases extracted from the configured actions.
     * @return all phrases extracted from the configured actions.
     */
    public List<String> getPhrases()
    {
        return actions.stream()
            .map(a -> a.getPhrase())
            .collect(Collectors.toList());
    }

    /**
     * A final notifications had been received, check it against the patterns
     * and if something is detected post the results.
     * @param result the transcription received.
     */
    public void notifyActionServices(TranscriptionResult result)
    {
        for (Map.Entry<Pattern, ActionHandler> en : patterns.entrySet())
        {
            TranscriptionAlternative alt
                = result.getAlternatives().iterator().next();
            String msg = alt.getTranscription();
            Matcher match = en.getKey().matcher(msg);
            if (match.find())
            {
                // lets modify it so we can remove the trigger command text
                String newText = msg.substring(match.end()).trim();
                result = new TranscriptionResult(
                    result.getParticipant(),
                    result.getMessageID(),
                    result.getTimeStamp(),
                    result.isInterim(),
                    result.getLanguage(),
                    result.getStability(),
                    new TranscriptionAlternative(newText, alt.getConfidence()));

                JSONObject jsonResult =
                    LocalJsonTranscriptHandler.createTranscriptionJSONObject(result);
                String roomName
                    = result.getParticipant().getTranscriber().getRoomName();
                jsonResult.put(
                    LocalJsonTranscriptHandler
                        .JSON_KEY_FINAL_TRANSCRIPT_ROOM_NAME,
                    roomName);


                ActionHandler handler = en.getValue();
                if (logger.isDebugEnabled())
                {
                    logger.debug("Action detected:" + handler.getName()
                        + ", will push to address:" + handler.getUrl());
                }

                // store that we had sent a result to that handler for this room
                if (!actionSources.containsKey(roomName))
                {
                    List<ActionHandler> handlers = new ArrayList<>();
                    handlers.add(handler);
                    actionSources.put(roomName, handlers);
                }
                else
                {
                    List<ActionHandler> handlers = actionSources.get(roomName);
                    if (!handlers.contains(handler))
                    {
                        handlers.add(handler);
                    }
                }

                // post to action url
                Util.postJSON(handler.getUrl(), jsonResult);
            }
        }
    }

    /**
     * Notifies action services for a conference end, only if we had ever
     * sent some results to them.
     *
     * @param transcriber the transcriber
     * @param event the event
     */
    public void notifyActionServices(
        Transcriber transcriber, TranscriptEvent event)
    {
        String roomName = transcriber.getRoomName();

        if (event.getEvent() != Transcript.TranscriptEventType.END
            || !actionSources.containsKey(roomName))
            return;

        JSONObject object = new JSONObject();
        object.put(
            LocalJsonTranscriptHandler
                .JSON_KEY_FINAL_TRANSCRIPT_ROOM_NAME,
            roomName);
        object.put(LocalJsonTranscriptHandler.JSON_KEY_EVENT_EVENT_TYPE,
            event.getEvent().toString());
        object.put(LocalJsonTranscriptHandler.JSON_KEY_EVENT_TIMESTAMP,
            String.valueOf(event.getTimeStamp().toEpochMilli()));

        for (ActionHandler handler : actionSources.remove(roomName))
        {
            Util.postJSON(handler.getUrl(), object);
        }
    }
}
