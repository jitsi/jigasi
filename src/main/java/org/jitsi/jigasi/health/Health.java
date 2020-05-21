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

import org.jitsi.jigasi.*;
import org.jitsi.utils.concurrent.*;

/**
 * Checks the health of Jigasi.
 *
 * @author Damian Minkov
 */
public class Health
{
    /**
     * The executor used to perform periodic health checks.
     */
    private static final RecurringRunnableExecutor executor
        = new RecurringRunnableExecutor(Health.class.getName());

    /**
     * The current sip checker.
     */
    private static SipHealthPeriodicChecker sipChecker = null;

    /**
     * Whether there is a <tt>SipGateway</tt> returned from the list of
     * available gateways.
     */
    private static boolean hasSipGw = false;

    /**
     * Starts a runnable which checks the health of jigasi
     * periodically (at an interval).
     */
    public static void start()
    {
        SipGateway sipGateway = null;
        for (AbstractGateway gw : JigasiBundleActivator.getAvailableGateways())
        {
            if (gw instanceof SipGateway)
                sipGateway = (SipGateway) gw;
        }

        if (sipGateway != null)
        {
            hasSipGw = true;

            if (sipGateway.isReady())
            {
                sipChecker = SipHealthPeriodicChecker.create(sipGateway);
                if (sipChecker != null)
                {
                    executor.registerRecurringRunnable(sipChecker);
                }
            }
            else
            {
                // we will wait for the sip gw to become ready and will try
                // to start it again
                final SipGateway finalSipGw = sipGateway;
                sipGateway.addGatewayListener(new GatewayListener()
                {
                    @Override
                    public void onReady()
                    {
                        finalSipGw.removeGatewayListener(this);
                        start();
                    }
                });
            }
        }
    }

    /**
     * Stops running health checks.
     */
    public static void stop()
    {
        if (sipChecker != null)
        {
            executor.deRegisterRecurringRunnable(sipChecker);
            sipChecker = null;
        }
    }

    /**
     * Checks the health (status). This method only returns the cache results,
     * it does not do the actual health check
     * (i.e. creating a test conference/call).
     *
     * @throws Exception if an error occurs while checking the health (status)
     * or the check determines that is not healthy.
     */
    public static void check()
        throws Exception
    {
        if (!CallManager.isHealthy())
        {
            throw new Exception("CallManager is not healthy.");
        }

        if (sipChecker != null)
        {
            sipChecker.check();
        }
        else
        {
            if (hasSipGw)
            {
                // we have sip gw, but no sipChecker, means gw is not ready yet
                throw new Exception("GW not ready.");
            }

            if (JigasiBundleActivator.getAvailableGateways().isEmpty())
            {
                throw new Exception("No gateways configured.");
            }
            // currently we do not check the health of the transcription gateway
            // it is always healthy
        }
    }
}
