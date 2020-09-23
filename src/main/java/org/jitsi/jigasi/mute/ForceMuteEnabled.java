package org.jitsi.jigasi.mute;

import org.jitsi.jigasi.JvbConference;

public class ForceMuteEnabled
    implements ForceMute
{
    private JvbConference conference;

    private boolean allowedToSpeak;

    public ForceMuteEnabled(JvbConference jvbConference)
    {
        this.conference = jvbConference;
        this.allowedToSpeak = false;
    }

    @Override
    public void requestAudioMute(boolean muted)
    {
        if (muted == false)
        {
            if (this.allowedToSpeak == false)
            {
                //

                return;
            }
        }

        this.conference.requestAudioMute(muted);
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
