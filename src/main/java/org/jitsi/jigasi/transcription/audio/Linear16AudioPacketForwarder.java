package org.jitsi.jigasi.transcription.audio;

import org.jitsi.impl.neomedia.device.*;

import javax.media.*;
import javax.media.rtp.*;

public class Linear16AudioPacketForwarder
    extends AbstractForwarder
    implements ReceiveStreamBufferListener
{
    /**
     * The MediaDevice which will get all audio to transcribe
     */
    private TranscribingAudioMixerMediaDevice mediaDevice
        = new TranscribingAudioMixerMediaDevice(this);

    /**
     * Get the MediaDevice this transcriber is listening to for audio
     *
     * @return the AudioMixerMediaDevice which should receive all audio needed
     * to be transcribed
     */
    public AudioMixerMediaDevice getMediaDevice()
    {
        return this.mediaDevice;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void bufferReceived(ReceiveStream receiveStream, Buffer buffer)
    {
        long ssrc = receiveStream.getSSRC() & 0xffffffffL;
        byte[] audio = (byte[]) buffer.getData();

        javax.media.format.AudioFormat audioFormat
            = (javax.media.format.AudioFormat) buffer.getFormat();

        if(!audioFormat.getEncoding()
            .equalsIgnoreCase(javax.media.format.AudioFormat.LINEAR))
        {
            throw new IllegalStateException("receiving audio which is not" +
                                                " linear16");
        }

        AudioFormat format = new AudioFormat(audioFormat);

        AudioSegment segment = new AudioSegment(ssrc, audio, format);

        processSegment(segment);
    }
}
