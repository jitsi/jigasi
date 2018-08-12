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

import java.time.Instant;

///**
// * A TranslatedSpeechEvent extends a normal TranscriptEvent by including a
// * TranslationResult
// *
// * APP.conference._room.dial("jitsi_meet_transcribe")
// *
// * APP.conference._room.setLocalParticipantProperty('translation_language','hi');
// *
// * APP.conference._room.setLocalParticipantProperty('transcription_language','hi-in');
// *
// * APP.conference._room.setLocalParticipantProperty('translation_language','en');
// *
// * @author Praveen Kumar Gupta
// */
public class TranslatedSpeechEvent
    extends TranscriptEvent
{
    /**
     * The transcriptionResult
     */
    private TranslationResult result;

    /**
     * Create a ResultHolder with a given TranslationResult and timestamp
     *
     * @param timeStamp the time when the result was received
     * @param result the result which was received
     */
    TranslatedSpeechEvent(Instant timeStamp, TranslationResult result)
    {
        super(timeStamp, result.getTranscriptionResult().getParticipant(),
            Transcript.TranscriptEventType.SPEECH);
        this.result = result;
    }

    /**
     * Get the TranslationResult this holder is holding
     *
     * @return the result
     */
    public TranslationResult getResult()
    {
        return result;
    }

}

