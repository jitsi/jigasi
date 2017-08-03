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
 * An alternative transcription of a chunk of audio.
 *
 * @author Boris Grozev
 */
public class TranscriptionAlternative
{
    /**
     * The default confidence value.
     */
    private static double DEFAULT_CONFIDENCE = 1.0D;

    /**
     * The text of the transcription alternative.
     */
    private String transcription;

    /**
     * The confidence level.
     */
    private double confidence;

    /**
     * Initializes a new {@link TranscriptionAlternative} instance.
     * @param transcription the text of the transcription alternative.
     * @param confidence the confidence level.
     */
    public TranscriptionAlternative(String transcription, double confidence)
    {
        this.transcription = transcription;
        this.confidence = confidence;
    }

    /**
     * Initializes a new {@link TranscriptionAlternative} instance with a
     * default confidence level.
     * @param transcription the text of the transcription alternative.
     */
    public TranscriptionAlternative(String transcription)
    {
        this(transcription, DEFAULT_CONFIDENCE);
    }

    /**
     * @return the text of this transcription alternative.
     */
    public String getTranscription()
    {
        return transcription;
    }

    /**
     * @return the confidence level of this transcription alternative.
     */
    public double getConfidence()
    {
        return confidence;
    }

}
