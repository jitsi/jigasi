package org.jitsi.jigasi.transcription.audio;

import java.util.*;

public class AudioBuffer
{
    private final static int MAXIMUM_DURATION_IN_BUFFER_IN_MS = 500;

    private final LinkedList<AudioSegment> segments = new LinkedList<>();
    private long currentDurationInMs = 0;
    private int currentLength = 0;

    public boolean doesFit(AudioSegment segment)
    {
        synchronized (segments)
        {
            return currentDurationInMs + segment.getDurationInMs()
                <= MAXIMUM_DURATION_IN_BUFFER_IN_MS;
        }
    }

    public boolean exceedsBufferSize(AudioSegment segment)
    {
        synchronized (segments)
        {
            return segment.getDurationInMs() > MAXIMUM_DURATION_IN_BUFFER_IN_MS;
        }
    }

    public AudioSegment getBufferedSegments()
    {
        synchronized (segments)
        {
            if(segments.isEmpty())
            {
                throw new IllegalStateException("buffer is currently empty");
            }
            System.out.println("length before merging: " + currentLength + ", size: " + segments.size());
            byte[] audio = new byte[currentLength];

            int pos = 0;
            for(AudioSegment segment : segments)
            {
                byte[] tmp = segment.getAudio();
                System.arraycopy(audio, pos, tmp, 0, tmp.length);
                pos += tmp.length;
            }

            AudioFormat format = segments.get(0).getFormat();
            long ssrc = segments.get(0).getSsrc();

            return new AudioSegment(ssrc, audio, format);
        }
    }

    public long spaceLeft()
    {
        synchronized (segments)
        {
            return MAXIMUM_DURATION_IN_BUFFER_IN_MS - currentDurationInMs;
        }
    }

    public void clear()
    {
        synchronized (segments)
        {
            segments.clear();
            currentDurationInMs = 0;
        }
    }

    public void put(AudioSegment segment)
    {
        synchronized (segments)
        {
            System.out.println("putting a segment of duration " + segment.getDurationInMs() + " and of length " + segment.getAudio().length + " in the buffer");
            if (!doesFit(segment))
            {
                throw new IllegalArgumentException(
                    "given segment does not fit in" +
                        " the buffer");
            }

            segments.addLast(segment);
            currentDurationInMs += segment.getDurationInMs();
            currentLength += segment.getAudio().length;
        }
    }


}
