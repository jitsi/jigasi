/*
 * Jigasi, the JItsi GAteway to SIP.
 *
 * Copyright @ 2018 - present 8x8, Inc.
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

import org.jitsi.impl.neomedia.device.*;

public abstract class AbstractTranscriptionService
        implements TranscriptionService
{
    protected TranscribingAudioMixerMediaDevice mediaDevice = null;

    /**
     * Get the MediaDevice this transcriber is listening to for audio
     *
     * @return the AudioMixerMediaDevice which should receive all audio needed
     * to be transcribed
     */
    public AudioMixerMediaDevice getMediaDevice(ReceiveStreamBufferListener listener)
    {
        if (this.mediaDevice == null)
        {
            this.mediaDevice = new TranscribingAudioMixerMediaDevice(new AudioSilenceMediaDevice(), listener);
        }

        return this.mediaDevice;
    }

    /**
     * Returns true if the SilenceFilter should be disabled for this
     * TranscriptionService.
     */
    public boolean disableSilenceFilter()
    {
        return false;
    }
}
