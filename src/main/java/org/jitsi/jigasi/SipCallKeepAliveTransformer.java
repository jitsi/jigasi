/*
 * Copyright @ 2018 - present 8x8, Inc.
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
 *
 */
package org.jitsi.jigasi;

import net.java.sip.communicator.service.protocol.media.*;
import org.jitsi.impl.neomedia.*;
import org.jitsi.impl.neomedia.rtcp.*;
import org.jitsi.impl.neomedia.transform.*;
import org.jitsi.jigasi.util.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.util.*;
import org.jitsi.utils.*;
import org.jitsi.utils.concurrent.*;

import java.util.*;

/**
 * Monitors outgoing RTCP and passes only those which are relevant for that
 * direction. Filters outgoing RTCP.BYEs for ssrc that we haven't seen. Also
 * monitors last seen media or rtcp and if one is missing we
 * send hole punch packets. It can happen that the xmpp call is dropped and
 * the sip call participant is waiting then there will be no media ot RTCP to
 * keep alive the stream from the other side.
 *
 * @author Damian Minkov
 */
public class SipCallKeepAliveTransformer
    extends SinglePacketTransformerAdapter
    implements TransformEngine
{
    /**
     * The RTCP transformer.
     */
    private final RTCPTransformer rtcpTransformer = new RTCPTransformer();

    /**
     * List of seen incoming or outgoing ssrcs.
     */
    private Set<Long> seenSSRCs = Collections.synchronizedSet(new HashSet<>());

    /**
     * The executor which periodically calls {@link KeepAliveIncomingMedia}.
     */
    private static final RecurringRunnableExecutor EXECUTOR
        = new RecurringRunnableExecutor(KeepAliveIncomingMedia.class.getName());

    /**
     * The runner that checks media.
     */
    private KeepAliveIncomingMedia recurringMediaChecker
        = new KeepAliveIncomingMedia(15000);

    private long lastOutgoingActivity;

    /**
     * The peer handler for which we are adding this transformer.
     */
    private final CallPeerMediaHandler handler;

    /**
     * The stream for this transformer.
     */
    private final MediaStream stream;

    /**
     * Initializes a new {@link SsrcRewriter} instance.
     */
    public SipCallKeepAliveTransformer(
        CallPeerMediaHandler handler, MediaStream stream)
    {
        super(RTPPacketPredicate.INSTANCE);

        this.handler = handler;
        this.stream = stream;

        EXECUTOR.registerRecurringRunnable(recurringMediaChecker);
    }

    /**
     * Disposes, clears the executor and its task.
     */
    void dispose()
    {
        EXECUTOR.deRegisterRecurringRunnable(recurringMediaChecker);
    }

    /**
     * Marks the ssrc and time of last seen packet.
     *
     * @param pkt the packet.
     * @return the original packet.
     */
    @Override
    public RawPacket transform(RawPacket pkt)
    {
        lastOutgoingActivity = System.currentTimeMillis();
        seenSSRCs.add(pkt.getSSRCAsLong());

        return pkt;
    }

    @Override
    public RawPacket reverseTransform(RawPacket pkt)
    {
        seenSSRCs.add(pkt.getSSRCAsLong());

        return pkt;
    }

    /**
     * Implements {@link TransformEngine#getRTPTransformer()}.
     */
    @Override
    public PacketTransformer getRTPTransformer()
    {
        return this;
    }

    /**
     * Implements {@link TransformEngine#getRTCPTransformer()}.
     * <p>
     * This <tt>TransformEngine</tt> does not transform RTCP packets.
     */
    @Override
    public PacketTransformer getRTCPTransformer()
    {
        return rtcpTransformer;
    }

    /**
     * Checks and filters RTCP.BYE.
     */
    public class RTCPTransformer
        extends SinglePacketTransformerAdapter
    {
        RTCPTransformer()
        {
            super(RTCPPacketPredicate.INSTANCE);
        }

        @Override
        public RawPacket transform(RawPacket pkt)
        {
            RTCPIterator it = new RTCPIterator(pkt);
            while (it.hasNext())
            {
                ByteArrayBuffer baf = it.next();
                int type = RTCPUtils.getPacketType(baf);
                long ssrc = RawPacket.getRTCPSSRC(baf);

                // Filter RTCP.BYE for streams we don't know about
                if (!seenSSRCs.contains(ssrc) && type == 203)
                {
                    it.remove();
                }
            }

            if (pkt.getLength() > 0)
            {
                lastOutgoingActivity = System.currentTimeMillis();
                return pkt;
            }

            return null;
        }
    }

    /**
     * Called periodically to check when was the last outgoing RTP or RTCP,
     * if it was longer than a threshold we send hole punch packets to keep
     * media, as some providers can stop media till they see something coming.
     * In this case all web participants can be muted and the bridge will not be
     * forwarding any media so we can stop sending any media to the sip side
     * and the incoming media can stop and participants will stop hearing that
     * side.
     */
    private class KeepAliveIncomingMedia
        extends PeriodicRunnable
    {
        /**
         * The timestamp of the keepalive packets.
         */
        private long ts = new Random().nextInt() & 0xFFFFFFFFL;

        /**
         * The sequence number of the keepalive packets.
         */
        private int seqNum = new Random().nextInt(0xFFFF);

        /**
         * In case of 20 seconds of no media we want to send few
         * rtp packets to keep it alive.
         */
        private long NO_MEDIA_THRESHOLD = 20000;

        public KeepAliveIncomingMedia(long period)
        {
            super(period);
        }

        @Override
        public void run()
        {
            super.run();

            try
            {
                // if there was no activity for a period 5secs less the
                // period we check
                if (System.currentTimeMillis() - lastOutgoingActivity
                        > NO_MEDIA_THRESHOLD)
                {
                    // we may want to send more than one packet
                    // in case they get lost
                    for(int i=0; i < 3; i++)
                    {
                        RawPacket packet = Util.makeRTP(
                            stream.getLocalSourceID(),
                            13/* comfort noise payload type */,
                            seqNum++,
                            ts,
                            RawPacket.FIXED_HEADER_SIZE + 1);

                        stream.injectPacket(
                            packet, true, SipCallKeepAliveTransformer.this);

                        ts += 160;
                    }
                    lastOutgoingActivity = System.currentTimeMillis();
                }
            }
            catch(Throwable e)
            {}
        }
    }


}
