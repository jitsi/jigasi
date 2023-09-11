/*
 * Jigasi, the JItsi GAteway to SIP.
 *
 * Copyright @ 2023 8x8 Inc.
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
import org.jitsi.service.neomedia.*;

import javax.media.*;
import javax.media.protocol.*;

public class WhisperAudioSilenceMediaDevice
    extends AudioSilenceMediaDevice
{
    protected CaptureDevice createCaptureDevice()
    {
        return new WhisperAudioSilenceCaptureDevice(false);
    }

    protected Processor createPlayer(DataSource dataSource)
    {
        return null;
    }

    public MediaDeviceSession createSession()
    {
        return new AudioMediaDeviceSession(this)
        {
            protected Player createPlayer(DataSource dataSource)
            {
                return null;
            }
        };
    }

    public MediaDirection getDirection()
    {
        return MediaDirection.SENDRECV;
    }
}
