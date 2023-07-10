package org.jitsi.jigasi.transcription;

import java.io.IOException;
import java.util.Arrays;
import javax.media.Buffer;
import javax.media.Format;
import javax.media.control.FormatControl;
import javax.media.format.AudioFormat;
import javax.media.protocol.BufferTransferHandler;
import org.jitsi.impl.neomedia.codec.AbstractCodec2;
import org.jitsi.impl.neomedia.jmfext.media.protocol.AbstractPushBufferCaptureDevice;
import org.jitsi.impl.neomedia.jmfext.media.protocol.AbstractPushBufferStream;
import org.jitsi.impl.neomedia.jmfext.media.renderer.audio.AbstractAudioRenderer;
import org.jitsi.utils.logging.Logger;

class WhisperTsAudioSilenceCaptureDevice extends AbstractPushBufferCaptureDevice {
    private static final long CLOCK_TICK_INTERVAL = 20L;
    private static final Format[] SUPPORTED_FORMATS;
    private final boolean clockOnly;

    private final static Logger logger
            = Logger.getLogger(WhisperTsAudioSilenceCaptureDevice.class);

    WhisperTsAudioSilenceCaptureDevice(boolean clockOnly) {
        this.clockOnly = clockOnly;
    }

    protected AudioSilenceStream createStream(int streamIndex, FormatControl formatControl) {
        return new AudioSilenceStream(this, formatControl, this.clockOnly);
    }

    protected Format[] getSupportedFormats(int streamIndex) {
        return (Format[])SUPPORTED_FORMATS.clone();
    }

    static {
        SUPPORTED_FORMATS = new Format[]{
                new AudioFormat(
                        "LINEAR",
                        16000.0,
                        16,
                        1,
                        0,
                        1,
                        -1,
                        -1.0,
                        Format.byteArray
                )};
    }

    private static class AudioSilenceStream
            extends AbstractPushBufferStream<WhisperTsAudioSilenceCaptureDevice> implements Runnable {
        private boolean started;
        private Thread thread;
        private final boolean clockOnly;

        public AudioSilenceStream(
                WhisperTsAudioSilenceCaptureDevice dataSource,
                FormatControl formatControl,
                boolean clockOnly) {
            super(dataSource, formatControl);
            this.clockOnly = clockOnly;
        }

        public void read(Buffer buffer) throws IOException {
            if (this.clockOnly)
            {
                buffer.setLength(0);
            }
            else
            {
                AudioFormat format = (AudioFormat)this.getFormat();
                int frameSizeInBytes = format.getChannels() *
                        ((int)format.getSampleRate() / 50) *
                        (format.getSampleSizeInBits() / 8);
                byte[] data = AbstractCodec2.validateByteArraySize(buffer, frameSizeInBytes, false);
                Arrays.fill(data, 0, frameSizeInBytes, (byte)0);
                buffer.setFormat(format);
                buffer.setLength(frameSizeInBytes);
                buffer.setOffset(0);
            }

        }

        public void run()
        {
            boolean var18 = false;

            try
            {
                var18 = true;
                AbstractAudioRenderer.useAudioThreadPriority();
                long tickTime = System.currentTimeMillis();

                while (true)
                {
                    long sleepInterval = tickTime - System.currentTimeMillis();
                    boolean tick = sleepInterval <= 0L;
                    if (tick)
                    {
                        tickTime += 20L;
                    }
                    else
                    {
                        try
                        {
                            Thread.sleep(sleepInterval);
                        }
                        catch (InterruptedException var21)
                        {
                            logger.debug(var21.toString());
                        }
                    }

                    synchronized(this)
                    {
                        if (this.thread != Thread.currentThread() || !this.started)
                        {
                            var18 = false;
                            break;
                        }
                    }

                    if (tick)
                    {
                        BufferTransferHandler transferHandler = this.transferHandler;
                        if (transferHandler != null)
                        {
                            try
                            {
                                transferHandler.transferData(this);
                            }
                            catch (Throwable var22)
                            {
                                if (var22 instanceof ThreadDeath)
                                {
                                    throw (ThreadDeath)var22;
                                }
                            }
                        }
                    }
                }
            }
            finally
            {
                if (var18)
                {
                    synchronized(this)
                    {
                        if (this.thread == Thread.currentThread())
                        {
                            this.thread = null;
                            this.started = false;
                            this.notifyAll();
                        }
                    }
                }
            }

            synchronized(this)
            {
                if (this.thread == Thread.currentThread())
                {
                    this.thread = null;
                    this.started = false;
                    this.notifyAll();
                }

            }
        }

        public synchronized void start() throws IOException
        {
            if (this.thread == null)
            {
                String className = this.getClass().getName();
                this.thread = new Thread(this, className);
                this.thread.setDaemon(true);
                boolean started = false;

                try
                {
                    this.thread.start();
                    started = true;
                }
                finally
                {
                    this.started = started;
                    if (!started)
                    {
                        this.thread = null;
                        this.notifyAll();
                        throw new IOException("Failed to start " + className);
                    }

                }
            }

        }

        public synchronized void stop() throws IOException {
            this.started = false;
            this.notifyAll();
            boolean interrupted = false;

            while (this.thread != null)
            {
                try
                {
                    this.wait();
                }
                catch (InterruptedException var3)
                {
                    interrupted = true;
                }
            }

            if (interrupted)
            {
                Thread.currentThread().interrupt();
            }
        }
    }
}
