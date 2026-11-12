/*
 * Copyright @ 2026 - present, 8x8 Inc
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

import static org.junit.jupiter.api.Assertions.*;

import org.jitsi.jigasi.util.*;
import org.jitsi.service.neomedia.*;
import org.junit.jupiter.api.*;

/**
 * Tests which outgoing packets count as activity for the keep-alive of
 * {@link SipCallTransformer}.
 */
public class SipCallTransformerTest
{
    private SipCallTransformer transformer;

    @BeforeEach
    public void setUp()
    {
        transformer = new SipCallTransformer(null, null);
    }

    @AfterEach
    public void tearDown()
    {
        transformer.dispose();
    }

    /**
     * Outgoing RTCP can use a different port than RTP, so it must not count as
     * activity, or the RTP flow is never kept alive.
     */
    @Test
    public void outgoingRtcpDoesNotCountAsActivity()
    {
        RawPacket receiverReport = new RawPacket(new byte[] {
            (byte) 0x80, (byte) 201, 0, 1, // V=2, RR, length 1
            0x11, 0x22, 0x33, 0x44 // sender SSRC
        }, 0, 8);

        assertNotNull(transformer.getRTCPTransformer().transform(new RawPacket[] { receiverReport })[0]);
        assertEquals(0, transformer.getLastOutgoingActivity());
    }

    @Test
    public void outgoingRtpCountsAsActivity()
    {
        RawPacket rtp = Util.makeRTP(0x11223344L, 111, 1, 960, 40);

        assertNotNull(transformer.getRTPTransformer().transform(new RawPacket[] { rtp })[0]);
        assertTrue(transformer.getLastOutgoingActivity() > 0);
    }
}
