package org.jitsi.jigasi.transcription.audio;

import java.util.*;
import java.util.concurrent.*;

public abstract class AbstractForwarder
{
    private List<AudioSegmentListener> listeners = new ArrayList<>();

    private ExecutorService service = Executors.newSingleThreadExecutor();

    public void addListener(AudioSegmentListener listener)
    {
        if (!listeners.contains(listener))
        {
            listeners.add(listener);
        }
    }

    public void removeListener(AudioSegmentListener listener)
    {
        listeners.remove(listener);
    }

    void processSegment(final AudioSegment segment)
    {
        service.submit(() -> {
            forward(segment);
        });
    }

    private void forward(AudioSegment segment)
    {
        listeners.forEach(listener -> listener.receiveAudioSegment(segment));
    }

}
