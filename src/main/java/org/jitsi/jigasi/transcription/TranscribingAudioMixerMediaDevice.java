package org.jitsi.jigasi.transcription;

import org.jitsi.impl.neomedia.device.AudioMixerMediaDevice;
import org.jitsi.impl.neomedia.device.AudioSilenceMediaDevice;
import org.jitsi.impl.neomedia.device.RawStreamListener;

/**
 * AudioMixerMediaDevice which adds a RawStreamListener to itself which
 * gets all the audio data going to the AudioMixer.
 *
 * This audio can be distinguished by participant by looking at the SSRC
 * of the ReceiveStream providing the audio
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
    public TranscribingAudioMixerMediaDevice(RawStreamListener listener)
    {
        super(new AudioSilenceMediaDevice());
        super.setRawStreamListener(listener);
    }

}
