/*
 * Jigasi, the JItsi GAteway to SIP.
 *
 * Copyright @ 2017 Atlassian Pty Ltd
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

import org.jitsi.impl.neomedia.device.*;

import javax.media.*;
import java.io.*;

public class TranscribingAudioMixerMediaDeviceFromFile
    extends AudioMixerMediaDevice
{

    ReceiveStreamBufferListener listener;
    File file;
    Thread readingThread;

    /**
     * Create a new MediaDevice which does not output any audio
     * and has a listener for all other audio
     */
    public TranscribingAudioMixerMediaDeviceFromFile(
        ReceiveStreamBufferListener listener, File f)
    {
        super(new AudioSilenceMediaDevice());
        this.listener = listener;

        FileInputStream fis;
        try
        {
            fis = new FileInputStream(f);
        }
        catch (FileNotFoundException e)
        {
            fis = null;
        }
        final FileInputStream ffis = fis;

        readingThread = new Thread()
        {
            @Override
            public void run()
            {
                readFromFile(listener, ffis);
            }
        };
        readingThread.start();
    }

    private void readFromFile(ReceiveStreamBufferListener listener, FileInputStream f)
    {
        long lastTs = -1;
        while (true)
        {
            // 64bit ts
            // 32bit SSRC
            // 32bit length
            // "length" bytes of data (1920?)
            long ts = readLong(f);

            if (lastTs == -1)
            {
                lastTs = ts;
            }
            else
            {
                try
                {
                    Thread.sleep(ts - lastTs);
                }
                catch (InterruptedException e)
                {
                    e.printStackTrace();
                }

                lastTs = ts;
            }



            long ssrc = readInt(f) & 0xffff_ffffL;
            int len = readInt(f);
            if (len != 1920)
            {
                System.err.println("weird length: "+len);
            }
            byte[] b = new byte[len];
            try
            {
                f.read(b);
            }
            catch (IOException e)
            {
                System.err.println("Failed to read from file, ");
                e.printStackTrace();
            }

            Buffer buf = new Buffer();
            buf.setData(b);
            buf.setLength(len);
            buf.setOffset(0);
            //TODO set format
            ((Transcriber) listener).bufferReceived(ssrc, buf);
        }
    }

    private int readInt(FileInputStream f)
    {
        try
        {
            int b1 = f.read();
            int b2 = f.read();
            int b3 = f.read();
            int b4 = f.read();
            return ((b1 & 0xFF) << 24)
                | ((b2 & 0xFF) << 16)
                | ((b3 & 0xFF) << 8)
                | (b4 & 0xFF);
        }
        catch (IOException e)
        {
            System.err.println("Failed to read from file, ");
            e.printStackTrace();
            return -1;
        }
    }

    private long readLong(FileInputStream f)
    {
        try
        {
            long b1 = f.read();
            long b2 = f.read();
            long b3 = f.read();
            long b4 = f.read();
            long b5 = f.read();
            long b6 = f.read();
            long b7 = f.read();
            long b8 = f.read();
            return ((b1 & 0xFF) << 56)
                | ((b2 & 0xFF) << 48)
                | ((b3 & 0xFF) << 40)
                | ((b4 & 0xFF) << 32)
                | ((b5 & 0xFF) << 24)
                | ((b6 & 0xFF) << 16)
                | ((b7 & 0xFF) << 8)
                | (b8 & 0xFF);
        }
        catch (IOException e)
        {
            System.err.println("Failed to read from file, ");
            e.printStackTrace();
            return -1;
        }
    }
}
