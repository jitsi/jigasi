package org.jitsi.jigasi.mute;

import org.jitsi.jigasi.JvbConference;

public class ForceMuteEnabled
    extends ForceMute
{

    private boolean allowedToSpeak;

    public ForceMuteEnabled(JvbConference jvbConference)
    {
        this.conference = jvbConference;
        this.allowedToSpeak = false;
    }

    @Override
    public boolean requestAudioMute(boolean mute)
    {
        if (!this.allowedToSpeak)
        {
            if (mute)
            {
                return this.conference.sendAudioMuteRequest(mute);
            }

            return false;
        }

        // TODO reset allowed to speak flag?
        allowedToSpeak = false;

        return this.conference.sendAudioMuteRequest(mute);
    }

    @Override
    public void setAllowedToSpeak(boolean allowedToSpeak)
    {
        this.allowedToSpeak = allowedToSpeak;
    }

    @Override
    public boolean enabled() {
        return true;
    }
}
