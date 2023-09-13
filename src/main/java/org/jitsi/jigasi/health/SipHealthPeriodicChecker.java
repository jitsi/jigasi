/*
 * Jigasi, the JItsi GAteway to SIP.
 *
 * Copyright @ 2018 Atlassian Pty Ltd
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
package org.jitsi.jigasi.health;

import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.protocol.event.*;
import net.java.sip.communicator.service.protocol.media.*;
import org.apache.commons.lang3.StringUtils;
import org.jitsi.impl.neomedia.device.*;
import org.jitsi.jigasi.*;
import org.jitsi.jigasi.sounds.*;
import org.jitsi.jigasi.transcription.*;
import org.jitsi.service.configuration.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.service.neomedia.device.*;
import org.jitsi.utils.logging.*;
import org.jitsi.utils.*;
import org.jitsi.utils.concurrent.*;

import java.lang.management.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Health checks sip provider, on every check we check whether sip provider is
 * registered and we return the cached state while reoccurring creating
 * a health check call to a predefined number.
 *
 * @author Damian Minkov
 */
class SipHealthPeriodicChecker
    extends PeriodicRunnableWithObject<SipGateway>
{
    /**
     * The {@link Logger} used to print debug information.
     */
    private static final Logger logger
        = Logger.getLogger(SipHealthPeriodicChecker.class);

    /**
     * The property for custom interval between health checks.
     */
    private static final String PROP_HEALTH_CHECK_INTERVAL
        = "org.jitsi.jigasi.HEALTH_CHECK_INTERVAL";

    /**
     * The property for custom health check timeout.
     */
    private static final String PROP_HEALTH_CHECK_TIMEOUT
        = "org.jitsi.jigasi.HEALTH_CHECK_TIMEOUT";

    /**
     * The property to specify the sip uri to be called when health checking.
     */
    private static final String PROP_HEALTH_CHECK_SIP_URI
        = "org.jitsi.jigasi.HEALTH_CHECK_SIP_URI";

    /**
     * The default interval between health checks. 5 minutes.
     */
    private static final long DEFAULT_HEALTH_CHECK_INTERVAL = 5*60*1000;

    /**
     * The API will return failure unless a there was a health check performed
     * in the last that many milliseconds. 10 minutes.
     */
    private static final long DEFAULT_HEALTH_CHECK_TIMEOUT = 10*60*1000;

    /**
     * The seconds to wait for receiving media, when creating healthcheck call.
     * If no media is received for that time we consider the call as failed.
     */
    private static final long CALL_ESTABLISH_TIMEOUT = 10;

    /**
     * The seconds to wait before retrying a check after a failure.
     */
    private static final long CHECK_RETRY_INTERVAL = 60;

    /**
     * A property to enable health check debug, printing thread dump in the logs
     * for those failed health checks.
     */
    private static final String HEALTH_CHECK_DEBUG_PROP_NAME =
        "org.jitsi.jigasi.HEALTH_CHECK_DEBUG_ENABLED";

    /**
     * Whether health check debug is enabled. Off by default.
     */
    private boolean healthChecksDebugEnabled = false;

    /**
     * The health check interval ({@link #DEFAULT_HEALTH_CHECK_TIMEOUT}).
     */
    private static long timeout;

    /**
     * The sip uri to be called when health checking.
     */
    private static String healthCheckSipUri;

    /**
     * The exception resulting from the last health check performed. When the
     * health check is successful, this is {@code null}.
     */
    private Exception lastResult = null;

    /**
     * The time the last health check finished being performed. A value of
     * {@code -1} indicates that no health check has been performed yet.
     */
    private long lastResultMs = -1;

    /**
     * The executor that will push sound files in the stream.
     */
    private final static ExecutorService injectSoundExecutor
        = Executors.newSingleThreadExecutor(new CustomizableThreadFactory("SipHealthPeriodicChecker", false));


    /**
     * Creates this periodic checker.
     *
     * @param o the sip protocol provider service.
     * @param period the period between checks.
     */
    private SipHealthPeriodicChecker(SipGateway o, long period)
    {
        super(o, period, true);

        healthChecksDebugEnabled
            = JigasiBundleActivator.getConfigurationService()
                .getBoolean(HEALTH_CHECK_DEBUG_PROP_NAME, false);
    }

    /**
     * Creates SipHealthPeriodicChecker.
     *
     * @param gw the sip gateway which provider we will use.
     * @return new SipHealthPeriodicChecker
     */
    static SipHealthPeriodicChecker create(SipGateway gw)
    {
        ConfigurationService conf
            = JigasiBundleActivator.getConfigurationService();
        long healthCheckInterval = conf.getLong(
            PROP_HEALTH_CHECK_INTERVAL, DEFAULT_HEALTH_CHECK_INTERVAL);
        timeout = conf.getLong(
            PROP_HEALTH_CHECK_TIMEOUT, DEFAULT_HEALTH_CHECK_TIMEOUT);

        healthCheckSipUri = conf.getString(PROP_HEALTH_CHECK_SIP_URI);

        if (StringUtils.isEmpty(StringUtils.trim(healthCheckSipUri)))
        {
            logger.warn(
                "No health check started, no HEALTH_CHECK_SIP_URI prop.");
            return null;
        }

        return new SipHealthPeriodicChecker(gw, healthCheckInterval);
    }

    /**
     * Checks the health (status). This method only returns the cache results,
     * it does not do the actual health check
     * (i.e. creating a test conference/call).
     *
     * @throws Exception if an error occurs while checking the health (status)
     *                   or the check determines that is not healthy.
     */
    public void check()
        throws Exception
    {
        if (this.o.getSipProvider().getRegistrationState()
                != RegistrationState.REGISTERED)
        {
            throw new Exception("SIP provider not registered.");
        }

        Exception lastResult = this.lastResult;
        long lastResultMs = this.lastResultMs;
        long timeSinceLastResult = System.currentTimeMillis() - lastResultMs;

        if (timeSinceLastResult > timeout)
        {
            throw new Exception(
                "No health checks performed recently, the last result was "
                    + timeSinceLastResult + "ms ago.");
        }

        if (lastResult != null)
        {
            throw new Exception(lastResult);
        }

        // We've had a recent result, and it is successful (no exception).
    }

    @Override
    protected void doRun()
    {
        this.doRunInternal(true);
    }

    /**
     * The doRun implementation.
     * @param retryOnFailure <tt>true</tt> if we want to do a short retry after
     * failure, the retry will happen after <tt>CHECK_RETRY_INTERVAL</tt>
     * seconds.
     */
    private void doRunInternal(boolean retryOnFailure)
    {
        long start = System.currentTimeMillis();
        Exception exception = null;

        try
        {
            doCheck(this.o.getSipProvider(), healthChecksDebugEnabled);
        }
        catch (Exception e)
        {
            exception = e;
        }

        long duration = System.currentTimeMillis() - start;
        lastResult = exception;
        lastResultMs = start + duration;

        if (exception == null)
        {
            logger.info(
                "Performed a successful health check in " + duration
                    + "ms. ");
        }
        else
        {
            logger.error(
                "Health check failed in " + duration + "ms:", exception);

            if (retryOnFailure)
            {
                new Timer().schedule(new TimerTask()
                {
                    @Override
                    public void run()
                    {
                        doRunInternal(false);
                    }
                }, CHECK_RETRY_INTERVAL*1000);
            }
        }
    }

    /**
     * Performs a health check on a specific {@link ProtocolProviderService},
     * by creating a call and waiting for the first media to arrive.
     *
     * @param pps the {@code ProtocolProviderService} to check
     * the health (status) of
     * @param debugEnabled whether to do a thread dump on failure
     * @throws Exception if an error occurs while checking the health (status)
     * or the check determines that is not healthy
     */
    private static void doCheck(
        ProtocolProviderService pps, boolean debugEnabled)
        throws Exception
    {
        // countdown will wait for received audio buffer or call being ended
        CountDownLatch hangupLatch = new CountDownLatch(1);

        // will use this to store whether any media had been received
        Boolean[] receivedBuffer = new Boolean[1];

        OperationSetBasicTelephony tele
            = pps.getOperationSet(OperationSetBasicTelephony.class);

        // once peer is connected we will start sending audio to avoid relaying on just a few hole punch packets
        CallPeerListener callPeerListener = new CallPeerAdapter()
        {
            @Override
            public void peerStateChanged(CallPeerChangeEvent evt)
            {
                super.peerStateChanged(evt);

                CallPeer peer = evt.getSourceCallPeer();

                CallPeerState peerState = peer.getState();

                if (CallPeerState.CONNECTED.equals(peerState))
                {
                    injectSoundExecutor.execute(() -> {
                        try
                        {
                            // make sure we push audio, no longer than the time limit we will check for media
                            long startNano = System.nanoTime();
                            long maxDuration = TimeUnit.NANOSECONDS.convert(CALL_ESTABLISH_TIMEOUT, TimeUnit.SECONDS);
                            while (hangupLatch.getCount() > 0 && (System.nanoTime() - startNano) < maxDuration)
                            {
                                SoundNotificationManager.injectSoundFile(
                                    peer.getCall(), SoundNotificationManager.PARTICIPANT_ALONE);

                                Thread.sleep(1000);
                            }
                        }
                        catch(InterruptedException ex)
                        {}
                    });
                }
            }
        };

        // set a dummy mixer to detect incoming audio packets
        Call call = tele.createCall(healthCheckSipUri,
            new MediaAwareCallConference()
            {
                TranscribingAudioMixerMediaDevice mixer
                    = new TranscribingAudioMixerMediaDevice(
                        new AudioSilenceMediaDevice(),
                       (receiveStream, buffer) ->
                            {
                                receivedBuffer[0] = true;
                                hangupLatch.countDown();
                            });

                @Override
                public MediaDevice getDefaultDevice(MediaType mediaType,
                    MediaUseCase useCase)
                {
                    if (MediaType.AUDIO.equals(mediaType))
                    {
                        return mixer;
                    }
                    return super.getDefaultDevice(mediaType, useCase);
                }
            });
        CallPeer sipPeer = call.getCallPeers().next();
        sipPeer.addCallPeerListener(callPeerListener);

        call.addCallChangeListener(new CallChangeAdapter()
        {
            @Override
            public void callStateChanged(CallChangeEvent callChangeEvent)
            {
                if (callChangeEvent.getNewValue().equals(CallState.CALL_ENDED))
                {
                    hangupLatch.countDown();
                }
            }
        });

        hangupLatch.await(CALL_ESTABLISH_TIMEOUT, TimeUnit.SECONDS);
        sipPeer.removeCallPeerListener(callPeerListener);

        // we do not care for any failures on hangup
        Iterator<? extends CallPeer> peerIter = call.getCallPeers();
        while (peerIter.hasNext())
        {
            try
            {
                tele.hangupCallPeer(peerIter.next());
            }
            catch (Throwable t){}
        }

        if (receivedBuffer[0] == null ||  receivedBuffer[0] != true)
        {
            logger.error("Outgoing health check failed. " + (debugEnabled ? getThreadDumb() : ""));

            String sessionInfo = (String)call.getData("X-session-info");

            throw new Exception("Health check call failed with no media! "
                + (sessionInfo != null ? "Session info:" + sessionInfo: ""));
        }
    }

    /**
     * Retrieves a thread dump.
     * @return string representing current state of threads.
     */
    private static String getThreadDumb()
    {
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        ThreadInfo[] threadInfos = threadMXBean.getThreadInfo(
            threadMXBean.getAllThreadIds(), 100);
        StringBuilder dbg = new StringBuilder();
        for (ThreadInfo threadInfo : threadInfos)
        {
            if (threadInfo == null)
            {
                continue;
            }
            dbg.append('"').append(threadInfo.getThreadName()).append('"');

            Thread.State state = threadInfo.getThreadState();
            dbg.append("\n   java.lang.Thread.State: ").append(state);

            if (threadInfo.getLockName() != null)
            {
                dbg.append(" on ").append(threadInfo.getLockName());
            }
            dbg.append('\n');

            StackTraceElement[] stackTraceElements
                = threadInfo.getStackTrace();
            for (int i = 0; i < stackTraceElements.length; i++)
            {
                StackTraceElement ste = stackTraceElements[i];
                dbg.append("\tat " + ste.toString());
                dbg.append('\n');
                if (i == 0 && threadInfo.getLockInfo() != null)
                {
                    Thread.State ts = threadInfo.getThreadState();
                    if (ts == Thread.State.BLOCKED
                        || ts == Thread.State.WAITING
                        || ts == Thread.State.TIMED_WAITING)
                    {
                        dbg.append("\t-  " + ts + " on "
                            + threadInfo.getLockInfo());
                        dbg.append('\n');
                    }
                }

                for (MonitorInfo mi
                    : threadInfo.getLockedMonitors())
                {
                    if (mi.getLockedStackDepth() == i)
                    {
                        dbg.append("\t-  locked " + mi);
                        dbg.append('\n');
                    }
                }
            }
            dbg.append("\n\n");
        }

        return dbg.toString();
    }
}
