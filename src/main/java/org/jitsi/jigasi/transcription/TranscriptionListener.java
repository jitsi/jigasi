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

/**
 * A listener object which will be notified when a Transcription receives
 * a new Transcription result. This can for example be used to push new
 * results to a conference to give participants immediate results
 *
 * @author Nik Vaessen
 */
public interface TranscriptionListener
{
    /**
     * Notify this listener that a TranscriptionResult has come in
     *
     * @param result the result which has come in
     */
    void notify(TranscriptionResult result);

    /**
     * Notify this listener that no new results will come in as
     * the transcription has been completed
     */
    void completed();

    /**
     * Notify this listener that the transcriptions has failed and no new
     * results will come in.
     * @param reason the failure reason
     */
    void failed(FailureReason reason);

    /**
     * Passed as an argument to {@link #failed(FailureReason)}.
     */
    enum FailureReason
    {
        /**
         * The request quota limit set by the transcription service provider
         * has been exhausted.
         */
        RESOURCES_EXHAUSTED
    }
}
