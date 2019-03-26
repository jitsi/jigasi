package org.jitsi.jigasi.transcription.audio;

public class AudioSegment
{

    private byte[] audio;
    private AudioFormat format;
    private long ssrc;

    public AudioSegment(long ssrc, byte[] audio, AudioFormat format)
    {
        this.ssrc = ssrc;
        this.audio = audio;
        this.format = format;
    }

    public AudioFormat getFormat()
    {
        return format;
    }

    public byte[] getAudio()
    {
        return audio;
    }

    public long getSsrc()
    {
        return ssrc;
    }

    public long getDurationInMs()
    {
        return getFormat().computeDurationInMs(audio.length);
    }

}
