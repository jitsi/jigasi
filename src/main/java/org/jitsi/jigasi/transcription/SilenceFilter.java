/*
 * Jigasi, the JItsi GAteway to SIP.
 *
 * Copyright @ 2018 - present 8x8, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jitsi.jigasi.transcription;

import org.jitsi.webrtcvadwrapper.*;
import org.jitsi.webrtcvadwrapper.audio.*;

/**
 * This class uses {@link SpeechDetector<ByteSignedPcmAudioSegment>) to
 * detect silent audio.
 *
 * @author Nik Vaessen
 */
public class SilenceFilter
{
    /**
     * The vad mode which should be used for the {@link WebRTCVad}.
     */
    private static final int VAD_MODE = 1;

    /**
     * The sample rate of the audio given to {@link WebRTCVad}.
     */
    private static final int VAD_AUDIO_HZ = 48000;

    /**
     * The length of each consecutive segment which is given to the
     * {@link WebRTCVad}.
     */
    private static final int VAD_SEGMENT_SIZE_MS = 20;

    /**
     * The length of the audio which is considered when determining speech.
     * In this case 10 segments (as defined above) are considered.
     */
    private static final int VAD_WINDOW_SIZE_MS = 200;

    /**
     * The audio is considered as silent when 8 out of 10 previous segments
     * where determined to be not speech.
     */
    private static final int VAD_THRESHOLD = 8;

    /**
     * The {@link SpeechDetector} object used to detect silent audio.
     */
    private SpeechDetector<ByteSignedPcmAudioSegment> speechDetector
        = new SpeechDetector<>(
            VAD_AUDIO_HZ,
            VAD_MODE,
            VAD_SEGMENT_SIZE_MS,
            VAD_WINDOW_SIZE_MS,
            VAD_THRESHOLD);

    /**
     * Whether the previously given segment was determined to be speech.
     */
    private boolean previousSegmentWasSpeech;

    /**
     * Whether the latest speech segment was determined to be speech.
     */
    private boolean isCurrentlySpeech;

    /**
     * Give a new segment of audio
     *
     * @param audio the audio
     */
    public void giveSegment(byte[] audio)
    {
        ByteSignedPcmAudioSegment segment
            = new ByteSignedPcmAudioSegment(audio);

        speechDetector.nextSegment(segment);
        previousSegmentWasSpeech = isCurrentlySpeech;
        isCurrentlySpeech = speechDetector.isSpeech();
    }

    /**
     * Get the whole window size in a single array.
     *
     * @return the whole window as an array of bytes.
     */
    public byte[] getSpeechWindow()
    {
        return ByteSignedPcmAudioSegment
            .merge(speechDetector.getLatestSegments())
            .getAudio();
    }

    /**
     * Whether the current window is considered to be speech.
     * @return
     */
    public boolean shouldFilter()
    {
        return !speechDetector.isSpeech();
    }

    /**
     * Whether the last given segment indicated that the audio transistioned
     * from silence to speech, which means the whole window is now
     * considered speech.
     *
     * @return true when a transition from silence to speech took place.
     */
    public boolean newSpeech()
    {
        return !previousSegmentWasSpeech && isCurrentlySpeech;
    }

}
