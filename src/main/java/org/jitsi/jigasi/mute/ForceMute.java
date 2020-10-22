package org.jitsi.jigasi.mute;

import org.jitsi.jigasi.JvbConference;

public abstract class ForceMute
{
    protected JvbConference conference;

    public abstract boolean requestAudioMute(boolean muted);

    public abstract void setAllowedToSpeak(boolean allowedToSpeak);

    public abstract boolean enabled();
}
