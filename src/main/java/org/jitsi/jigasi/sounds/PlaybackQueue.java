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

import java.util.*;
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
         * The call to send audio to.
         */
        private Call playbackCall;

        /**
         * Constructor of <tt>PlaybackData</tt>
         *
         * @param fileName File to play.
         * @param delegate Delegate code.
         * @param call Call to send audio to.
         */
        public PlaybackData(String fileName,
                            PlaybackDelegate delegate,
                            Call call)
        {
            playbackFileName = fileName;
            playbackDelegate = delegate;
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
         * Returns the caller.
         *
         * @return Call to send audio to.
         */
        Call getPlaybackCall() { return playbackCall; }

        /**
         * Checks for equals by just checking the filename as this is what is to be played.
         * Can be passed a String (the filename) and we will also check and that.
         * @param o the object to check can be PlaybackData or string.
         * @return true if equals.
         */
        @Override
        public boolean equals(Object o)
        {
            if (this == o)
            {
                return true;
            }

            if (o != null && o instanceof String)
            {
                return playbackFileName.equals(o);
            }

            if (o == null || getClass() != o.getClass())
            {
                return false;
            }

            PlaybackData that = (PlaybackData) o;
            return playbackFileName.equals(that.playbackFileName);
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(playbackFileName, playbackDelegate, playbackCall);
        }
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
     * Queues a file to be played to the caller.
     *
     * @param call The call used to send audio.
     * @param fileName The file to be queued for playback.
     * @throws InterruptedException
     */
    public void queueNext(Call call, String fileName) throws InterruptedException
    {
        this.queueNext(call, fileName, null);
    }

    /**
     * Queues a file to be played to the caller.
     *
     * @param call The call used to send audio.
     * @param fileName The file to be queued for playback.
     * @param delegate Used to delegate code when needed.
     * @throws InterruptedException
     */
    public void queueNext(Call call,
                          String fileName,
                          PlaybackDelegate delegate)
        throws InterruptedException
    {
        // if the thread for playing is not started (call is not connected)
        // there is no point to add the same notification twice, so skip it
        if (!this.isAlive() && playbackQueue.contains(fileName))
        {
            // let's skip it
            return;
        }

        playbackQueue.put(new PlaybackData(fileName, delegate, call));
    }

    /**
     * Stops the playback queue after the current playback ends.
     */
    public void stopAtNextPlayback()
    {
        playbackQueue.clear();
        playbackQueueStopFlag.set(true);
        this.interrupt();
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
                PlaybackData playbackData = playbackQueue.take();

                if (playbackData != null)
                {
                    playbackCall = playbackData.getPlaybackCall();

                    if (playbackCall != null)
                    {
                        SoundNotificationManager.injectSoundFile(playbackCall, playbackData.getPlaybackFileName());
                    }

                    final PlaybackDelegate playbackDelegate = playbackData.getPlaybackDelegate();
                    if (playbackDelegate != null)
                    {
                        playbackDelegate.onPlaybackFinished();
                    }
                }
            }
            catch(InterruptedException ex)
            {
                // the execution was interrupted
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
}
