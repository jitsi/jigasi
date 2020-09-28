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

/**
 * The entry point for stats logging.
 * Listens for start of calls to start reporting and end of calls
 * to stop reporting.
 * This is one instance per sip and one instance per xmpp call(JvbConference).
 * We need to initialize on Callstats instance of the life time of Jigasi.
 * So we keep a list of those instances per appId.
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
     * Map of all statsInstances we had created.
     */
    private static final Map<Integer, StatsServiceWrapper> statsInstances = new HashMap<>();

    /**
     * The {@link RecurringRunnableExecutor} which periodically invokes
     * generating and pushing statistics per call for every statistic from the
     * MediaStream.
     */
    private static final RecurringRunnableExecutor statisticsExecutor
        = new RecurringRunnableExecutor(
            StatsHandler.class.getSimpleName() + "-statisticsExecutor");

    /**
     * The call handled by this StatsHandler instance.
     */
    private final Call call;

    /**
     * The periodic runnable we register with the the executor.
     * We stop it and deRegister on dispose.
     */
    private CallPeriodicRunnable theStatsReporter = null;

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
     * The call context used to create this conference, contains info as
     * room name and room password and other optional parameters.
     */
    private final CallContext callContext;

    /**
     * The stats service for this instance of StatsHandler.
     */
    private StatsServiceWrapper statsService = null;

    /**
     * Constructs StatsHandler.
     * @param remoteEndpointID the remote endpoint.
     */
    public StatsHandler(Call call, String originID, String remoteEndpointID)
    {
        this.call = call;
        this.remoteEndpointID = remoteEndpointID;
        this.originID = originID;
        this.callContext = (CallContext) this.call.getData(CallContext.class);

        initStatsService();
    }

    /**
     * Initializes the stats service.
     */
    private void initStatsService()
    {
        BundleContext bundleContext = JigasiBundleActivator.osgiContext;
        Object source = this.callContext.getSource();

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
                    String confPrefix = acc.getAccountPropertyString(CS_ACC_PROP_CONFERENCE_PREFIX);
                    if (callContext.getDomain() != null && callContext.getDomain().equals(confPrefix))
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

        synchronized(statsInstances)
        {
            this.statsService = getStatsServiceWrapper(appId, targetAccountID, bundleContext);

            // Adds the call change listener only after we find config and create the stats service
            this.call.addCallChangeListener(this);
        }
    }

    /**
     * Returns already initialized wrapper or creates a new one add returns it.
     *
     * @param appId the appId of the stats instance.
     * @param accountID the target account id.
     * @param bundleContext the osgi bundle context.
     * @return the StatsServiceWrapper instance.
     */
    private StatsServiceWrapper getStatsServiceWrapper(
        int appId, AccountID accountID, BundleContext bundleContext)
    {
        if (statsInstances.containsKey(appId))
        {
            // that stat instance is already created
            return statsInstances.get(appId);
        }

        String keyId = accountID.getAccountPropertyString(CS_ACC_PROP_KEY_ID);
        String keyPath = accountID.getAccountPropertyString(CS_ACC_PROP_KEY_PATH);
        String jigasiId = accountID.getAccountPropertyString(CS_ACC_PROP_JIGASI_ID, DEFAULT_JIGASI_ID);

        String conferenceIDPrefix = accountID.getAccountPropertyString(CS_ACC_PROP_CONFERENCE_PREFIX);
        int interval = accountID.getAccountPropertyInt(
            CS_ACC_PROP_STATISTICS_INTERVAL, DEFAULT_STAT_INTERVAL);

        ServiceReference<VersionService> serviceReference = bundleContext.getServiceReference(VersionService.class);
        VersionService versionService
            = (serviceReference == null) ? null : bundleContext.getService(serviceReference);
        Version version = versionService != null ? versionService.getCurrentVersion() : null;

        logger.info(callContext + " Jitsi-stats library initializing for account: " + accountID);

        StatsService statsServiceInstance = StatsServiceFactory.getInstance()
            .createStatsService(
                version,
                appId,
                null,
                keyId,
                keyPath,
                jigasiId,
                true,
                new StatsServiceInitListener());
        StatsServiceWrapper wrapper = new StatsServiceWrapper(conferenceIDPrefix, interval, statsServiceInstance);
        statsInstances.put(appId, wrapper);

        return wrapper;
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
            stopConferencePeriodicRunnable();
        }
    }

    /**
     * Starts <tt>CallPeriodicRunnable</tt> for a call.
     * @param call the call.
     */
    private void startConferencePeriodicRunnable(Call call)
    {
        if(this.theStatsReporter != null)
        {
            logger.warn(callContext + " Stats reporter already started for call:" + this.call);
            return;
        }

        if (this.statsService == null)
        {
            logger.warn(callContext + " Stats handler missing for call:" + this.call);
        }

        if (call.getCallState() != CallState.CALL_IN_PROGRESS)
        {
            // this is the case when the callstats initialized before the call
            // was connected, once that is done we will start the runnable
            return;
        }

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

        CallPeriodicRunnable cpr = StatsHandler.this.theStatsReporter = new CallPeriodicRunnable(
            call,
            this.statsService.interval,
            this.statsService.service,
            roomJid,
            this.statsService.conferenceIDPrefix,
            DEFAULT_JIGASI_ID + "-" + originID,
            remoteEndpointID);
        cpr.start();

        // register for periodic execution.
        statisticsExecutor.registerRecurringRunnable(cpr);
    }

    /**
     * Disposes this stats handler and any left processors are cleared.
     */
    public void dispose()
    {
        this.call.removeCallChangeListener(this);

        stopConferencePeriodicRunnable();
    }

    /**
     * Stops the periodic runnable and deregister it from the executor.
     */
    private void stopConferencePeriodicRunnable()
    {
        if (this.theStatsReporter != null)
        {
            this.theStatsReporter.stop();
            statisticsExecutor.deRegisterRecurringRunnable(this.theStatsReporter);
            this.theStatsReporter = null;
        }
    }

    /**
     * Waits for <tt>StatsService</tt> to registered to OSGi.
     */
    private class StatsServiceInitListener
        implements StatsServiceFactory.InitCallback
    {
        @Override
        public void error(String reason, String message)
        {
            logger.error(callContext + " Jitsi-stats library failed to initialize "
                + "with reason: " + reason
                + " and error message: " + message);
        }

        @Override
        public void onInitialized(StatsService statsService, String message)
        {
            logger.info(callContext + " StatsService initialized " + message);

            startConferencePeriodicRunnable(call);
        }
    }

    /**
     * A wrapper to carry all needed information from the runners.
     * The confId prefix, the interval and the service.
     */
    private static class StatsServiceWrapper
    {
        /**
         * The conference prefix.
         */
        private final String conferenceIDPrefix;

        /**
         * the interval for stats used by the runnable.
         */
        private final int interval;

        /**
         * The service itself.
         */
        private final StatsService service;

        public StatsServiceWrapper(String conferenceIDPrefix, int interval, StatsService service)
        {
            this.conferenceIDPrefix = conferenceIDPrefix;
            this.interval = interval;
            this.service = service;
        }
    }
}
