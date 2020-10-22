package org.jitsi.jigasi.mute;

import org.jitsi.jigasi.JvbConference;

public class ForceMuteDisabled
    extends ForceMute
{

    public ForceMuteDisabled(JvbConference jvbConference)
    {
        this.conference = jvbConference;
    }

    @Override
    public boolean requestAudioMute(boolean mute)
    {
        return this.conference.sendAudioMuteRequest(mute);
    }

    @Override
    public void setAllowedToSpeak(boolean allowedToSpeak)
    {

    }

    @Override
    public boolean enabled() {
        return false;
    }
}
