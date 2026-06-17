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

import javax.media.*;
import javax.media.format.*;

/**
 * PCM silence capture device at 24kHz mono 16-bit.
 * Required by services (e.g. OpenAI Realtime API) that reject sample rates below 24000 Hz.
 */
public class PCMAudioSilence24kCaptureDevice
    extends PCMAudioSilenceCaptureDevice
{
    private static final Format[] SUPPORTED_FORMATS
        = new Format[]
        {
            new AudioFormat(
                "LINEAR",
                24000.0,
                16,
                1,
                0,
                1,
                -1,
                -1.0,
                Format.byteArray
            )
        };

    public PCMAudioSilence24kCaptureDevice()
    {
        super(false);
    }

    @Override
    protected Format[] getSupportedFormats(int streamIndex)
    {
        return SUPPORTED_FORMATS.clone();
    }
}
