package org.jitsi.jigasi.transcription.audio;

import java.util.concurrent.*;

public class AudioFormat
{
    public final static String LINEAR16_ENCODING = "linear16";
    public final static String OPUS_ENCODING = "opus";

    private final static int OPUS_FRAME_LENGTH_IN_MS = 20;

    private String encoding;
    private int sampleRate;

    private javax.media.format.AudioFormat af = null;

    public AudioFormat(String encoding, int sampleRate)
    {
        this.encoding = encoding;
        this.sampleRate = sampleRate;
    }

    public AudioFormat(javax.media.format.AudioFormat af)
    {
        if(!af.getEncoding().
            equalsIgnoreCase(javax.media.format.AudioFormat.LINEAR))
        {
            throw new IllegalArgumentException("can currently only accept " +
                                                   "linear audio format");
        }

        this.af = af;
        this.encoding = LINEAR16_ENCODING;
        this.sampleRate = new Double(af.getSampleRate()).intValue();
    }

    @Override
    public boolean equals(Object o)
    {
        if(o instanceof AudioFormat)
        {
            return ((AudioFormat) o).getEncoding().equals(this.getEncoding());
        }

        return false;
    }

    public String getEncoding()
    {
        return this.encoding;
    }

    public int getSampleRate()
    {
        return this.sampleRate;
    }


    public long computeDurationInMs(long length)
    {
        if(getEncoding().equalsIgnoreCase(LINEAR16_ENCODING))
        {
            return TimeUnit.NANOSECONDS.toMillis(af.computeDuration(length));
        }

        else if(getEncoding().equalsIgnoreCase(OPUS_ENCODING))
        {
            //todo dynamically check if a pkt is 20 ms
            return OPUS_FRAME_LENGTH_IN_MS;
        }

        return -1;
    }




}
