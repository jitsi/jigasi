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

import javax.media.format.*;
import java.util.*;
import java.util.function.*;

/**
 * Buffers a participant's audio until the configured duration has been
 * collected. The capacity is calculated from the audio format, and only
 * written bytes are sent to the transcription service.
 * <p>
 * Not thread safe: {@link Participant} calls it on the transcriber's
 * single-threaded executor only.
 */
final class ParticipantAudioBuffer
{
    /**
     * Milliseconds of audio to collect per speaker before sending; 0 sends
     * every packet as it arrives.
     */
    static final String P_NAME_BUFFER_MS
        = "org.jitsi.jigasi.transcription.AUDIO_BUFFER_MS";

    static final int DEFAULT_BUFFER_MS = 500;

    static final int MAX_BUFFER_MS = 2000;

    /**
     * Packets are 20 ms; used to size the buffer when the format does not
     * specify its rate, channels or sample size.
     */
    private static final int PACKET_MS = 20;

    private final int bufferMs;

    private final Consumer<byte[]> sink;

    private byte[] data;

    private int position;

    ParticipantAudioBuffer(int bufferMs, Consumer<byte[]> sink)
    {
        this.bufferMs = Math.max(0, Math.min(MAX_BUFFER_MS, bufferMs));
        this.sink = sink;
    }

    /**
     * Bytes per millisecond of the given format, or 0 when it is unknown.
     */
    static int bytesPerMillisecond(AudioFormat format)
    {
        if (format == null)
        {
            return 0;
        }
        double rate = format.getSampleRate();
        int bits = format.getSampleSizeInBits();
        int channels = format.getChannels();
        if (rate <= 0 || bits <= 0 || bits % 8 != 0 || channels <= 0)
        {
            return 0;
        }
        double perMs = rate * channels * (bits / 8) / 1000.0;
        return perMs == Math.rint(perMs) ? (int) perMs : 0;
    }

    /**
     * Buffer capacity in bytes for the given format and first packet length.
     */
    static int capacity(AudioFormat format, int firstPacketLength, int ms)
    {
        int perMs = bytesPerMillisecond(format);
        if (perMs == 0)
        {
            perMs = Math.max(1, firstPacketLength / PACKET_MS);
        }
        return Math.max(1, perMs * ms);
    }

    /**
     * Number of bytes waiting to be sent.
     */
    int pending()
    {
        return position;
    }

    void add(AudioFormat format, byte[] audio)
    {
        if (audio == null || audio.length == 0)
        {
            return;
        }
        if (bufferMs == 0)
        {
            sink.accept(audio.clone());
            return;
        }
        if (data == null)
        {
            data = new byte[capacity(format, audio.length, bufferMs)];
        }
        if (position + audio.length > data.length)
        {
            flush();
        }
        if (audio.length >= data.length)
        {
            sink.accept(audio.clone());
            return;
        }
        System.arraycopy(audio, 0, data, position, audio.length);
        position += audio.length;
        if (position >= data.length)
        {
            flush();
        }
    }

    /**
     * Sends the written bytes, if any, and empties the buffer.
     */
    void flush()
    {
        if (position == 0)
        {
            return;
        }
        byte[] filled = Arrays.copyOf(data, position);
        position = 0;
        sink.accept(filled);
    }

    /**
     * Drops the written bytes without sending them.
     */
    void clear()
    {
        position = 0;
    }
}
