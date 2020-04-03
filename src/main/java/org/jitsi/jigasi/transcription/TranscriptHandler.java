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

import java.util.*;

/**
 * This class is used to publish TranscriptionResults and Transcript to whatever
 * is desired by adding the necessary {@link TranscriptionResultPublisher}
 * and {@link TranscriptPublisher}s
 *
 * @author Nik Vaessen
 */
public class TranscriptHandler
{
    /**
     * Property name for saving transcript in json
     */
    public final static String P_NAME_SAVE_JSON
        = "org.jitsi.jigasi.transcription.SAVE_JSON";
    /**
     * Property name for saving transcript in txt
     */
    public final static String P_NAME_SAVE_TXT
        = "org.jitsi.jigasi.transcription.SAVE_TXT";

    /**
     * Property name for sending result in json
     */
    public final static String P_NAME_SEND_JSON
        = "org.jitsi.jigasi.transcription.SEND_JSON";

    /**
     * Property name for sending result in txt
     */
    public final static String P_NAME_SEND_TXT
        = "org.jitsi.jigasi.transcription.SEND_TXT";

    /**
     * Property name for sending result in json to remote service.
     */
    public final static String P_NAME_SEND_JSON_REMOTE
        = "org.jitsi.jigasi.transcription.SEND_JSON_REMOTE_URLS";

    /**
     * Whether to publish final transcripts by locally saving them in json
     * format
     */
    private final static boolean SAVE_JSON = false;

    /**
     * Whether to publish final transcripts by locally saving them in txt format
     */
    private final static boolean SAVE_TXT = false;

    /**
     * Whether to send results in json to
     * {@link net.java.sip.communicator.service.protocol.ChatRoom} of muc
     */
    private final static boolean SEND_JSON = true;

    /**
     * Whether to send results in txt to
     * {@link net.java.sip.communicator.service.protocol.ChatRoom} of muc
     */
    private final static boolean SEND_TXT = false;

    /**
     * The list of {@link TranscriptionResultPublisher} which will handle
     * {@link TranscriptionResult}s
     */
    private List<TranscriptionResultPublisher> resultPublishers
        = new LinkedList<>();

    /**
     * The list of {@link TranscriptPublisher} which will handle
     * {@link Transcript}s
     */
    private List<TranscriptPublisher> transcriptPublishers = new LinkedList<>();

    /**
     * Set up a new TranscriptHandler. The {@link TranscriptPublisher} and
     * {@link TranscriptionResultPublisher}s can be manually added or some of
     * them can be added via setting the static boolean flags.
     */
    public TranscriptHandler()
    {
        LocalJsonTranscriptHandler jsonHandler
            = new LocalJsonTranscriptHandler();
        LocalTxtTranscriptHandler txtHandler = new LocalTxtTranscriptHandler();

        if(getStoreInJson())
        {
            this.add((TranscriptPublisher) jsonHandler);
        }
        if(getStoreInTxt())
        {
            this.add((TranscriptPublisher) txtHandler);
        }
        if(getSendInJSON())
        {
            this.add((TranscriptionResultPublisher) jsonHandler);
        }
        if(getSendInTxt())
        {
            this.add((TranscriptionResultPublisher) txtHandler);
        }
        String urls;
        if ((urls = getSendJSONToRemote()) != null)
        {
            this.add((TranscriptionResultPublisher)
                new RemotePublisherTranscriptionHandler(urls));
        }
    }

    /**
     * Handle a {@link TranscriptionResult} with all given
     * {@link TranscriptionResultPublisher}'s
     *
     * @param room the {@link ChatRoom} to send the result to
     * @param result the {@link TranscriptionResult} to handle
     */
    public void publishTranscriptionResult(ChatRoom room,
                                           TranscriptionResult result)
    {
        for(TranscriptionResultPublisher p : resultPublishers)
        {
            p.publish(room, result);
        }
    }

    /**
     * Handle a {@link TranslationResult} with all given
     * {@link TranscriptionResultPublisher}'s
     *
     * @param room the {@link ChatRoom} to send the result to
     * @param result the {@link TranslationResult} to handle
     */
    public void publishTranslationResult(ChatRoom room,
                                         TranslationResult result)
    {
        for(TranscriptionResultPublisher p : resultPublishers)
        {
            p.publish(room, result);
        }
    }

    /**
     * Get a list of {@link TranscriptPublisher.Promise}s which can handle a
     * {@link Transcript}. The list will contain such a
     * {@link TranscriptPublisher.Promise} for all {@link TranscriptPublisher}s
     * added to this {@link TranscriptHandler}
     *
     * @return a list of {@link TranscriptPublisher.Promise}s
     */
    public List<TranscriptPublisher.Promise> getTranscriptPublishPromises()
    {
        List<TranscriptPublisher.Promise> promises = new LinkedList<>();
        for(TranscriptPublisher p : transcriptPublishers)
        {
            promises.add(p.getPublishPromise());
        }

        return promises;
    }

    /**
     * Get a list of {@link TranscriptionResultPublisher}s which can handle a
     * {@link Transcript}.
     *
     * @return a list of {@link TranscriptionResultPublisher}s
     */
    public List<TranscriptionResultPublisher> getTranscriptResultPublishers()
    {
        return resultPublishers;
    }

    /**
     * Add a {@link TranscriptPublisher}
     *
     * @param publisher the {@link TranscriptPublisher} to add
     */
    public void add(TranscriptPublisher publisher)
    {
        transcriptPublishers.add(publisher);
    }

    /**
     * Remove a {@link TranscriptPublisher}
     *
     * @param publisher the {@link TranscriptPublisher} to remove
     */
    public void remove(TranscriptPublisher publisher)
    {
        transcriptPublishers.remove(publisher);
    }


    /**
     * Add a {@link TranscriptionResultPublisher}
     *
     * @param publisher the {@link TranscriptionResultPublisher} to add
     */
    public void add(TranscriptionResultPublisher publisher)
    {
        resultPublishers.add(publisher);
    }

    /**
     * Remove a {@link TranscriptionResultPublisher}
     *
     * @param publisher the {@link TranscriptionResultPublisher} to remove
     */
    public void remove(TranscriptionResultPublisher publisher)
    {
        resultPublishers.remove(publisher);
    }

    /**
     * Get whether to send results in JSON
     *
     * @return true if results are send in json, false otherwise
     */
    private boolean getSendInJSON()
    {
        return JigasiBundleActivator.getConfigurationService()
            .getBoolean(P_NAME_SEND_JSON, SEND_JSON);
    }

    /**
     * Get whether to send results in TXT
     *
     * @return true if results are send in txt, false otherwise
     */
    private boolean getSendInTxt()
    {
        return JigasiBundleActivator.getConfigurationService()
            .getBoolean(P_NAME_SEND_TXT, SEND_TXT);
    }

    /**
     * Get whether to send results in JSON to remote address.
     *
     * @return the URLs of the remote services, or null if not enabled.
     */
    private String getSendJSONToRemote()
    {
        return JigasiBundleActivator.getConfigurationService()
            .getString(P_NAME_SEND_JSON_REMOTE);
    }

    /**
     * Get whether to save transcript in JSON
     *
     * @return true if saved in json, false otherwise
     */
    private boolean getStoreInJson()
    {
        return JigasiBundleActivator.getConfigurationService()
            .getBoolean(P_NAME_SAVE_JSON, SAVE_JSON);
    }

    /**
     * Get whether to save transcripts in txt
     *
     * @return true if saved in txt, false otherwise
     */
    private boolean getStoreInTxt()
    {
        return JigasiBundleActivator.getConfigurationService()
            .getBoolean(P_NAME_SAVE_TXT, SAVE_TXT);
    }
}
