package org.jitsi.jigasi.mute;

public interface ForceMute
{
    void requestAudioMute(boolean muted);

    void setAllowedToSpeak(boolean allowedToSpeak);
}
