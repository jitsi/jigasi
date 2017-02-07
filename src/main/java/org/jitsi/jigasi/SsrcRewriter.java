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
package org.jitsi.jigasi;

import org.jitsi.impl.neomedia.*;
import org.jitsi.impl.neomedia.transform.*;
import org.jitsi.service.neomedia.*;

/**
 * Transformer that will rewrite rtp packets ssrc, when attached to a stream.
 *
 * @author Damian Minkov
 */
public class SsrcRewriter
    extends SinglePacketTransformerAdapter
    implements TransformEngine
{
    private final RTCPTransformer rtcpTransformer = new RTCPTransformer();

    private final long ssrc;

    /**
     * Initializes a new {@link SsrcRewriter} instance.
     */
    public SsrcRewriter(long ssrc)
    {
        super(RTPPacketPredicate.INSTANCE);
        this.ssrc = ssrc;
    }

    /**
     * Do the rewriting.
     * @param pkt
     * @return
     */
    @Override
    public RawPacket transform(RawPacket pkt)
    {
        if (pkt.getLength() >= 12)
            pkt.setSSRC((int)this.ssrc);
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
     *
     * This <tt>TransformEngine</tt> does not transform RTCP packets.
     *
     */
    @Override
    public PacketTransformer getRTCPTransformer()
    {
        return rtcpTransformer;
    }

    /**
     * Rewrites rtcp ssrc.
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
            if (pkt.getLength() >= 8)
                pkt.writeInt(4, (int)SsrcRewriter.this.ssrc);
            return pkt;
        }
    }
}
