/*
 * Jigasi, the JItsi GAteway to SIP.
 *
 * Copyright @ 2015 Atlassian Pty Ltd
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

import java.io.*;
import java.lang.management.*;
import java.text.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.stream.*;

import javax.servlet.http.*;

import org.eclipse.jetty.server.*;
import org.jitsi.jigasi.version.*;
import org.jitsi.utils.*;
import org.osgi.framework.*;
import org.json.simple.*;

import org.jitsi.xmpp.extensions.colibri.*;
import static org.jitsi.xmpp.extensions.colibri.ColibriStatsExtension.*;

import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.protocol.jabber.*;
import net.java.sip.communicator.util.*;

import org.jitsi.jigasi.*;
import org.jitsi.jigasi.xmpp.*;

import static org.jitsi.jigasi.JvbConference.*;

/**
 * Implements statistics that are collected by the JIgasi.
 * @author Damian Minkov
 * @author Nik Vaessen
 */
public class Statistics
{
    /**
     * The logger
     */
    private final static Logger logger
        = Logger.getLogger(Statistics.class);

    /**
     * The name of the number of conferences for which XMPP connection
     * had failed.
     * {@code Integer}.
     */
    public static final String TOTAL_CALLS_WITH_CONNECTION_FAILED
        = "total_calls_with_connection_failed";

    /**
     * The name of the number of conferences for which XMPP call was ended
     * and the gateway part is waiting for new connection.
     * {@code Integer}.
     */
    public static final String TOTAL_CALLS_WITH_SIP_CALL_WAITING
        = "total_calls_with_sip_call_waiting";

    /**
     * The name of the number of conferences for which XMPP call was ended
     * and the gateway part was waiting for new connection and the call was
     * connected to new XMPP calls.
     * {@code Integer}.
     */
    public static final String TOTAL_CALLS_WITH_SIP_CALL_RECONNECTED
        = "total_calls_with_sip_call_reconnected";

    /**
     * The name of the number of conferences for which XMPP call received transport replace for migrating to a new
     * jvb.
     * {@code Integer}.
     */
    public static final String TOTAL_CALLS_WITH_JVB_MIGRATE = "total_calls_with_jvb_migrate";

    /**
     * The name of the number of conferences for which XMPP call did not receive any media from the bridge.
     * {@code Integer}.
     */
    public static final String TOTAL_CALLS_JVB_NO_MEDIA = "total_calls_jvb_no_media";

    /**
     * The name of the stress level metric.
     */
    private static final String STRESS_LEVEL = "stress_level";

    /**
     * Total number of participants since started.
     */
    private static int totalParticipantsCount = 0;

    /**
     * Total number of conferences since started.
     */
    private static int totalConferencesCount = 0;

    /**
     * Total number of calls with dropped media since started.
     */
    private static AtomicLong totalCallsWithMediaDroppedCount
        = new AtomicLong();

    /**
     * Total number of calls with xmpp connection failed since started.
     */
    private static AtomicLong totalCallsWithConnectionFailedCount
        = new AtomicLong();

    /**
     * Total number of calls with xmpp call terminated and sip call waiting
     * for new xmpp call.
     */
    private static AtomicLong totalCallsWithSipCallWaiting
        = new AtomicLong();

    /**
     * Total number of calls with xmpp call terminated and sip call waiting
     * for new xmpp call and new xmpp call and both calls were connected
     * and operational.
     */
    private static AtomicLong totalCallsWithSipCalReconnected
        = new AtomicLong();

    /**
     * Total number of calls with xmpp call receiving transport replace for moving to a new bridge.
     */
    private static AtomicLong totalCallsWithJvbMigrate = new AtomicLong();

    /**
     * Total number of calls with xmpp call not receiving media from the bridge.
     */
    private static AtomicLong totalCallsJvbNoMedia = new AtomicLong();

    /**
     * Cumulative number of seconds of all conferences.
     */
    private static long cumulativeConferenceSeconds = 0;

    /**
     * The <tt>DateFormat</tt> to be utilized by <tt>Statistics</tt>
     * in order to represent time and date as <tt>String</tt>.
     */
    private static final DateFormat dateFormat;

    static
    {
        dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    /**
     * The number of buckets to use for conference sizes.
     */
    private static final int CONFERENCE_SIZE_BUCKETS = 22;

    /**
     * We want to send the stats in a separate thread to not block
     * join/leave rooms process and we want to send those stats to all servers
     * even when some maybe blocked because of lack of connectivity to the
     * server/
     */
    private static ExecutorService threadPool = Executors.newFixedThreadPool(3);

    /**
     * Gets a JSON representation of the statistics of a specific
     * {@link SipGateway}.
     *
     * @param baseRequest the original unwrapped {@link Request} object
     * @param request the request either as the {@code Request} object or a
     * wrapper of that request
     * @param response the response either as the {@code Response} object or a
     * wrapper of that response
     */
    public static synchronized void sendJSON(
            Request baseRequest,
            HttpServletRequest request,
            HttpServletResponse response)
        throws IOException
    {
        Map<String,Object> stats = new HashMap<>();

        stats.putAll(getSessionStats());

        stats.put(THREADS,
            ManagementFactory.getThreadMXBean().getThreadCount());

        // TIMESTAMP
        stats.put(TIMESTAMP, currentTimeMillis());

        // TOTAL stats
        stats.put(TOTAL_CONFERENCES_COMPLETED, totalConferencesCount);
        stats.put(TOTAL_PARTICIPANTS, totalParticipantsCount);
        stats.put(TOTAL_CONFERENCE_SECONDS, cumulativeConferenceSeconds);
        stats.put(TOTAL_CALLS_WITH_DROPPED_MEDIA,
            totalCallsWithMediaDroppedCount.get());
        stats.put(TOTAL_CALLS_WITH_CONNECTION_FAILED,
            totalCallsWithConnectionFailedCount.get());
        stats.put(TOTAL_CALLS_WITH_SIP_CALL_WAITING,
            totalCallsWithSipCallWaiting.get());
        stats.put(TOTAL_CALLS_WITH_SIP_CALL_RECONNECTED,
            totalCallsWithSipCalReconnected.get());
        stats.put(TOTAL_CALLS_WITH_JVB_MIGRATE, totalCallsWithJvbMigrate.get());
        stats.put(TOTAL_CALLS_JVB_NO_MEDIA, totalCallsJvbNoMedia.get());

        stats.put(SHUTDOWN_IN_PROGRESS,
            JigasiBundleActivator.isShutdownInProgress());

        response.setStatus(HttpServletResponse.SC_OK);
        new JSONObject(stats).writeJSONString(response.getWriter());
    }

    /**
     * Counts conferences count, participants count and conference size
     * distributions.
     *
     * @return a map with the stats.
     */
    private static Map<String,Object> getSessionStats()
    {
        // get sessions from all gateways
        List<AbstractGatewaySession> sessions = new ArrayList<>();
        JigasiBundleActivator.getAvailableGateways().forEach(
            gw -> sessions.addAll(gw.getActiveSessions()));

        int[] conferenceSizes = new int[CONFERENCE_SIZE_BUCKETS];
        Map<String,Object> stats = new HashMap<>();

        int participants = 0;
        int conferences = 0;

        for(AbstractGatewaySession ses : sessions)
        {
            if (ses.getJvbChatRoom() == null)
            {
                continue;
            }

            // do not count focus
            int conferenceEndpoints
                = ses.getJvbChatRoom().getMembersCount() - 1;
            participants += conferenceEndpoints;
            int idx
                = conferenceEndpoints < conferenceSizes.length
                ? conferenceEndpoints
                : conferenceSizes.length - 1;
            if (idx >= 0)
            {
                conferenceSizes[idx]++;
            }
            conferences++;
        }

        stats.put(CONFERENCES, conferences);

        JSONArray conferenceSizesJson = new JSONArray();
        for (int size : conferenceSizes)
            conferenceSizesJson.add(size);
        stats.put(CONFERENCE_SIZES, conferenceSizesJson);

        stats.put(PARTICIPANTS, participants);

        // This emulates the current behavour that we have implemented in our autoscaling
        // rules. It's not a good model for estimating load and will change in the near
        // future
        double stressLevel = participants / 450.0;
        stats.put(STRESS_LEVEL, stressLevel);

        return stats;
    }

    /**
     * Returns the current time stamp as a (formatted) <tt>String</tt>.
     * @return the current time stamp as a (formatted) <tt>String</tt>.
     */
    private static String currentTimeMillis()
    {
        return dateFormat.format(new Date());
    }

    /**
     * Adds the value to the number of total participants count.
     * @param value the value to add to the total participants count.
     */
    public static void addTotalParticipantsCount(int value)
    {
        totalParticipantsCount += value;
    }

    /**
     * Adds the value to the total number of completed conferences.
     * @param value the value to add to the total number
     *              of completed conferences.
     */
    public static void addTotalConferencesCount(int value)
    {
        totalConferencesCount += value;
    }

    /**
     * Increment the value of total number of calls with dropped media.
     */
    public static void incrementTotalCallsWithMediaDropped()
    {
        totalCallsWithMediaDroppedCount.incrementAndGet();
    }

    /**
     * Increment the value of total number of calls with XMPP connection failed.
     */
    public static void incrementTotalCallsWithConnectionFailed()
    {
        totalCallsWithConnectionFailedCount.incrementAndGet();
    }

    /**
     * Increment the value of total number of calls with XMPP calls terminated
     * and sip call waiting for new xmpp call to be connected.
     */
    public static void incrementTotalCallsWithSipCallWaiting()
    {
        totalCallsWithSipCallWaiting.incrementAndGet();
    }

    /**
     * Increment the value of total number of calls with XMPP calls terminated
     * and new XMPP call been connected again with the SIP call
     */
    public static void incrementTotalCallsWithSipCallReconnected()
    {
        totalCallsWithSipCalReconnected.incrementAndGet();
    }

    /**
     * Increment the value of total number of calls with XMPP calls terminated
     * and new XMPP call been connected again with the SIP call
     */
    public static void incrementTotalCallsWithJvbMigrate()
    {
        totalCallsWithJvbMigrate.incrementAndGet();
    }

    /**
     * Increment the value of total number of calls with XMPP calls not receiving media from the bridge.
     */
    public static void incrementTotalCallsJvbNoMedia()
    {
        totalCallsJvbNoMedia.incrementAndGet();
    }

    /**
     * Adds the value to the number of total conference seconds.
     * @param value the value to add to the number of total conference seconds.
     */
    public static void addCumulativeConferenceSeconds(long value)
    {
        cumulativeConferenceSeconds += value;
    }

    /**
     * Updates the presence in each {@link ProtocolProviderService} registered
     * with OSGi with the current number of conferences and participants.
     */
    public static void updatePresenceStatusForXmppProviders()
    {
        BundleContext osgiContext = JigasiBundleActivator.osgiContext;
        Collection<ServiceReference<ProtocolProviderService>> refs
            = ServiceUtils.getServiceReferences(
                osgiContext,
                ProtocolProviderService.class);

        List<ProtocolProviderService> ppss
            = refs.stream()
            .map(ref -> osgiContext.getService(ref))
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

        updatePresenceStatusForXmppProviders(ppss);
    }

    /**
     * Updates the presence in each of the given {@link ProtocolProviderService}s
     * with the current number of conferences and participants.
     *
     * @param ppss the list of protocol providers to update.
     */
    public static void updatePresenceStatusForXmppProviders(
        List<ProtocolProviderService> ppss)
    {
        final Map<String,Object> stats = getSessionStats();

        ppss.forEach(pps ->
            updatePresenceStatusForXmppProvider(
                pps,
                (int)stats.get(PARTICIPANTS),
                (int)stats.get(CONFERENCES),
                (double)stats.get(STRESS_LEVEL),
                JigasiBundleActivator.isShutdownInProgress()));
    }

    /**
     * Updates the presence in the given {@link ProtocolProviderService} with
     * the current number of conferences and participants.
     *
     * Adds a {@link ColibriStatsExtension} to our presence in the brewery room.
     *
     * @param pps the protocol provider service
     * @param participants the participant count.
     * @param conferences the active session/conference count.
     * @param stressLevel the current stress level
     */
    private static void updatePresenceStatusForXmppProvider(
        ProtocolProviderService pps,
        int participants,
        int conferences,
        double stressLevel,
        boolean shutdownInProgress)
    {
        if (ProtocolNames.JABBER.equals(pps.getProtocolName())
            && pps.getAccountID() instanceof JabberAccountID
            && !((JabberAccountID)pps.getAccountID()).isAnonymousAuthUsed()
            && pps.isRegistered())
        {
            try
            {
                // use only providers which are used for muc call control
                String roomName = pps.getAccountID().getAccountPropertyString(
                    CallControlMucActivator.ROOM_NAME_ACCOUNT_PROP);
                if (roomName == null)
                {
                    return;
                }

                OperationSetMultiUserChat muc
                    = pps.getOperationSet(OperationSetMultiUserChat.class);
                // this should be getting the room from the cache,
                // no network operations
                ChatRoom mucRoom = muc.findRoom(roomName);

                if (mucRoom == null)
                {
                    return;
                }

                ColibriStatsExtension stats = new ColibriStatsExtension();
                stats.addStat(new ColibriStatsExtension.Stat(
                    CONFERENCES,
                    conferences));
                stats.addStat(new ColibriStatsExtension.Stat(
                    PARTICIPANTS,
                    participants));
                stats.addStat(new ColibriStatsExtension.Stat(
                    SHUTDOWN_IN_PROGRESS,
                    shutdownInProgress));
                stats.addStat((new ColibriStatsExtension.Stat(
                    STRESS_LEVEL,
                    stressLevel
                )));

                String region = JigasiBundleActivator.getConfigurationService()
                    .getString(LOCAL_REGION_PNAME);
                if(!StringUtils.isNullOrEmpty(region))
                {
                    stats.addStat(new ColibriStatsExtension.Stat(
                        REGION,
                        region));
                }

                stats.addStat(new ColibriStatsExtension.Stat(
                    VERSION,
                    CurrentVersionImpl.VERSION));

                ColibriStatsExtension.Stat transcriberStat =
                    new ColibriStatsExtension.Stat(
                        SUPPORTS_TRANSCRIPTION, false);
                ColibriStatsExtension.Stat sipgwStat =
                    new ColibriStatsExtension.Stat(SUPPORTS_SIP, false);

                JigasiBundleActivator.getAvailableGateways().forEach(gw ->
                    {
                        if (gw instanceof TranscriptionGateway)
                        {
                            transcriberStat.setValue(true);
                        }
                        if (gw instanceof SipGateway)
                        {
                            sipgwStat.setValue(true);
                        }
                    });
                stats.addStat(transcriberStat);
                stats.addStat(sipgwStat);

                threadPool.submit(
                    () -> pps.getOperationSet(OperationSetJitsiMeetTools.class)
                            .sendPresenceExtension(mucRoom, stats));
            }
            catch (Exception e)
            {
                logger.error("Error updating presence for:" + pps, e);
            }
        }
    }
}
