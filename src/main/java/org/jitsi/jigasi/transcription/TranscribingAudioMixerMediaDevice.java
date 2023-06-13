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

import org.jitsi.impl.neomedia.device.*;
import org.jitsi.impl.neomedia.format.MediaFormatImpl;
import org.jitsi.impl.neomedia.jmfext.media.renderer.audio.AbstractAudioRenderer;
import org.jitsi.jigasi.JigasiBundleActivator;
import org.jitsi.service.neomedia.MediaDirection;
import org.jitsi.service.neomedia.QualityPreset;
import org.jitsi.service.neomedia.RTPExtension;
import org.jitsi.service.neomedia.codec.EncodingConfiguration;
import org.jitsi.service.neomedia.format.MediaFormat;
import org.jitsi.utils.MediaType;
import org.jitsi.utils.logging.Logger;

import javax.media.Format;
import javax.media.format.AudioFormat;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.Key;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * AudioMixerMediaDevice which adds a {@link ReceiveStreamBufferListener} to
 * itself which gets all the audio data going to the AudioMixer.
 *
 * This audio can be distinguished by participant by looking at the SSRC
 * of the {@link javax.media.rtp.ReceiveStream} providing the audio
 *
 * @author Nik Vaessen
 */
public class TranscribingAudioMixerMediaDevice
    extends AudioMixerMediaDevice
{

    /**
     * Create a new MediaDevice which does not output any audio
     * and has a listener for all other audio
     */
    public TranscribingAudioMixerMediaDevice(
            AudioSilenceMediaDevice device,
            ReceiveStreamBufferListener listener)
    {
        super(device);
//        super(new WhisperTsAudioSilenceMediaDevice());
        super.setReceiveStreamBufferListener(listener);
    }



}
