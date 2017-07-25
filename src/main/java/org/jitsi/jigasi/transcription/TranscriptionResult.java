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
 * A TranscriptionResult which is created when audio has been transcribed
 * by a speech-to-text service. It holds the request which result an instance
 * of this class represents
 *
 * @author Nik Vaessen
 */
public class TranscriptionResult
{
    /**
     * The transcription of the audio in the request
     */
    private String transcription;

    /**
     * The name of the participant saying the transcription
     * Can be added after result has come in if TranscriptionService has no way
     * of knowing
     */
    private String name;

    /**
     * Create a TranscriptionResult of a TranscriptionRequest which
     * will hold the text of the audio
     *
     * @param transcription the transcription of the audio in the request
     */
    public TranscriptionResult(String transcription)
    {
        this.transcription = transcription;
    }

    /**
     * Create a TranscriptionResult of a TranscriptionRequest which
     * will hold the text of the audio
     *
     * @param transcription the transcription of the audio in the request
     * @param name the name of the participant who said the transcription
     */
    public TranscriptionResult(String transcription, String name)
    {
        this.transcription = transcription;
        this.name = name;
    }

    /**
     * The transcription of the audio in the request
     *
     * @return the transcription as a String
     */
    public String getTranscription()
    {
        return transcription;
    }

    /**
     * Get the name of the participant saying the audio
     *
     * @return the name
     */
    public String getName()
    {
        return name;
    }

    /**
     * Set the name of the participant who said this piece of transcription
     *
     * @param name the name
     */
    public void setName(String name)
    {
        this.name = name;
    }

    @Override
    public String toString()
    {
        return name == null ?
                transcription : name + ": " + transcription;
    }
}
