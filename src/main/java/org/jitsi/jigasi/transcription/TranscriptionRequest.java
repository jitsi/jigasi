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

import javax.media.format.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * A TranscriptionRequest serves as a holder for some audio fragment
 * which needs to be transcribed
 *
 * @author Nik Vaessen
 */
public class TranscriptionRequest
{

    /**
     * The audio which needs to be transcribed
     */
    private byte[] audio;

    /**
     * The AudioFormat of the audio in this instance
     */
    private AudioFormat format;

    /**
     * The Locale of the audio. It is expected to contain both a Language
     * and a region, at the minimum
     */
    private Locale locale;

    /**
     * Create a TranscriptionRequest which holds the audio to be
     * transcribed along with its AudioFormat
     *
     * @param audio the audio fragment to be transcribed as an array of bytes
     * @param format the format of the given audio fragment
     * @param locale the locale of the audio being spoken
     */
    public TranscriptionRequest(byte[] audio, AudioFormat format,
                                Locale locale)
    {
        this.audio = audio;
        this.format = format;
        this.locale = locale;
    }

    /**
     * Get the length of the audio in this {@link TranscriptionRequest} in
     * milliseconds
     *
     * @return the duration of the audio in milliseconds or -1 when unknown
     */
    public long getDurationInMs()
    {
        if(this.format == null)
        {
            return -1;
        }

        return TimeUnit.NANOSECONDS.toMillis(
            this.format.computeDuration(this.audio.length));
    }


    /**
     * The audio this instance is holding
     *
     * @return an audio fragment as an array of bytes
     */
    public byte[] getAudio()
    {
        return audio;
    }

    /**
     * Get the format of the audio this instance is holding
     *
     * @return the AudioFormat of the audio of this instance
     */
    public AudioFormat getFormat()
    {
        return format;
    }

    /**
     * Get the Locale of the audio
     *
     * @return the locale of the audio
     */
    public Locale getLocale()
    {
        return locale;
    }
}
