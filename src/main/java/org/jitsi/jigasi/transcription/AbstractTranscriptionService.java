package org.jitsi.jigasi.transcription;

import org.jitsi.impl.neomedia.device.AudioMixerMediaDevice;
import org.jitsi.impl.neomedia.device.AudioSilenceMediaDevice;
import org.jitsi.impl.neomedia.device.ReceiveStreamBufferListener;

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
}
