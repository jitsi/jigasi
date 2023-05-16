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

import java.time.*;

/**
 * A SpeechEvent extends a normal TranscriptEvent by including a
 * TranscriptionResult
 *
 * @author Nik Vaessen
 */
public class SpeechEvent
    extends TranscriptEvent
{
    /**
     * The transcriptionResult
     */
    private TranscriptionResult result;

    /**
     * Create a ResultHolder with a given TranscriptionResult and timestamp
     *
     * @param result the result which was received
     */
    SpeechEvent(TranscriptionResult result)
    {
        super(result.getTimeStamp(), result.getParticipant(), Transcript.TranscriptEventType.SPEECH);
        this.result = result;
    }

    /**
     * Get the TranscriptionResult this holder is holding
     *
     * @return the result
     */
    public TranscriptionResult getResult()
    {
        return result;
    }

}

