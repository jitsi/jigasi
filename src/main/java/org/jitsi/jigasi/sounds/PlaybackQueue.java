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
package org.jitsi.jigasi.sounds;

import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.util.*;
import org.jitsi.jigasi.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.service.neomedia.codec.*;

import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * Used to queue audio files for playback. This is used for the IVR.
 */
class PlaybackQueue
    extends Thread
{
    /**
     * The logger.
     */
    private final static Logger logger = Logger.getLogger(PlaybackQueue.class);

    /**
     * Interface to use to execute specific code.
     */
    public interface PlaybackDelegate
    {
        void onPlaybackFinished();
    }

    /**
     * Used internally. This holds information of the file that is to be played and additional code.
     */
    private static class PlaybackData
    {
        /**
         * File to be played, relative path.
         */
        private String playbackFileName;

        /**
         * Used to delegate code.
         */
        private PlaybackDelegate playbackDelegate;

        /**
         * Playback duration of the file in seconds.
         */
        private long playbackDurationSeconds;

        /**
         * The call to send audio to.
         */
        private Call playbackCall;

        /**
         * Constructor of <tt>PlaybackData</tt>
         *
         * @param fileName File to play.
         * @param delegate Delegate code.
         * @param durationSeconds Playback duration in seconds.
         * @param call Call to send audio to.
         */
        public PlaybackData(String fileName,
                            PlaybackDelegate delegate,
                            long durationSeconds,
                            Call call)
        {
            playbackFileName = fileName;
            playbackDelegate = delegate;
            playbackDurationSeconds = durationSeconds;
            playbackCall = call;
        }

        /**
         * Returns the relative path of the file to be played.
         *
         * @return File to play.
         */
        String getPlaybackFileName() { return playbackFileName; }

        /**
         * Used to run specific code.
         *
         * @return <tt>PlaybackDelegate</tt>
         */
        PlaybackDelegate getPlaybackDelegate() { return playbackDelegate; }

        /**
         * Returns the playback duration of the file.
         *
         * @return Length in seconds of the file.
         */
        long getPlaybackDurationSeconds() { return playbackDurationSeconds; }

        /**
         * Returns the caller.
         *
         * @return Call to send audio to.
         */
        Call getPlaybackCall() { return playbackCall; }
    }

    /**
     * Queue used to schedule sound notifications.
     */
    private final BlockingQueue<PlaybackData> playbackQueue = new ArrayBlockingQueue<>(20, true);

    /**
     * Flag used to stop the queue thread.
     */
    private AtomicBoolean playbackQueueStopFlag = new AtomicBoolean(false);

    /**
     * Constructor for <tt>PlaybackQueue</tt>
     */
    public PlaybackQueue() { }

    /**
     * Queues a file to be played to the caller.
     *
     * @param call The call used to send audio.
     * @param fileName The file to be queued for playback.
     * @throws InterruptedException
     */
    public void queueNext(Call call, String fileName) throws InterruptedException
    {
        playbackQueue.put(new PlaybackData(fileName, null, 0, call));
    }

    /**
     * Queues a file to be played to the caller.
     *
     * @param call The call used to send audio.
     * @param fileName The file to be queued for playback.
     * @param delegate Used to delegate code when needed.
     * @param lengthInSeconds Playback duration in seconds for the file.
     * @throws InterruptedException
     */
    public void queueNext(Call call,
                          String fileName,
                          PlaybackDelegate delegate,
                          long lengthInSeconds) throws InterruptedException
    {
        playbackQueue.put(new PlaybackData(fileName, delegate, lengthInSeconds, call));
    }

    /**
     * Stops the playback queue after the current playback ends.
     */
    public void stopAtNextPlayback()
    {
        playbackQueueStopFlag.set(true);
    }

    /**
     * Thread execution method that injects queued files into audio stream.
     */
    @Override
    public void run()
    {
        while(!playbackQueueStopFlag.get())
        {
            Call playbackCall = null;
            try
            {
                PlaybackData playbackData
                    = playbackQueue.poll(1000, TimeUnit.MILLISECONDS);

                if (playbackData != null)
                {
                    playbackCall = playbackData.getPlaybackCall();

                    if (playbackCall != null)
                    {
                        injectSoundFile(playbackCall, playbackData.getPlaybackFileName());
                    }

                    final PlaybackDelegate playbackDelegate = playbackData.getPlaybackDelegate();
                    if (playbackDelegate != null)
                    {
                        playbackDelegate.onPlaybackFinished();
                    }
                }
            }
            catch (Exception ex)
            {
                if (playbackCall != null)
                {
                    Object callContext = playbackCall.getData(CallContext.class);
                    logger.error(callContext + " " + ex.toString(), ex);
                }
                else
                {
                    logger.error(ex.toString());
                }
            }
        }
    }

    /**
     * Copied from above.
     * Injects sound file in a call's <tt>MediaStream</tt> using injectPacket
     * method and constructing RTP packets for it.
     * Supports opus only (when using translator mode calls from the jitsi-meet
     * side are using opus and are just translated to the sip side).
     *
     * The file will be played if possible, if there is call passed and that
     * call has call peers of type MediaAwareCallPeer with media handler that
     * has MediaStream for Audio.
     *
     * @param call the call (sip one) to inject the sound as rtp.
     * @param fileName the file name to play.
     */
    private void injectSoundFile(Call call, String fileName)
    {
        MediaStream stream = SoundNotificationManager.getMediaStream(call);

        // if there is no stream or the calling account is not using translator
        // or the current call is not using opus
        if (stream == null
            || !call.getProtocolProvider().getAccountID().getAccountPropertyBoolean(
            ProtocolProviderFactory.USE_TRANSLATOR_IN_CONFERENCE, false)
            || stream.getDynamicRTPPayloadType(Constants.OPUS) == -1
            || fileName == null)
        {
            logger.error(call.getData(CallContext.class) + " No playback!");
            return;
        }

        try
        {
            SoundNotificationManager.injectSoundFileInStream(stream, fileName);
        }
        catch (Throwable t)
        {
            logger.error(call.getData(CallContext.class) + " Error playing:" + fileName, t);
        }
    }
}
