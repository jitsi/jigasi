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
import org.jitsi.jigasi.*;
import org.jitsi.jigasi.transcription.*;
import org.jitsi.service.configuration.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.service.neomedia.device.*;
import org.jitsi.util.*;
import org.jitsi.util.concurrent.*;

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
     * Creates this periodic checker.
     *
     * @param o the sip protocol provider service.
     * @param period the period between checks.
     */
    private SipHealthPeriodicChecker(SipGateway o, long period)
    {
        super(o, period, true);
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

        if (StringUtils.isNullOrEmpty(healthCheckSipUri))
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
        long start = System.currentTimeMillis();
        Exception exception = null;

        try
        {
            doCheck(this.o.getSipProvider());
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
        }
    }

    /**
     * Performs a health check on a specific {@link ProtocolProviderService},
     * by creating a call and waiting for the first media to arrive.
     *
     * @param pps the {@code ProtocolProviderService} to check
     * the health (status) of
     * @throws Exception if an error occurs while checking the health (status)
     * or the check determines that is not healthy
     */
    private static void doCheck(ProtocolProviderService pps)
        throws Exception
    {
        // countdown will wait for received audio buffer or call being ended
        CountDownLatch countDownLatch = new CountDownLatch(1);

        // will use this to store whether any media had been received
        Boolean[] receivedBuffer = new Boolean[1];

        OperationSetBasicTelephony tele
            = pps.getOperationSet(OperationSetBasicTelephony.class);

        // set a dummy mixer to detect incoming audio packets
        Call call = tele.createCall(healthCheckSipUri,
            new MediaAwareCallConference()
            {
                TranscribingAudioMixerMediaDevice mixer
                    = new TranscribingAudioMixerMediaDevice(
                       (receiveStream, buffer) ->
                            {
                                receivedBuffer[0] = true;
                                countDownLatch.countDown();
                            });

                @Override
                public MediaDevice getDefaultDevice(MediaType mediaType,
                    MediaUseCase useCase)
                {
                    if(MediaType.AUDIO.equals(mediaType))
                    {
                        return mixer;
                    }
                    return super.getDefaultDevice(mediaType, useCase);
                }
            });

        call.addCallChangeListener(new CallChangeAdapter()
        {
            @Override
            public void callStateChanged(CallChangeEvent callChangeEvent)
            {
                if (callChangeEvent.getNewValue().equals(CallState.CALL_ENDED))
                {
                    countDownLatch.countDown();
                }
            }
        });

        countDownLatch.await(CALL_ESTABLISH_TIMEOUT, TimeUnit.SECONDS);

        if (receivedBuffer[0] != true)
        {
            throw new Exception("Health check call failed with no media!");
        }
        else
        {
            // the call had succeeded  as we had received media, we do not care
            // for any failures on hangup
            try
            {
                Iterator<? extends CallPeer> peerIter = call.getCallPeers();

                while (peerIter.hasNext())
                {
                    tele.hangupCallPeer(peerIter.next());
                }
            }
            catch (Throwable t){}
        }
    }
}
