package org.jitsi.jigasi.mute;

import org.jitsi.jigasi.JvbConference;

public class ForceMuteDisabled
    implements ForceMute
{

    private JvbConference conference;

    public ForceMuteDisabled(JvbConference jvbConference)
    {
        this.conference = jvbConference;
    }

    @Override
    public void requestAudioMute(boolean muted)
    {
        this.conference.requestAudioMute(muted);
    }

    @Override
    public void setAllowedToSpeak(boolean allowedToSpeak)
    {

    }
}
