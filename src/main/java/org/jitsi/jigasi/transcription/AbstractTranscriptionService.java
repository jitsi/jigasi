package org.jitsi.jigasi.transcription;

import org.jitsi.impl.neomedia.device.AudioMixerMediaDevice;
import org.jitsi.impl.neomedia.device.AudioSilenceMediaDevice;
import org.jitsi.impl.neomedia.device.ReceiveStreamBufferListener;

public abstract class AbstractTranscriptionService
    implements TranscriptionService
{
    private TranscribingAudioMixerMediaDevice mediaDevice = null;

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

    //    private static final Format[] SUPPORTED_FORMATS
//            = new Format[]
//            {
//                    new AudioFormat(
//                            AudioFormat.LINEAR,
//                            16000,
//                            16,
//                            1,
//                            AudioFormat.LITTLE_ENDIAN,
//                            AudioFormat.SIGNED,
//                            Format.NOT_SPECIFIED,
//                            Format.NOT_SPECIFIED,
//                            Format.byteArray)
//            };
//
//    /**
//     * The MediaDevice which will get all audio to transcribe
//     */
//    private TranscribingAudioMixerMediaDevice mediaDevice = new TranscribingAudioMixerMediaDevice(
//            new AudioSilenceMediaDevice()
//            {
//
//                protected CaptureDevice createCaptureDevice()
//                {
//                    return new AudioSilenceCaptureDevice(true)
//                    {
//                        protected Format[] getSupportedFormats(int streamIndex)
//                        {
//                            return SUPPORTED_FORMATS.clone();
//                        }
//                    };
//                }
//            }, this);
}
