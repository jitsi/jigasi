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
import net.java.sip.communicator.service.protocol.media.*;
import org.gagravarr.ogg.*;
import org.gagravarr.opus.*;
import org.jitsi.impl.neomedia.*;
import org.jitsi.impl.neomedia.codec.*;
import org.jitsi.jigasi.*;
import org.jitsi.jigasi.util.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.service.neomedia.codec.*;
import org.jitsi.utils.*;
import org.jitsi.utils.logging.Logger;
import org.jitsi.xmpp.extensions.jibri.*;
import org.jivesoftware.smack.packet.*;

import java.util.*;
import java.util.concurrent.*;

/**
 * Manages all sounds notifications that are send to a SIP session side.
 *
 * @author Damian Minkov
 */
public class SoundNotificationManager
{
    /**
     * The logger.
     */
    private final static Logger logger = Logger.getLogger(
        SoundNotificationManager.class);

    /**
     * The sound file to use when recording is ON.
     */
    private static final String REC_ON_SOUND = "sounds/RecordingOn.opus";

    /**
     * The sound file to use when recording is OFF.
     */
    private static final String REC_OFF_SOUND = "sounds/RecordingStopped.opus";

    /**
     * The sound file to use when live streaming is ON.
     */
    private static final String LIVE_STREAMING_ON_SOUND
        = "sounds/LiveStreamingOn.opus";

    /**
     * The sound file to use when live streaming is OFF.
     */
    private static final String LIVE_STREAMING_OFF_SOUND
        = "sounds/LiveStreamingOff.opus";

    /**
     * The sound file to use when the max occupants limit is reached.
     */
    private static final String MAX_OCCUPANTS_SOUND
        = "sounds/MaxOccupants.opus";

    /**
     * The sound file to use to notify sip participant that
     * nobody is in the conference.
     */
    public static final String PARTICIPANT_ALONE = "sounds/Alone.opus";

    /**
     * The sound file to use to notify sip participant that a
     * participant has left.
     */
    public static final String PARTICIPANT_LEFT = "sounds/ParticipantLeft.opus";

    /**
     * The sound file to use to notify sip partitipant that a
     * new participant has joined.
     */
    public static final String PARTICIPANT_JOINED = "sounds/ParticipantJoined.opus";

    /**
     * The sound file to use to notify the participant that access was granted to join the
     * main room.
     */
    private static final String LOBBY_ACCESS_GRANTED = "sounds/LobbyAccessGranted.opus";

    /**
     * The sound file to use to notify the participant that access was denied to join the
     * main room.
     */
    private static final String LOBBY_ACCESS_DENIED = "sounds/LobbyAccessDenied.opus";

    /**
     * The sound file to use to notify the participant that the conference has ended.
     */
    private static final String LOBBY_MEETING_END = "sounds/LobbyMeetingEnd.opus";

    /**
     * The sound file to use to notify the participant that request to join is being reviewed.
     */
    private static final String LOBBY_JOIN_REVIEW = "sounds/LobbyWait.opus";

    /**
     * Approximate duration of the file to be played, we need it as to know
     * when to hangup the call. The actual file is 10 seconds but we give a
     * little longer for the file to be played and call to be answered.
     */
    private static final int MAX_OCCUPANTS_SOUND_DURATION_SEC = 15;

    /**
     * The current Jibri status in the conference.
     */
    private JibriIq.Status currentJibriStatus = JibriIq.Status.OFF;

    /**
     * The current jibri On sound to use, recording or live streaming.
     */
    private String currentJibriOnSound = null;

    /**
     * {@link SipGatewaySession} that uses this instance.
     */
    private final SipGatewaySession gatewaySession;

    /**
     * When set will indicate that we only need to play announcement to the
     * sip side and hangup the call.
     */
    private boolean callMaxOccupantsLimitReached = false;

    /**
     * In certain scenarios (max occupants) we wait till we hangup the call.
     */
    private CountDownLatch hangupWait = null;

    /**
     * Rate limiter of participant left sound notification.
     */
    private SoundRateLimiter participantLeftRateLimiterLazy = null;

    /**
     * Rate limiter of participant joined sound notification.
     */
    private SoundRateLimiter participantJoinedRateLimiterLazy = null;

    /**
     * Rate limiter of recording on sound notification.
     */
    private SoundRateLimiter recordingOnRateLimiterLazy = null;

    /**
     * Timeout of rate limiter for participant alone notification.
     */
    private static final long PARTICIPANT_ALONE_TIMEOUT_MS = 15000;

    /**
     * Timeout of rate limiter for participant left notification.
     */
    private static final long PARTICIPANT_LEFT_RATE_TIMEOUT_MS = 30000;

    /**
     * Timeout of rate limiter for recording on.
     */
    private static final long RECORDING_ON_RATE_TIMEOUT_MS = 10000;

    /**
     * Timeout of rate limiter for participant joined notification.
     */
    private static final long PARTICIPANT_JOINED_RATE_TIMEOUT_MS = 30000;

    /**
     * Timer trigger notification when participant is the only one
     * in the conference for a certain amount of time.
     */
    private Timer participantAloneNotificationTimerLazy = null;

    /**
     * Task to trigger notification when participant is the only one
     * in the conference from a certain amount of time.
     */
    private TimerTask participantAloneNotificationTask = null;

    /**
     * To sync schedule and cancel the participant alone notification.
     */
    private final Object participantAloneNotificationSync = new Object();

    /**
     * A queue of files to be played when call is connected.
     */
    private PlaybackQueue playbackQueue = new PlaybackQueue();

    /**
     * Constructs new <tt>SoundNotificationManager</tt>.
     *
     * @param gatewaySession The sip session using this instance.
     */
    public SoundNotificationManager(SipGatewaySession gatewaySession)
    {
        this.gatewaySession = gatewaySession;
    }

    /**
     * Returns the call context for the current session.

     * @return the call context for the current session.
     */
    private CallContext getCallContext()
    {
        return this.gatewaySession.getCallContext();
    }

    /**
     * Processes a member presence change.
     * @param presence the presence to process.
     */
    public void process(Presence presence)
    {
        RecordingStatus rs = presence.getExtension(RecordingStatus.class);

        if (rs != null
            && gatewaySession.getFocusResourceAddr().equals(
                presence.getFrom().getResourceOrEmpty().toString()))
        {
            notifyRecordingStatusChanged(rs.getRecordingMode(), rs.getStatus());
        }
    }

    /**
     * Method called notify that a recording status change was detected.
     *
     * @param mode The recording mode.
     * @param status The recording status.
     */
    private void notifyRecordingStatusChanged(
        JibriIq.RecordingMode mode, JibriIq.Status status)
    {
        // not a change, ignore
        if (currentJibriStatus.equals(status))
        {
            return;
        }
        currentJibriStatus = status;

        String offSound;
        if (mode.equals(JibriIq.RecordingMode.FILE))
        {
            currentJibriOnSound = REC_ON_SOUND;
            offSound = REC_OFF_SOUND;
        }
        else if (mode.equals(JibriIq.RecordingMode.STREAM))
        {
            currentJibriOnSound = LIVE_STREAMING_ON_SOUND;
            offSound = LIVE_STREAMING_OFF_SOUND;
        }
        else
        {
            return;
        }

        try
        {
            if (JibriIq.Status.ON.equals(status) && !getRecordingOnRateLimiter().on())
            {
                playbackQueue.queueNext(gatewaySession.getSipCall(), currentJibriOnSound);
            }
            else if (JibriIq.Status.OFF.equals(status))
            {
                playbackQueue.queueNext(gatewaySession.getSipCall(), offSound);
            }
        }
        catch(InterruptedException ex)
        {
            logger.error(getCallContext() + " Error playing sound notification");
        }
    }

    /**
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
    public static void injectSoundFile(Call call, String fileName)
    {
        MediaStream stream = getMediaStream(call);

        // if there is no stream or the calling account is not using translator
        // or the current call is not using opus
        if (stream == null
            || !call.getProtocolProvider().getAccountID().getAccountPropertyBoolean(
                    ProtocolProviderFactory.USE_TRANSLATOR_IN_CONFERENCE, false)
            || stream.getDynamicRTPPayloadType(Constants.OPUS) == -1
            || fileName == null)
        {
            return;
        }

        final MediaStream streamToPass = stream;

        try
        {
            injectSoundFileInStream(streamToPass, fileName);
        }
        catch (Throwable t)
        {
            logger.error(call.getData(CallContext.class) + " Error playing:" + fileName, t);
        }
    }

    /**
     * The internal implementation where we read the file and inject it in
     * the stream.
     * @param stream the stream where we inject the sound as rtp.
     * @param fileName the file name to play.
     * @throws Throwable cannot read source sound file or cannot transmit it.
     */
    static void injectSoundFileInStream(MediaStream stream, String fileName)
        throws Throwable
    {
        OpusFile of = new OpusFile(new OggPacketReader(
            Util.class.getClassLoader().getResourceAsStream(fileName)));

        OpusAudioData opusAudioData;
        // Random timestamp, ssrc and seq
        int seq = new Random().nextInt(0xFFFF);
        long ts = new Random().nextInt(0xFFFF);
        long ssrc = new Random().nextInt(0xFFFF);
        byte pt = stream.getDynamicRTPPayloadType(Constants.OPUS);
        long timeForNextPacket = System.currentTimeMillis();
        long sentDuration = 0;

        while ((opusAudioData = of.getNextAudioPacket()) != null)
        {
            // seq may rollover
            if (seq > AbstractCodec2.SEQUENCE_MAX)
            {
                seq = 0;
            }

            int nSamples = opusAudioData.getNumberOfSamples();
            ts += nSamples;
            // timestamp may rollover
            if (ts > TimestampUtils.MAX_TIMESTAMP_VALUE)
            {
                ts = ts - TimestampUtils.MAX_TIMESTAMP_VALUE;
            }

            byte[] data = opusAudioData.getData();
            RawPacket rtp = Util.makeRTP(
                ssrc, // ssrc
                pt, // payload
                seq++, /// seq
                ts, // ts
                data.length + RawPacket.FIXED_HEADER_SIZE// len
            );
            rtp.setSkipStats(true);

            System.arraycopy(
                data, 0, rtp.getBuffer(), rtp.getPayloadOffset(), data.length);
            int duration = nSamples/48;
            timeForNextPacket += duration;
            sentDuration += duration;
            if (stream instanceof MediaStreamImpl)
            {
                ((MediaStreamImpl)stream).injectPacket(rtp, true, null, true);
            }
            else
            {
                stream.injectPacket(rtp, true, null);
            }

            long sleep = timeForNextPacket - System.currentTimeMillis();
            if (sleep > 0 && sentDuration > 200) // we let the first 200ms to be sent without waiting
            {
                Thread.sleep(sleep);
            }
        }
    }

    /**
     * Process call peer state change, if we are going to play a notification
     * we want to return the time in seconds to wait before hanging up the sip
     * side, this is in order to be able to signal a message to the caller.
     *
     * @param callPeerState The call peer state to process.
     */
    public void process(CallPeerState callPeerState)
    {
        long delayedHangupSeconds = -1;

        if (CallPeerState.BUSY.equals(callPeerState))
        {
            // Hangup the call with 5 sec delay, so that we can see BUSY
            // status in jitsi-meet
            delayedHangupSeconds = 5 * 1000;
        }

        // when someone connects and recording is on, play notification
        if (CallPeerState.CONNECTED.equals(callPeerState))
        {
            try
            {
                if (currentJibriStatus.equals(JibriIq.Status.ON) && !getRecordingOnRateLimiter().on())
                {
                    playbackQueue.queueNext(gatewaySession.getSipCall(), currentJibriOnSound);
                }

                if (callMaxOccupantsLimitReached)
                {
                    playbackQueue.queueNext(gatewaySession.getSipCall(), MAX_OCCUPANTS_SOUND);

                    delayedHangupSeconds = MAX_OCCUPANTS_SOUND_DURATION_SEC * 1000;
                }
                else
                {
                    playbackQueue.start();

                    playParticipantJoinedNotification();
                }
            }
            catch(InterruptedException ex)
            {
                logger.error(getCallContext() + " Error playing sound notification");
            }
        }
        else if (CallPeerState.DISCONNECTED.equals(callPeerState))
        {
            playbackQueue.stopAtNextPlayback();
        }

        if (delayedHangupSeconds != -1)
        {
            final long mills = delayedHangupSeconds;
            new Thread(() -> {
                try
                {
                    Thread.sleep(mills);
                }
                catch(InterruptedException e)
                {
                    throw new RuntimeException(e);
                }
                CallManager.hangupCall(gatewaySession.getSipCall());

                if (hangupWait != null)
                    hangupWait.countDown();
            }).start();
        }
    }

    /**
     * Stops playback.
     */
    public void stop()
    {
        this.playbackQueue.stopAtNextPlayback();
    }

    /**
     * We need to play a sound notification that the limit is reached, so we
     * need to answer the call once connected, play the sound and then wait for
     * the hangup before returning.
     */
    public void indicateMaxOccupantsLimitReached()
    {
        callMaxOccupantsLimitReached = true;

        // will wait for answering and then the hangup before returning
        hangupWait = new CountDownLatch(1);

        // answer play and hangup
        try
        {
            CallManager.acceptCall(gatewaySession.getSipCall());
        }
        catch(OperationFailedException e)
        {
            logger.error(getCallContext() + " Cannot answer call to play max occupants sound", e);
            return;
        }

        try
        {
            hangupWait.await(
                MAX_OCCUPANTS_SOUND_DURATION_SEC, TimeUnit.SECONDS);
        }
        catch(InterruptedException e)
        {
            logger.warn(getCallContext() + " Didn't finish waiting for hangup on max occupants");
        }
    }

    /**
     * Schedules a sound notification playback to notify
     * the participant is the only one in the conference.
     */
    private void scheduleAloneNotification(long timeout)
    {
        synchronized(participantAloneNotificationSync)
        {
            this.cancelAloneNotification();

            this.participantAloneNotificationTask
                = new TimerTask()
            {
                @Override
                public void run()
                {
                    try
                    {
                        playParticipantAloneNotification();
                    }
                    catch(Exception ex)
                    {
                        logger.error(getCallContext() + ex.getMessage(), ex);
                    }
                }
            };

            getParticipantAloneNotificationTimer().schedule(this.participantAloneNotificationTask, timeout);
        }
    }

    /**
     * Cancels the participant alone notification.
     */
    private void cancelAloneNotification()
    {
        synchronized(participantAloneNotificationSync)
        {
            if (this.participantAloneNotificationTask != null)
            {
                this.participantAloneNotificationTask.cancel();
            }
        }
    }

    /**
     * Called when no other participant is left in the conference.
     */
    public void onJvbCallEnded()
    {
        scheduleAloneNotification(0);

        if (this.participantJoinedRateLimiterLazy != null)
        {
            this.participantJoinedRateLimiterLazy.reset();
        }

        if (this.participantLeftRateLimiterLazy != null)
        {
            participantLeftRateLimiterLazy.reset();
        }

        if (this.recordingOnRateLimiterLazy != null)
        {
            this.recordingOnRateLimiterLazy.reset();
        }
    }

    /**
     * Called when a new participant has joined the conference.
     *
     * @param member the member who joined the JVB conference.
     */
    public void notifyChatRoomMemberJoined(ChatRoomMember member)
    {
        boolean sendNotification = false;

        Call sipCall = gatewaySession.getSipCall();
        Call jvbCall = gatewaySession.getJvbCall();

        if (sipCall != null)
        {
            sendNotification
                = (sendNotification ||
                    sipCall.getCallState() == CallState.CALL_IN_PROGRESS);
        }

        if (jvbCall != null)
        {
            sendNotification
                = (sendNotification ||
                    jvbCall.getCallState() == CallState.CALL_IN_PROGRESS);
        }

        if (sendNotification)
        {
            playParticipantJoinedNotification();
        }

        this.cancelAloneNotification();
    }

    /**
     * Called when a new participant has left the conference.
     *
     * @param member the member who joined the JVB conference.
     */
    public void notifyChatRoomMemberLeft(ChatRoomMember member)
    {
        JvbConference jvbConference = gatewaySession.getJvbConference();

        // if this is the sip hanging up (stopping) skip playing
        if (jvbConference != null && jvbConference.isStarted() && gatewaySession.getSipCall() != null)
        {
            playParticipantLeftNotification();
        }
    }

    /**
     * Called when JVB conference was joined.
     */
    public void notifyJvbRoomJoined()
    {
        int participantCount = gatewaySession.getParticipantsCount();

        if (participantCount <= 2)
        {
            scheduleAloneNotification(PARTICIPANT_ALONE_TIMEOUT_MS);
        }
    }

    /**
     * Tries to play a sound file if connected if not it will be queued.
     *
     * @param fileName The sound file to be played.
     */
    private void playSoundFileIfPossible(String fileName)
    {
        try
        {
            if (gatewaySession.getSipCall() != null)
            {
                if (gatewaySession.getSipCall().getCallState()
                        != CallState.CALL_IN_PROGRESS)
                {
                    // Queue playback of file
                    CallManager.acceptCall(gatewaySession.getSipCall());
                }

                // Hangup in these two cases
                if (fileName.equals(LOBBY_ACCESS_DENIED) || fileName.equals(LOBBY_MEETING_END))
                {
                        playbackQueue.queueNext(
                            gatewaySession.getSipCall(),
                            fileName,
                            () -> {
                                // Hangup
                                CallManager.hangupCall(gatewaySession.getSipCall());
                            });
                }
                else if (fileName.equals(LOBBY_JOIN_REVIEW))
                {
                    playbackQueue.queueNext(
                            gatewaySession.getSipCall(),
                            fileName,
                            () -> gatewaySession.notifyLobbyJoined());
                }
                else
                {
                    playbackQueue.queueNext(gatewaySession.getSipCall(), fileName);
                }
            }
        }
        catch (Exception ex)
        {
            logger.error(getCallContext() + " " + ex.toString(), ex);
        }
    }

    /**
     * Called when the user waits for approval to join.
     */
    public void notifyLobbyWaitReview()
    {
        playSoundFileIfPossible(LOBBY_JOIN_REVIEW);
    }

    /**
     * Called when the user was granted access to JVB conference.
     */
    public void notifyLobbyAccessGranted()
    {
        playSoundFileIfPossible(LOBBY_ACCESS_GRANTED);
    }

    /**
     * Called when the user was denied access to JVB conference.
     */
    public void notifyLobbyAccessDenied()
    {
        playSoundFileIfPossible(LOBBY_ACCESS_DENIED);
    }

    /**
     * Used to notify the user that the main room was destroyed - meetings has ended.
     */
    public void notifyLobbyRoomDestroyed()
    {
        playSoundFileIfPossible(LOBBY_MEETING_END);
    }

    /**
     * Sends sound notification that tells the user
     * is alone in the conference.
     */
    private void playParticipantAloneNotification()
    {
        try
        {
            Call sipCall = gatewaySession.getSipCall();

            if (sipCall != null)
            {
                playbackQueue.queueNext(sipCall, PARTICIPANT_ALONE);

                if (sipCall.getCallState() != CallState.CALL_IN_PROGRESS)
                {
                    CallManager.acceptCall(sipCall);
                }
            }
        }
        catch(Exception ex)
        {
            logger.error(getCallContext() + " " + ex.getMessage(), ex);
        }
    }

    /**
     * Sends sound notification for sip participants if
     * rate limiter allows.
     */
    private void playParticipantLeftNotification()
    {
        try
        {
            if (!getParticipantLeftRateLimiter().on())
            {
                Call sipCall = gatewaySession.getSipCall();

                if (sipCall != null)
                {
                    playbackQueue.queueNext(sipCall, PARTICIPANT_LEFT);
                }
            }
        }
        catch(Exception ex)
        {
            logger.error(getCallContext() + " " + ex.getMessage(), ex);
        }
    }

    /**
     * Sends sound notification for sip participant if
     * rate limiter allows.
     */
    private void playParticipantJoinedNotification()
    {
        try
        {
            this.cancelAloneNotification();

            if (!getParticipantJoinedRateLimiter().on())
            {
                Call sipCall = gatewaySession.getSipCall();

                if (sipCall != null)
                {
                    playbackQueue.queueNext(gatewaySession.getSipCall(), PARTICIPANT_JOINED);
                }
            }
        }
        catch(Exception ex)
        {
            logger.error(getCallContext() + " " + ex.getMessage());
        }
    }

    /**
     * Returns new Timer that will trigger playback for
     * PARTICIPANT_ALONE notification.
     *
     * @return participantAloneNotificationTimerLazy
     */
    private Timer getParticipantAloneNotificationTimer()
    {
        if (this.participantAloneNotificationTimerLazy == null)
        {
            this.participantAloneNotificationTimerLazy
                = new Timer();
        }

        return participantAloneNotificationTimerLazy;
    }

    /**
     * Returns new SoundRateLimiter to be used for participant left
     * if not created already.
     *
     * @return participantLeftRateLimiterLazy
     */
    private SoundRateLimiter getParticipantLeftRateLimiter()
    {
        if (this.participantLeftRateLimiterLazy == null)
        {
            this.participantLeftRateLimiterLazy
                = new SoundRateLimiter(PARTICIPANT_LEFT_RATE_TIMEOUT_MS);
        }

        return this.participantLeftRateLimiterLazy;
    }

    /**
     * Returns new SoundRateLimiter to be used for participant joined
     * if not created already.
     *
     * @return participantJoinedRateLimiterLazy
     */
    private SoundRateLimiter getParticipantJoinedRateLimiter()
    {
        if (this.participantJoinedRateLimiterLazy == null)
        {
            this.participantJoinedRateLimiterLazy
                = new SoundRateLimiter(PARTICIPANT_JOINED_RATE_TIMEOUT_MS);
        }

        return this.participantJoinedRateLimiterLazy;
    }

    /**
     * Returns new SoundRateLimiter to be used for recording on if not created already.
     *
     * @return recordingOnRateLimiterLazy
     */
    private SoundRateLimiter getRecordingOnRateLimiter()
    {
        if (this.recordingOnRateLimiterLazy == null)
        {
            this.recordingOnRateLimiterLazy = new SoundRateLimiter(RECORDING_ON_RATE_TIMEOUT_MS);
        }

        return this.recordingOnRateLimiterLazy;
    }

    /**
     * Extracts MediaStream from call.
     * @param call the call.
     * @return null or <tt>MediaStream</tt> if available.
     */
    static MediaStream getMediaStream(Call call)
    {
        CallPeer peer;
        if (call != null
            && call.getCallPeers() != null
            && call.getCallPeers().hasNext()
            && (peer = call.getCallPeers().next()) != null
            && peer instanceof MediaAwareCallPeer)
        {
            MediaAwareCallPeer peerMedia = (MediaAwareCallPeer) peer;

            CallPeerMediaHandler mediaHandler
                = peerMedia.getMediaHandler();
            if (mediaHandler != null)
            {
                return mediaHandler.getStream(MediaType.AUDIO);
            }
        }

        return null;
    }
}
