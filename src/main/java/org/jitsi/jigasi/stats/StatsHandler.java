/*
 * Jigasi, the JItsi GAteway to SIP.
 *
 * Copyright @ 2015 - present, 8x8 Inc
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
package org.jitsi.jigasi.stats;

import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.protocol.event.*;
import net.java.sip.communicator.util.*;
import org.jitsi.jigasi.*;
import org.jitsi.stats.media.*;
import org.jitsi.utils.concurrent.*;
import org.jitsi.utils.version.*;
import org.jitsi.utils.version.Version;
import org.jxmpp.jid.*;
import org.jxmpp.jid.impl.*;
import org.jxmpp.stringprep.*;
import org.osgi.framework.*;

import java.util.*;
import java.util.concurrent.*;

/**
 * The entry point for stats logging.
 * Listens for start of calls to start reporting and end of calls
 * to stop reporting.
 *
 * @author Damian Minkov
 */
public class StatsHandler
    extends CallChangeAdapter
{
    /**
     * The logger.
     */
    private final static Logger logger = Logger.getLogger(StatsHandler.class);

    /**
     * CallStats account property: appId
     * The account id from callstats.io settings.
     */
    private static final String CS_ACC_PROP_APP_ID = "CallStats.appId";

    /**
     * CallStats account property: keyId
     * The key id from callstats.io settings.
     */
    private static final String CS_ACC_PROP_KEY_ID = "CallStats.keyId";

    /**
     * CallStats account property: keyPath
     * The path to the key file on the file system.
     */
    private static final String CS_ACC_PROP_KEY_PATH = "CallStats.keyPath";

    /**
     * CallStats account property: conferenceIDPrefix
     * The domain that this stats account is handling.
     */
    private static final String CS_ACC_PROP_CONFERENCE_PREFIX
        = "CallStats.conferenceIDPrefix";

    /**
     * CallStats account property: STATISTICS_INTERVAL
     * The interval on which to publish statistics.
     */
    private static final String CS_ACC_PROP_STATISTICS_INTERVAL
        = "CallStats.STATISTICS_INTERVAL";

    /**
     * The default value for statistics interval.
     */
    public static final int DEFAULT_STAT_INTERVAL = 5000;

    /**
     * CallStats account property: jigasiId
     * The jigasi id to report to callstats.io.
     */
    private static final String CS_ACC_PROP_JIGASI_ID
        = "CallStats.jigasiId";

    /**
     * The default jigasi id to use if setting is missing.
     */
    public static final String DEFAULT_JIGASI_ID = "jigasi";

    /**
     * The {@link RecurringRunnableExecutor} which periodically invokes
     * generating and pushing statistics per call for every statistic from the
     * MediaStream.
     */
    private static final RecurringRunnableExecutor statisticsExecutor
        = new RecurringRunnableExecutor(
            StatsHandler.class.getSimpleName() + "-statisticsExecutor");

    /**
     * List of the processor per call. Kept in order to stop and
     * deRegister them from the executor.
     */
    private final Map<Call,CallPeriodicRunnable>
        statisticsProcessors = new ConcurrentHashMap<>();

    /**
     * The remote endpoint jvb or sip.
     */
    private final String remoteEndpointID;

    /**
     * The origin ID. From jigasi we always report jigasi-NUMBER as
     * the origin ID.
     */
    private final String originID;

    /**
     * Constructs StatsHandler.
     * @param remoteEndpointID the remote endpoint.
     */
    public StatsHandler(String originID, String remoteEndpointID)
    {
        this.remoteEndpointID = remoteEndpointID;
        this.originID = originID;
    }

    @Override
    public synchronized void callStateChanged(CallChangeEvent evt)
    {
        Call call = evt.getSourceCall();

        if (call.getCallState() == CallState.CALL_IN_PROGRESS)
        {
            startConferencePeriodicRunnable(call);
        }
        else if(call.getCallState() == CallState.CALL_ENDED)
        {
            CallPeriodicRunnable cpr
                = statisticsProcessors.remove(call);

            if (cpr == null)
            {
                return;
            }

            cpr.stop();
            statisticsExecutor.deRegisterRecurringRunnable(cpr);
        }
    }

    /**
     * Starts <tt>CallPeriodicRunnable</tt> for a call.
     * @param call the call.
     */
    private void startConferencePeriodicRunnable(Call call)
    {
        CallContext callContext = (CallContext) call.getData(CallContext.class);

        if(statisticsProcessors.get(call) != null)
        {
            logger.warn(callContext + " Stats handler already started for call:" + call);

            return;
        }

        BundleContext bundleContext = JigasiBundleActivator.osgiContext;
        Object source = callContext.getSource();

        if (source == null)
        {
            logger.warn(callContext + " No source of callContext found, will not init stats");
            return;
        }

        AccountID targetAccountID = null;
        if (source instanceof ProtocolProviderService
            && ((ProtocolProviderService) source).getProtocolName()
                    .equals(ProtocolNames.JABBER))
        {
            targetAccountID = ((ProtocolProviderService) source).getAccountID();
        }
        else
        {
            // if call is not created by a jabber provider
            // try to match conferenceIDPrefix of jabber accounts
            // to callContext.getDomain
            Collection<ServiceReference<ProtocolProviderService>> providers
                = ServiceUtils.getServiceReferences(
                bundleContext,
                ProtocolProviderService.class);

            for (ServiceReference<ProtocolProviderService> serviceRef
                : providers)
            {
                ProtocolProviderService candidate
                    = bundleContext.getService(serviceRef);

                if (ProtocolNames.JABBER.equals(candidate.getProtocolName()))
                {
                    AccountID acc = candidate.getAccountID();
                    String confPrefix = acc.getAccountPropertyString(
                        CS_ACC_PROP_CONFERENCE_PREFIX);
                    if (callContext.getDomain().equals(confPrefix))
                    {
                        targetAccountID = acc;
                        break;
                    }
                }
            }
        }

        if (targetAccountID == null)
        {
            // No account found with enabled stats
            // we are maybe in the case where there are no xmpp control accounts
            // this is when outgoing calls are disabled
            // and we have only the xmpp client account, which may have the
            // callstats settings

            for (Call confCall : call.getConference().getCalls())
            {
                if (confCall.getProtocolProvider().getProtocolName()
                    .equals(ProtocolNames.JABBER))
                {
                    AccountID acc = confCall.getProtocolProvider()
                        .getAccountID();
                    if (acc.getAccountPropertyInt(CS_ACC_PROP_APP_ID, 0) != 0)
                    {
                        // there are callstats settings then use it
                        targetAccountID = acc;
                    }
                }
            }

            if (targetAccountID == null)
            {
                logger.debug(callContext + " No account found with enabled stats");

                return;
            }
        }

        int appId = targetAccountID.getAccountPropertyInt(CS_ACC_PROP_APP_ID, 0);
        String keyId = targetAccountID.getAccountPropertyString(CS_ACC_PROP_KEY_ID);
        String keyPath = targetAccountID.getAccountPropertyString(CS_ACC_PROP_KEY_PATH);
        String jigasiId = targetAccountID.getAccountPropertyString(CS_ACC_PROP_JIGASI_ID, DEFAULT_JIGASI_ID);

        ServiceReference<VersionService> serviceReference = bundleContext.getServiceReference(VersionService.class);
        VersionService versionService = (serviceReference == null) ? null : bundleContext.getService(serviceReference);
        Version version = versionService != null ? versionService.getCurrentVersion() : null;

        EntityBareJid roomJid;
        try
        {
            roomJid = JidCreate.entityBareFrom(callContext.getRoomName());
        }
        catch(XmppStringprepException e)
        {
            logger.warn("Not stating stats handler as provided roomName is not a jid:" + callContext.getRoomName(), e);
            return;
        }

        StatsServiceFactory.getInstance().createStatsService(
            version,
            appId,
            null,
            keyId,
            keyPath,
            jigasiId,
            true,
            new StatsServiceInitListener(roomJid, callContext, call, targetAccountID));
    }

    /**
     * Disposes this stats handler and any left processors are cleared.
     */
    public void dispose()
    {
        statisticsProcessors.values().stream()
            .forEach(cpr ->
                {
                    cpr.stop();
                    statisticsExecutor.deRegisterRecurringRunnable(cpr);
                });
    }

    /**
     * Waits for <tt>StatsService</tt> to registered to OSGi.
     */
    private class StatsServiceInitListener
        implements StatsServiceFactory.InitCallback
    {
        /**
         *
         */
        private EntityBareJid roomJid;

        /**
         * The call context.
         */
        private CallContext callContext;

        /**
         * The call.
         */
        private Call call;

        /**
         * The accountID.
         */
        private AccountID accountID;

        /**
         * Constructs <tt>StatsServiceListener</tt>.
         * @param callContext the call context.
         * @param call the call.
         * @param accountID the accountID.
         */
        StatsServiceInitListener(
            EntityBareJid roomJid,
            CallContext callContext,
            Call call,
            AccountID accountID)
        {
            this.roomJid = roomJid;
            this.callContext = callContext;
            this.call = call;
            this.accountID = accountID;
        }

        /**
         * Cleans instances the listener holds.
         */
        private void clean()
        {
            this.callContext = null;
            this.call = null;
            this.accountID = null;
        }

        @Override
        public void error(String reason, String message)
        {
            logger.error(callContext + " Jitsi-stats library failed to initialize "
                + "with reason: " + reason
                + " and error message: " + message
                + " for account: " + accountID);

            // callstats holds this listener instance
            // and we clean instances to make sure we do not leave
            // anything behind, as much as possible
            this.clean();
        }

        @Override
        public void onInitialized(StatsService statsService, String message)
        {
            String conferenceIDPrefix = accountID.getAccountPropertyString(CS_ACC_PROP_CONFERENCE_PREFIX);
            int interval = accountID.getAccountPropertyInt(CS_ACC_PROP_STATISTICS_INTERVAL, DEFAULT_STAT_INTERVAL);

            CallPeriodicRunnable cpr = new CallPeriodicRunnable(
                call,
                interval,
                statsService,
                roomJid,
                conferenceIDPrefix,
                DEFAULT_JIGASI_ID + "-" + originID,
                remoteEndpointID);
            cpr.start();

            // register for periodic execution.
            statisticsProcessors.put(call, cpr);
            statisticsExecutor.registerRecurringRunnable(cpr);
        }
    }
}
