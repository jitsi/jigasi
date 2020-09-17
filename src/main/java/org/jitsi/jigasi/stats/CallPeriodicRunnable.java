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

import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.protocol.media.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.service.neomedia.stats.*;
import org.jitsi.stats.media.*;
import org.jitsi.utils.*;
import org.jxmpp.jid.*;

import java.util.*;

/**
 * Implements a {@link AbstractStatsPeriodicRunnable} which periodically
 * generates a statistics for the conference channels.
 *
 * @author Damian Minkov
 */
public class CallPeriodicRunnable
    extends AbstractStatsPeriodicRunnable<Call>
{
    /**
     * The default remote id to use. As jigasi is connecting to jvb
     * it reports it as remote participant.
     */
    private final String remoteID;

    /**
     * Initializes a new {@code CallPeriodicRunnable} instance
     * which is to {@code period}ically generate statistics for the
     * call peers.
     *
     * @param call the {@code Calls}'s channels to be
     * {@code period}ically checked for statistics by the new instance
     * @param period the time in milliseconds between consecutive
     * generations of statistics
     */
    CallPeriodicRunnable(
        Call call,
        long period,
        StatsService statsService,
        EntityBareJid conferenceName,
        String conferenceIDPrefix,
        String initiatorID,
        String remoteID)
    {
        super(
            call,
            period,
            statsService,
            conferenceName,
            conferenceIDPrefix,
            initiatorID);

        this.remoteID = remoteID;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    protected List<EndpointStats> getEndpointStats()
    {
        List<MediaStreamStats2> mediaStreamStatsList = new LinkedList<>();

        Iterator<? extends CallPeer> iter = o.getCallPeers();
        while (iter.hasNext())
        {
            MediaAwareCallPeer peer = (MediaAwareCallPeer) iter.next();

            MediaStream stream
                = peer.getMediaHandler().getStream(MediaType.AUDIO);

            if (stream == null)
            {
                continue;
            }

            MediaStreamStats2 peerMediaStreamStats
                    = stream.getMediaStreamStats();
            if (peerMediaStreamStats != null)
            {
                mediaStreamStatsList.add(peerMediaStreamStats);
            }
        }

        EndpointStats endpointStats = new EndpointStats(remoteID);

        mediaStreamStatsList.forEach(mediaStreamStats ->
        {
            mediaStreamStats.getAllReceiveStats().forEach(stats ->
            {
                SsrcStats receiveStats = new SsrcStats();
                receiveStats.ssrc = stats.getSSRC();
                receiveStats.bytes = stats.getBytes();
                receiveStats.packets = stats.getPackets();
                receiveStats.packetsLost = stats.getPacketsLost();
                receiveStats.fractionalPacketLoss = stats.getLossRate();

                if (stats.getJitter() != TrackStats.JITTER_UNSET)
                {
                    receiveStats.jitter_ms = stats.getJitter();
                }

                if (stats.getRtt() != -1)
                {
                    receiveStats.rtt_ms = (int) stats.getRtt();
                }

                endpointStats.addReceiveStats(receiveStats);
            });
            mediaStreamStats.getAllSendStats().forEach(stats ->
            {
                SsrcStats sendStats = new SsrcStats();
                sendStats.ssrc = stats.getSSRC();
                sendStats.bytes = stats.getBytes();
                sendStats.packets = stats.getPackets();
                //sendStats.packetsLost = stats.getPacketsLost();
                sendStats.fractionalPacketLoss = stats.getLossRate();

                if (stats.getJitter() != TrackStats.JITTER_UNSET)
                {
                    sendStats.jitter_ms = stats.getJitter();
                }

                if (stats.getRtt() != -1)
                {
                    sendStats.rtt_ms = (int) stats.getRtt();
                }

                endpointStats.addSendStats(sendStats);
            });
        });

        return Collections.singletonList(endpointStats);
    }
}
