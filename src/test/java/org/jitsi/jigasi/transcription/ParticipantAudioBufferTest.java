/*
 * Jigasi, the JItsi GAteway to SIP.
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
package org.jitsi.jigasi.transcription;

import static org.junit.jupiter.api.Assertions.*;

import javax.media.format.*;
import java.util.*;
import org.junit.jupiter.api.*;

/** Tests the per-participant audio buffer. */
public class ParticipantAudioBufferTest
{
    private static final AudioFormat PCM_16K_MONO
        = new AudioFormat(AudioFormat.LINEAR, 16000, 16, 1);

    @Test
    public void testCapacityUsesAudioFormat()
    {
        assertEquals(3200,
            ParticipantAudioBuffer.capacity(PCM_16K_MONO, 640, 100));
        assertEquals(19200,
            ParticipantAudioBuffer.capacity(
                new AudioFormat(AudioFormat.LINEAR, 48000, 16, 1), 1920, 200));
    }

    @Test
    public void testAddSendsOnlyFilledBytes()
    {
        List<byte[]> sent = new ArrayList<>();
        ParticipantAudioBuffer buffer
            = new ParticipantAudioBuffer(100, sent::add);

        buffer.add(PCM_16K_MONO, new byte[1600]);
        assertTrue(sent.isEmpty());

        buffer.add(PCM_16K_MONO, new byte[1600]);
        assertEquals(1, sent.size());
        assertEquals(3200, sent.get(0).length);
        assertEquals(0, buffer.pending());
    }

    @Test
    public void testFlushSendsOnlyPendingBytesOnce()
    {
        List<byte[]> sent = new ArrayList<>();
        ParticipantAudioBuffer buffer
            = new ParticipantAudioBuffer(100, sent::add);
        byte[] first = new byte[] { 1, 2, 3 };

        buffer.add(PCM_16K_MONO, first);
        buffer.flush();
        buffer.flush();

        assertEquals(1, sent.size());
        assertArrayEquals(first, sent.get(0));
        first[0] = 9;
        assertEquals(1, sent.get(0)[0]);
    }

    @Test
    public void testZeroDurationSendsCopiedPacketsImmediately()
    {
        List<byte[]> sent = new ArrayList<>();
        ParticipantAudioBuffer buffer
            = new ParticipantAudioBuffer(0, sent::add);
        byte[] packet = new byte[] { 1, 2, 3 };

        buffer.add(PCM_16K_MONO, packet);
        packet[0] = 9;

        assertEquals(1, sent.size());
        assertArrayEquals(new byte[] { 1, 2, 3 }, sent.get(0));
    }

    @Test
    public void testPacketBytesCopiesOnlyValidRegion()
    {
        javax.media.Buffer packet = new javax.media.Buffer();
        byte[] data = new byte[] { 0, 1, 2, 3, 0 };
        packet.setData(data);
        packet.setOffset(1);
        packet.setLength(3);

        byte[] copied = Participant.packetBytes(packet);
        data[1] = 9;

        assertArrayEquals(new byte[] { 1, 2, 3 }, copied);
        assertNull(Participant.packetBytes(new javax.media.Buffer()));
    }
}
