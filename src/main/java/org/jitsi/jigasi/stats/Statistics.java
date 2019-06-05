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
import java.util.concurrent.atomic.*;
import java.util.stream.*;

import javax.servlet.http.*;

import org.eclipse.jetty.server.*;
import org.osgi.framework.*;
import org.json.simple.*;

import org.jitsi.xmpp.extensions.colibri.*;
import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.protocol.jabber.*;
import net.java.sip.communicator.util.*;

import org.jitsi.jigasi.*;
import org.jitsi.jigasi.xmpp.*;

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
     * The name of the number of conferences statistic. Its runtime type is
     * {@code Integer}.
     */
    public static final String CONFERENCES = "conferences";

    /**
     * The name of the conference sizes statistic.
     */
    public static final String CONFERENCE_SIZES = "conference_sizes";

    /**
     * The name of the number of participants statistic. Its runtime type is
     * {@code Integer}.
     */
    public static final String NUMBEROFPARTICIPANTS = "participants";

    /**
     * The name of the number of threads statistic. Its runtime type is
     * {@code Integer}.
     */
    public static final String NUMBEROFTHREADS = "threads";

    /**
     * The name of the stat that indicates jigasi has entered graceful
     * shutdown mode. Its runtime type is {@code Boolean}.
     */
    public static final String SHUTDOWN_IN_PROGRESS = "graceful_shutdown";

    /**
     * The name of the piece of statistic which specifies the date and time at
     * which the associated set of statistics was generated. Its runtime type is
     * {@code String} and the value represents a {@code Date} value.
     */
    public static final String TIMESTAMP = "current_timestamp";

    /**
     * The name of the stat that indicates total number of
     * completed conferences.
     * {@code Integer}.
     */
    public static final String TOTAL_CONFERENCES
        = "total_conferences_completed";

    /**
     * The name of the stat that indicated the total number of participants
     * in completed conferences.
     * {@code Integer}.
     */
    public static final String TOTAL_NUMBEROFPARTICIPANTS
        = "total_participants";

    /**
     * The name of the stat indicating the total number of conference-seconds
     * (i.e. the sum of the lengths is seconds).
     */
    public static final String TOTAL_CONFERENCE_SECONDS
        = "total_conference_seconds";

    /**
     * The name of the number of conferences which do not receive media from
     * the gateway side.
     * {@code Integer}.
     */
    public static final String TOTAL_CALLS_WITH_DROPPED_MEDIA
        = "total_calls_with_dropped_media";

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

        // NUMBEROFTHREADS
        stats.put(NUMBEROFTHREADS,
            ManagementFactory.getThreadMXBean().getThreadCount());

        // TIMESTAMP
        stats.put(TIMESTAMP, currentTimeMillis());

        // TOTAL stats
        stats.put(TOTAL_CONFERENCES, totalConferencesCount);
        stats.put(TOTAL_NUMBEROFPARTICIPANTS, totalParticipantsCount);
        stats.put(TOTAL_CONFERENCE_SECONDS, cumulativeConferenceSeconds);
        stats.put(TOTAL_CALLS_WITH_DROPPED_MEDIA,
            totalCallsWithMediaDroppedCount.get());


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

        // CONFERENCES
        stats.put(CONFERENCES, conferences);

        // CONFERENCE_SIZES
        JSONArray conferenceSizesJson = new JSONArray();
        for (int size : conferenceSizes)
            conferenceSizesJson.add(size);
        stats.put(CONFERENCE_SIZES, conferenceSizesJson);

        // NUMBEROFPARTICIPANTS
        stats.put(NUMBEROFPARTICIPANTS, participants);

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
                (int)stats.get(NUMBEROFPARTICIPANTS),
                (int)stats.get(CONFERENCES),
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
     */
    private static void updatePresenceStatusForXmppProvider(
        ProtocolProviderService pps,
        int participants,
        int conferences,
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
                    NUMBEROFPARTICIPANTS,
                    participants));
                stats.addStat(new ColibriStatsExtension.Stat(
                    SHUTDOWN_IN_PROGRESS,
                    shutdownInProgress));

                pps.getOperationSet(OperationSetJitsiMeetTools.class)
                    .sendPresenceExtension(mucRoom, stats);
            }
            catch (Exception e)
            {
                logger.error("Error updating presence for:" + pps, e);
            }
        }
    }
}
