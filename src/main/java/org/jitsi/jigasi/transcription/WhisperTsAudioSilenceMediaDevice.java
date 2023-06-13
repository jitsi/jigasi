package org.jitsi.jigasi.transcription;

import javax.media.Player;
import javax.media.Processor;
import javax.media.protocol.CaptureDevice;
import javax.media.protocol.DataSource;

import org.jitsi.impl.neomedia.device.AudioMediaDeviceImpl;
import org.jitsi.impl.neomedia.device.AudioMediaDeviceSession;
import org.jitsi.impl.neomedia.device.AudioSilenceMediaDevice;
import org.jitsi.impl.neomedia.device.MediaDeviceSession;
import org.jitsi.service.neomedia.MediaDirection;

public class WhisperTsAudioSilenceMediaDevice extends AudioSilenceMediaDevice {
    private boolean clockOnly = false;

    public WhisperTsAudioSilenceMediaDevice() {
    }

    public WhisperTsAudioSilenceMediaDevice(boolean clockOnly) {
        this.clockOnly = clockOnly;
    }

    protected CaptureDevice createCaptureDevice() {
        return new WhisperTsAudioSilenceCaptureDevice(false);
    }

    protected Processor createPlayer(DataSource dataSource) {
        return null;
    }

    public MediaDeviceSession createSession() {
        return new AudioMediaDeviceSession(this) {
            protected Player createPlayer(DataSource dataSource) {
                return null;
            }
        };
    }

    public MediaDirection getDirection() {
        return MediaDirection.SENDRECV;
    }
}
