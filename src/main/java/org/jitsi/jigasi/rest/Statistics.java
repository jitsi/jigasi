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
package org.jitsi.jigasi.rest;

import java.io.*;
import java.lang.management.*;
import java.text.*;
import java.util.*;

import javax.servlet.*;
import javax.servlet.http.*;

import org.eclipse.jetty.server.*;
import org.jitsi.jigasi.*;
import org.json.simple.*;

/**
 * Implements statistics that are collected by the JIgasi.
 * @author Damian Minkov
 * @author Nik Vaessen
 */
public class Statistics
{
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
     * Total number of participants since started.
     */
    private static int totalParticipantsCount = 0;

    /**
     * Total number of conferences since started.
     */
    private static int totalConferencesCount = 0;

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
     * @param gateway the {@code SipGateway} to get the stats of
     * in the form of a JSON representation
     * @param baseRequest the original unwrapped {@link Request} object
     * @param request the request either as the {@code Request} object or a
     * wrapper of that request
     * @param response the response either as the {@code Response} object or a
     * wrapper of that response
     */
    static synchronized void getJSON(
        SipGateway gateway,
        Request baseRequest,
        HttpServletRequest request,
        HttpServletResponse response)
        throws IOException,
        ServletException
    {
        int[] conferenceSizes = new int[CONFERENCE_SIZE_BUCKETS];
        Map<String,Object> stats = new HashMap<>();

        stats.put(CONFERENCES, gateway.getActiveSessions().size());
        int participants = 0;
        for(SipGatewaySession ses : gateway.getActiveSessions())
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
            conferenceSizes[idx]++;
        }

        // NUMBEROFPARTICIPANTS
        stats.put(NUMBEROFPARTICIPANTS, participants);

        // NUMBEROFTHREADS
        stats.put(NUMBEROFTHREADS,
            ManagementFactory.getThreadMXBean().getThreadCount());

        // TIMESTAMP
        stats.put(TIMESTAMP, currentTimeMillis());

        // TOTAL stats
        stats.put(TOTAL_CONFERENCES, totalConferencesCount);
        stats.put(TOTAL_NUMBEROFPARTICIPANTS, totalParticipantsCount);
        stats.put(TOTAL_CONFERENCE_SECONDS, cumulativeConferenceSeconds);

        // CONFERENCE_SIZES
        JSONArray conferenceSizesJson = new JSONArray();
        for (int size : conferenceSizes)
            conferenceSizesJson.add(size);
        stats.put(CONFERENCE_SIZES, conferenceSizesJson);

        stats.put(SHUTDOWN_IN_PROGRESS, gateway.isShutdownInProgress());

        response.setStatus(HttpServletResponse.SC_OK);
        new JSONObject(stats).writeJSONString(response.getWriter());
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
    static void addTotalParticipantsCount(int value)
    {
        totalParticipantsCount += value;
    }

    /**
     * Adds the value to the total number of completed conferences.
     * @param value the value to add to the total number
     *              of completed conferences.
     */
    static void addTotalConferencesCount(int value)
    {
        totalConferencesCount += value;
    }

    /**
     * Adds the value to the number of total conference seconds.
     * @param value the value to add to the number of total conference seconds.
     */
    static void addCumulativeConferenceSeconds(long value)
    {
        cumulativeConferenceSeconds += value;
    }
}
