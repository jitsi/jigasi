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

import java.util.*;

/**
 * This class is used to publish TranscriptionResults and Transcript to the
 * what is desired by adding the necessary {@link TranscriptionResultPublisher}
 * and {@link TranscriptPublisher}s
 *
 * @author Nik Vaessen
 */
public class TranscriptHandler
{
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
     * Get the {@link TranscriptPublisher.Promise}s which can handle a
     * {@link Transcript} for all given {@link TranscriptPublisher}s
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
}
