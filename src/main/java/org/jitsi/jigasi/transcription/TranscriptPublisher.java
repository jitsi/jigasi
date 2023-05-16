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

import org.jitsi.service.neomedia.device.*;

/**
 * This interface is used to save a transcript to a desired location
 *
 * @author Nik Vaessen
 */
public interface TranscriptPublisher
{
    /**
     * Get a {@link Promise} which will be able to provide a description
     * and accept a {@link Transcript} to publish
     *
     * @return the {@link Promise}
     */
    Promise getPublishPromise();

    /**
     * A promise will be able to give a description of where or how the
     * {@link Transcript} will be published before it is actually published
     */
    interface Promise
    {
        /**
         * Whether a description is available
         *
         * @return true if there is description, false otherwise
         */
        boolean hasDescription();

        /**
         * Get the description of how the transcript will be published once
         * this {@link Promise#publish(Transcript)} method has been called.
         *
         * @return the description as a String, which will be empty when
         * {@link this#hasDescription()} is false
         */
        String getDescription();

        /**
         * Publish the given {@link Transcript} to the desired location.
         * Can only be called once.
         *
         * @param transcript the transcript to publish
         */
        void publish(Transcript transcript);

        /**
         * Give the {@link MediaDevice}
         * which is required to record the audio with a
         * {@link org.jitsi.service.neomedia.recording.Recorder} object.
         * If recording is desired, calling this method will start the
         * recording, otherwise it will do nothing.
         *
         * @param device the MediaDevice which will be used to
         * record the audio
         */
        void maybeStartRecording(MediaDevice device);

    }
}
