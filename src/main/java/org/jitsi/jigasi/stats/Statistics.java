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
import java.util.stream.*;

import jakarta.servlet.http.*;

import net.java.sip.communicator.impl.protocol.jabber.*;
import net.java.sip.communicator.util.osgi.ServiceUtils;
import org.apache.commons.lang3.*;
import org.eclipse.jetty.server.*;
import org.jitsi.jigasi.metrics.*;
import org.jitsi.jigasi.version.*;
import org.jitsi.metrics.*;
import org.jitsi.utils.logging.Logger;
import org.osgi.framework.*;
import org.json.simple.*;

import org.jitsi.xmpp.extensions.colibri.*;
import static org.jitsi.xmpp.extensions.colibri.ColibriStatsExtension.*;

import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.protocol.jabber.*;

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
    private final static Logger logger = Logger.getLogger(Statistics.class);

    /**
     * The name of the number of times in which we do not receive media from the gateway side.
     * {@code Integer}.
     */
    public static final String TOTAL_COUNT_DROPPED_MEDIA = "total_count_dropped_media";

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
     * The name of the number of conferences for which we did not receive response to the sip heartbeat.
     */
    public static final String TOTAL_CALLS_NO_HEARTBEAT = "total_calls_no_heartbeat_response";

    /**
     * The name of the number of started transcriptions.
     */
    public static final String TOTAL_TRANSCRIBER_STARTED = "total_transcriber_started";

    /**
     * The name of the number of stopped transcriptions.
     */
    public static final String TOTAL_TRANSCRIBER_STOPPED = "total_transcriber_stopped";

    /**
     * The name of the number of failed transcriptions.
     */
    public static final String TOTAL_TRANSCRIBER_FAILED = "total_transcriber_failed";

    /**
     * The total number of minute intervals submitted to the Google API for transcription.
     */
    public static final String TOTAL_TRANSCRIBER_G_MINUTES = "total_transcriber_g_minutes";

    /**
     * The total number of requests submitted to the Google Cloud Speech API.
     */
    public static final String TOTAL_TRANSCRIBER_G_REQUESTS = "total_transcriber_g_requests";

    /**
     * The total number of connection errors for the transcriber.
     */
    public static final String TOTAL_TRANSCRIBER_CONNECTION_ERRORS = "total_transcriber_connection_errors";

    /**
     * The total number of no result errors for the transcriber.
     */
    public static final String TOTAL_TRANSCRIBER_NO_RESUL_ERRORS = "total_transcriber_no_result_errors";

    /**
     * The total number of send errors for the transcriber.
     */
    public static final String TOTAL_TRANSCRIBER_SEND_ERRORS = "total_transcriber_send_errors";

    /**
     * The total number of session creation errors for the transcriber.
     */
    public static final String TOTAL_TRANSCRIBER_SESSION_CREATION_ERRORS = "total_transcriber_session_creation_errors";

    /**
     * The name of the property that holds the normalizing constant that is used to reduce the number of
     * current conferences to a stress level metric {@link #CONFERENCES_THRESHOLD}.
     */
    private static final String CONFERENCES_THRESHOLD_PNAME = "org.jitsi.jigasi.CONFERENCES_THRESHOLD";

    /**
     * The default value of the normalizing constant that is used to reduce the number of current conferences
     * to a stress level metric {@link #CONFERENCES_THRESHOLD}.
     */
    private static final double CONFERENCES_THRESHOLD_DEFAULT = 100;

    /**
     * The normalizing constant that is used to reduce the number of current conferences to a stress level
     * metric. This number should be the max number of conferences that this Jigasi instance can handle
     * correctly (i.e. if Jigasi has more conferences it is overloaded and it may experience abnormal operation).
     * The stress_level metric is computed in {@link #getSessionStats()}.
     */
    private static final double CONFERENCES_THRESHOLD = JigasiBundleActivator
            .getConfigurationService()
            .getDouble(CONFERENCES_THRESHOLD_PNAME, CONFERENCES_THRESHOLD_DEFAULT);

    /**
     * The name of the stress level metric.
     */
    private static final String STRESS_LEVEL = "stress_level";

    /**
     * Total number of times with dropped media since started.
     */
    private static final CounterMetric totalMediaDroppedCount = JigasiMetricsContainer.INSTANCE.registerCounter(
            TOTAL_COUNT_DROPPED_MEDIA,
            "Total number of times with dropped media since started.");

    /**
     * Total number of participants since started.
     */
    private static final CounterMetric totalParticipantsCount = JigasiMetricsContainer.INSTANCE.registerCounter(
            TOTAL_PARTICIPANTS,
            "Total number of participants since started.");

    /**
     * Total number of conferences since started.
     */
    private static CounterMetric totalConferencesCount = JigasiMetricsContainer.INSTANCE.registerCounter(
            TOTAL_CONFERENCES_COMPLETED,
            "Total number of conferences since started.");

    /**
     * Total number of calls with dropped media since started.
     */
    private static CounterMetric totalCallsWithMediaDroppedCount = JigasiMetricsContainer.INSTANCE.registerCounter(
            TOTAL_CALLS_WITH_DROPPED_MEDIA,
            "Total number of calls with dropped media since started.");

    /**
     * Total number of calls with xmpp connection failed since started.
     */
    private static CounterMetric totalCallsWithConnectionFailedCount = JigasiMetricsContainer.INSTANCE.registerCounter(
            TOTAL_CALLS_WITH_CONNECTION_FAILED,
            "Total number of calls with xmpp connection failed since started."
    );

    /**
     * Total number of calls with xmpp call terminated and sip call waiting for new xmpp call.
     */
    private static CounterMetric totalCallsWithSipCallWaiting = JigasiMetricsContainer.INSTANCE.registerCounter(
            TOTAL_CALLS_WITH_SIP_CALL_WAITING,
            "Total number of calls with xmpp call terminated and sip call waiting for new xmpp call.");

    /**
     * Total number of calls with xmpp call terminated and sip call waiting
     * for new xmpp call and new xmpp call and both calls were connected
     * and operational.
     */
    private static CounterMetric totalCallsWithSipCallReconnected = JigasiMetricsContainer.INSTANCE.registerCounter(
            TOTAL_CALLS_WITH_SIP_CALL_RECONNECTED,
            "Total number of calls with xmpp call terminated and sip call waiting.");

    /**
     * Total number of calls with xmpp call receiving transport replace for moving to a new bridge.
     */
    private static CounterMetric totalCallsWithJvbMigrate = JigasiMetricsContainer.INSTANCE.registerCounter(
            TOTAL_CALLS_WITH_JVB_MIGRATE,
            "Total number of calls with xmpp call receiving transport replace for moving to a new bridge.");

    /**
     * Total number of calls with xmpp call not receiving media from the bridge.
     */
    private static CounterMetric totalCallsJvbNoMedia = JigasiMetricsContainer.INSTANCE.registerCounter(
            TOTAL_CALLS_JVB_NO_MEDIA,
            "Total number of calls with xmpp call not receiving media from the bridge.");

    /**
     * Total number of calls dropped due to no response to sip heartbeat.
     */
    private static CounterMetric totalCallsWithNoHeartBeatResponse = JigasiMetricsContainer.INSTANCE.registerCounter(
            TOTAL_CALLS_NO_HEARTBEAT,
            "Total number of calls dropped due to no response to sip heartbeat.");

    /**
     * Total number of transcriptions started.
     */
    private static CounterMetric totalTrasnscriberStarted = JigasiMetricsContainer.INSTANCE.registerCounter(
            TOTAL_TRANSCRIBER_STARTED,
            "Total number of started transcriptions.");

    /**
     * Total number of transcriptions stopped.
     */
    private static CounterMetric totalTrasnscriberStopped = JigasiMetricsContainer.INSTANCE.registerCounter(
            TOTAL_TRANSCRIBER_STOPPED,
            "Total number of stopped transcriptions.");

    /**
     * Total number of transcriptions failures.
     */
    private static CounterMetric totalTrasnscriberFailed = JigasiMetricsContainer.INSTANCE.registerCounter(
            TOTAL_TRANSCRIBER_FAILED,
            "Total number of failed transcriptions.");

    /**
     * Total number of transcriptions connection errors.
     */
    private static CounterMetric totalTrasnscriberConnectionErrors = JigasiMetricsContainer.INSTANCE.registerCounter(
            TOTAL_TRANSCRIBER_CONNECTION_ERRORS,
            "Total number of transcriber connection errors.");

    /**
     * Total number of transcriptions no result errors.
     */
    private static CounterMetric totalTrasnscriberNoResultErrors = JigasiMetricsContainer.INSTANCE.registerCounter(
            TOTAL_TRANSCRIBER_NO_RESUL_ERRORS,
            "Total number of transcriber no result errors.");

    /**
     * Total number of transcriptions send errors.
     */
    private static CounterMetric totalTrasnscriberSendErrors = JigasiMetricsContainer.INSTANCE.registerCounter(
            TOTAL_TRANSCRIBER_SEND_ERRORS,
            "Total number of transcriber send errors.");

    /**
     * Total number of transcriptions session creation errors.
     */
    private static CounterMetric totalTrasnscriberSessionCreationErrors
        = JigasiMetricsContainer.INSTANCE.registerCounter(TOTAL_TRANSCRIBER_SESSION_CREATION_ERRORS,
            "Total number of transcriber session creation errors.");

    /**
     * The total number of 15 second intervals submitted to the Google API for transcription.
     */
    private static LongGaugeMetric totalTranscriberMinutes = JigasiMetricsContainer.INSTANCE.registerLongGauge(
            TOTAL_TRANSCRIBER_G_MINUTES,
            "Total number of minute intervals.");

    /**
     * The total number of requests submitted to the Google Cloud Speech API.
     */
    private static CounterMetric totalTrasnscriberRequests = JigasiMetricsContainer.INSTANCE.registerCounter(
            TOTAL_TRANSCRIBER_G_REQUESTS,
            "Total number of transcriber requests.");

    /**
     * Cumulative number of seconds of all conferences.
     */
    private static CounterMetric cumulativeConferenceSeconds = JigasiMetricsContainer.INSTANCE.registerCounter(
            TOTAL_CONFERENCE_SECONDS,
            "Cumulative number of seconds of all conferences");

    private static final LongGaugeMetric threadsMetric = JigasiMetricsContainer.INSTANCE.registerLongGauge(
            "threads",
            "Number of JVM threads.");
    private static final BooleanMetric shutdownMetric = JigasiMetricsContainer.INSTANCE.registerBooleanMetric(
            SHUTDOWN_IN_PROGRESS,
            "Whether jigasi is in graceful shutdown mode.");
    private static final LongGaugeMetric conferencesMetric = JigasiMetricsContainer.INSTANCE.registerLongGauge(
            CONFERENCES,
            "Number of conferences.");
    private static final LongGaugeMetric participantsMetric = JigasiMetricsContainer.INSTANCE.registerLongGauge(
            PARTICIPANTS,
            "Number of participants.");
    private static final DoubleGaugeMetric stressMetric = JigasiMetricsContainer.INSTANCE.registerDoubleGauge(
            STRESS_LEVEL,
            "Stress level.");

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
        updateMetrics();

        Map<String, Object> stats = new HashMap<>();

        stats.putAll(getSessionStats());

        stats.put(THREADS, threadsMetric.get());

        // TIMESTAMP
        stats.put(TIMESTAMP, currentTimeMillis());

        // TOTAL stats
        stats.put(TOTAL_CONFERENCES_COMPLETED, totalConferencesCount.get());
        stats.put(TOTAL_PARTICIPANTS, totalParticipantsCount.get());
        stats.put(TOTAL_CONFERENCE_SECONDS, cumulativeConferenceSeconds.get());
        stats.put(TOTAL_CALLS_WITH_DROPPED_MEDIA, totalCallsWithMediaDroppedCount.get());
        stats.put(TOTAL_COUNT_DROPPED_MEDIA, totalMediaDroppedCount.get());
        stats.put(TOTAL_CALLS_WITH_CONNECTION_FAILED, totalCallsWithConnectionFailedCount.get());
        stats.put(TOTAL_CALLS_WITH_SIP_CALL_WAITING, totalCallsWithSipCallWaiting.get());
        stats.put(TOTAL_CALLS_WITH_SIP_CALL_RECONNECTED, totalCallsWithSipCallReconnected.get());
        stats.put(TOTAL_CALLS_WITH_JVB_MIGRATE, totalCallsWithJvbMigrate.get());
        stats.put(TOTAL_CALLS_JVB_NO_MEDIA, totalCallsJvbNoMedia.get());
        stats.put(TOTAL_CALLS_NO_HEARTBEAT, totalCallsWithNoHeartBeatResponse.get());

        stats.put(TOTAL_TRANSCRIBER_G_REQUESTS, totalTrasnscriberRequests.get());
        stats.put(TOTAL_TRANSCRIBER_G_MINUTES, totalTranscriberMinutes.get());

        stats.put(TOTAL_TRANSCRIBER_STARTED, totalTrasnscriberStarted.get());
        stats.put(TOTAL_TRANSCRIBER_STOPPED, totalTrasnscriberStopped.get());
        stats.put(TOTAL_TRANSCRIBER_FAILED, totalTrasnscriberFailed.get());

        stats.put(TOTAL_TRANSCRIBER_CONNECTION_ERRORS, totalTrasnscriberConnectionErrors.get());
        stats.put(TOTAL_TRANSCRIBER_NO_RESUL_ERRORS, totalTrasnscriberNoResultErrors.get());
        stats.put(TOTAL_TRANSCRIBER_SEND_ERRORS, totalTrasnscriberSendErrors.get());
        stats.put(TOTAL_TRANSCRIBER_SESSION_CREATION_ERRORS, totalTrasnscriberSessionCreationErrors.get());

        stats.put(SHUTDOWN_IN_PROGRESS, shutdownMetric.get());

        response.setStatus(HttpServletResponse.SC_OK);
        new JSONObject(stats).writeJSONString(response.getWriter());
    }

    public static void updateMetrics()
    {
        threadsMetric.set(ManagementFactory.getThreadMXBean().getThreadCount());
        shutdownMetric.set(JigasiBundleActivator.isShutdownInProgress());

        // get sessions from all gateways
        List<AbstractGatewaySession> sessions = new ArrayList<>();
        JigasiBundleActivator.getAvailableGateways().forEach(gw -> sessions.addAll(gw.getActiveSessions()));

        int participants = 0;
        int conferences = 0;

        for (AbstractGatewaySession ses : sessions)
        {
            if (ses.getJvbChatRoom() == null)
            {
                continue;
            }

            // do not count focus
            int conferenceEndpoints = ses.getJvbChatRoom().getMembersCount() - 1;
            participants += conferenceEndpoints;
            conferences++;
        }

        double stressLevel = conferences / CONFERENCES_THRESHOLD;

        conferencesMetric.set(conferences);
        participantsMetric.set(participants);
        stressMetric.set(stressLevel);

    }

    /**
     * Counts conferences count, participants count and conference size
     * distributions.
     *
     * @return a map with the stats.
     */
    private static Map<String, Object> getSessionStats()
    {

        Map<String, Object> stats = new HashMap<>();
        stats.put(CONFERENCES, conferencesMetric.get());
        stats.put(PARTICIPANTS, participantsMetric.get());
        stats.put(STRESS_LEVEL, stressMetric.get());

        // Calculate the conference sizes separately because we don't have a metric for them. TODO: port to a metric.
        // get sessions from all gateways
        List<AbstractGatewaySession> sessions = new ArrayList<>();
        JigasiBundleActivator.getAvailableGateways().forEach(
            gw -> sessions.addAll(gw.getActiveSessions()));

        int[] conferenceSizes = new int[CONFERENCE_SIZE_BUCKETS];

        for (AbstractGatewaySession ses : sessions)
        {
            if (ses.getJvbChatRoom() == null)
            {
                continue;
            }

            // do not count focus
            int conferenceEndpoints
                = ses.getJvbChatRoom().getMembersCount() - 1;
            int idx
                = conferenceEndpoints < conferenceSizes.length
                ? conferenceEndpoints
                : conferenceSizes.length - 1;
            if (idx >= 0)
            {
                conferenceSizes[idx]++;
            }
        }


        JSONArray conferenceSizesJson = new JSONArray();
        for (int size : conferenceSizes)
        {
            conferenceSizesJson.add(size);
        }
        stats.put(CONFERENCE_SIZES, conferenceSizesJson);

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
        totalParticipantsCount.add(value);
    }

    /**
     * Adds the value to the total number of completed conferences.
     * @param value the value to add to the total number
     *              of completed conferences.
     */
    public static void addTotalConferencesCount(int value)
    {
        totalConferencesCount.add(value);
    }

    /**
     * Increment the value of total times with dropped media.
     */
    public static void incrementTotalMediaDropped()
    {
        totalMediaDroppedCount.inc();
    }

    /**
     * Increment the value of total number of calls with dropped media.
     */
    public static void incrementTotalCallsWithMediaDropped()
    {
        totalCallsWithMediaDroppedCount.inc();
    }

    /**
     * Increment the value of total number of calls with XMPP connection failed.
     */
    public static void incrementTotalCallsWithConnectionFailed()
    {
        totalCallsWithConnectionFailedCount.inc();
    }

    /**
     * Increment the value of total number of calls with XMPP calls terminated
     * and sip call waiting for new xmpp call to be connected.
     */
    public static void incrementTotalCallsWithSipCallWaiting()
    {
        totalCallsWithSipCallWaiting.inc();
    }

    /**
     * Increment the value of total number of calls with XMPP calls terminated
     * and new XMPP call been connected again with the SIP call
     */
    public static void incrementTotalCallsWithSipCallReconnected()
    {
        totalCallsWithSipCallReconnected.inc();
    }

    /**
     * Increment the value of total number of calls with XMPP calls terminated
     * and new XMPP call been connected again with the SIP call
     */
    public static void incrementTotalCallsWithJvbMigrate()
    {
        totalCallsWithJvbMigrate.inc();
    }

    /**
     * Increment the value of total number of calls with XMPP calls not receiving media from the bridge.
     */
    public static void incrementTotalCallsJvbNoMedia()
    {
        totalCallsJvbNoMedia.inc();
    }

    /**
     * Increment the value of total number of sip calls with no heartbeat.
     */
    public static void incrementTotalCallsWithNoSipHeartbeat()
    {
        totalCallsWithNoHeartBeatResponse.inc();
    }

    /**
     * Increment the value of total number of transcriber started.
     */
    public static void incrementTotalTranscriberStarted()
    {
        totalTrasnscriberStarted.inc();
    }

    /**
     * Increment the value of total number of transcriber stopped.
     */
    public static void incrementTotalTranscriberSopped()
    {
        totalTrasnscriberStopped.inc();
    }

    /**
     * Increment the value of total number of transcriber failes.
     */
    public static void incrementTotalTranscriberFailed()
    {
        totalTrasnscriberFailed.inc();
    }

    /**
     * Increment the value of total number of minute transcriber intervals.
     */
    public static void incrementTotalTranscriberMinutes(long value)
    {
        totalTranscriberMinutes.addAndGet(value);
    }

    /**
     * Increment the value of total number of transcriber request.
     */
    public static void incrementTotalTranscriberRequests()
    {
        totalTrasnscriberRequests.inc();
    }

    /**
     * Increment the value of total number of transcriber connection errors.
     */
    public static void incrementTotalTranscriberConnectionErrors()
    {
        totalTrasnscriberConnectionErrors.inc();
    }

    /**
     * Increment the value of total number of transcriber no result errors.
     */
    public static void incrementTotalTranscriberNoResultErrors()
    {
        totalTrasnscriberNoResultErrors.inc();
    }

    /**
     * Increment the value of total number of transcriber send errors.
     */
    public static void incrementTotalTranscriberSendErrors()
    {
        totalTrasnscriberSendErrors.inc();
    }

    /**
     * Increment the value of total number of transcriber session creation errors.
     */
    public static void incrementTotalTranscriberSessionCreationErrors()
    {
        totalTrasnscriberSessionCreationErrors.inc();
    }

    /**
     * Adds the value to the number of total conference seconds.
     * @param value the value to add to the number of total conference seconds.
     */
    public static void addCumulativeConferenceSeconds(long value)
    {
        cumulativeConferenceSeconds.add(value);
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
    public static void updatePresenceStatusForXmppProviders(List<ProtocolProviderService> ppss)
    {
        updateMetrics();

        ppss.forEach(Statistics::updatePresenceStatusForXmppProvider);
    }

    /**
     * Updates the presence in the given {@link ProtocolProviderService} with
     * the current number of conferences and participants.
     *
     * Adds a {@link ColibriStatsExtension} to our presence in the brewery room.
     *
     * @param pps the protocol provider service
     */
    private static void updatePresenceStatusForXmppProvider(ProtocolProviderService pps)
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
                    conferencesMetric.get()));
                stats.addStat(new ColibriStatsExtension.Stat(
                    PARTICIPANTS,
                    participantsMetric.get()));
                stats.addStat(new ColibriStatsExtension.Stat(
                    SHUTDOWN_IN_PROGRESS,
                    shutdownMetric.get()));
                stats.addStat((new ColibriStatsExtension.Stat(
                    STRESS_LEVEL,
                    stressMetric.get()
                )));

                String region = JigasiBundleActivator.getConfigurationService()
                    .getString(LOCAL_REGION_PNAME);
                if (StringUtils.isNotEmpty(StringUtils.trim(region)))
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

                threadPool.execute(
                    () -> pps.getOperationSet(OperationSetJitsiMeetToolsJabber.class)
                            .sendPresenceExtension(mucRoom, stats));
            }
            catch (Exception e)
            {
                logger.error("Error updating presence for:" + pps, e);
            }
        }
    }
}
