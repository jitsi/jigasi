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

import java.util.function.*;

/**
 * A TranscriptionService provides the ability to send audio either
 * as complete audio fragments or as a continuous stream of audio packets
 * to a speech-to-text API for transcription.
 *
 * Fragments of audio are assumed to lie in the range of 15 to 60 seconds
 * of audio.
 *
 * A continuous stream of audio packets has no expected maximum duration.
 *
 * @author Nik Vaessen
 */
public interface TranscriptionService
{
    /**
     * Get whether this TranscriptionService supports sending fragments of audio
     *
     * @return true when fragmented transcription is supported, false otherwise
     */
    boolean supportsFragmentTranscription();

    /**
     * Sends an audio fragment request to the service to be transcribed.
     * The fragment of audio should contain at least one spoken word for
     * transcription to be successful. Fragments longer than 60 seconds
     * might not be supported by most speech-to-text or will require
     * to much processing time to get a timely answer.
     *
     * @param request the TranscriptionRequest which holds the audio
     * @param resultConsumer a Consumer of the transcription result
     * @throws UnsupportedOperationException when this service does not support
     * fragmented audio speech-to-text
     */
    void sendSingleRequest(TranscriptionRequest request,
                           Consumer<TranscriptionResult> resultConsumer)
        throws UnsupportedOperationException;

    /**
     * Get whether this TranscriptionService supports sending a continuous
     * stream of audio packets
     *
     * @return true when streaming transcription is supported, false otherwise
     */
    boolean supportsStreamRecognition();

    /**
     * Initialise a session which sends a continuous stream of audio to the
     * service to be transcribed
     *
     * @param participant the participant starting the session.
     * @return a session which can be given new packets and which can be polled
     * or subscribed to for new incoming transcription results
     * @throws UnsupportedOperationException when the service does not support
     * streaming speech-to-text
     */
    StreamingRecognitionSession initStreamingSession(Participant participant)
        throws UnsupportedOperationException;

    /**
     * Get whether this service is properly configured and able to connect
     * to the service
     *
     * @return true when properly configured, false otherwise
     */
    boolean isConfiguredProperly();

    /**
     * An interface for a session managing the transcription of a stream of
     * audio. Allows giving small packets of audio as well as subscribing
     * (multiple) listener(s) which will retrieve TranscriptionResults
     * The AudioFormat and Locale of the audio are not expected to
     * change during the session
     */
    interface StreamingRecognitionSession
    {
        /**
         * Give the next fragment of audio on the continuous stream of
         * audio.
         *
         * @param request a TranscriptionRequest which holds the next fragment
         *                of audio in a continuous stream
         */
        void sendRequest(TranscriptionRequest request);

        /**
         * Gracefully end the session. Audio which was send but has not been
         * transcribed should still be processed
         */
        void end();

        /**
         * Get whether this StreamingRecognitionSession has already been told
         * to stop or has been stopped
         *
         * @return true when the session is stopping or has been stopped
         */
        boolean ended();

        /**
         * Add a TranscriptionListener which will be notified when a new
         * transcription result will come in
         *
         * @param listener a listener which should be notified of any incoming
         *                 results
         */
        void addTranscriptionListener(TranscriptionListener listener);
    }
}
