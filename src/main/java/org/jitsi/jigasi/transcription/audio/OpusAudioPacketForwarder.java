package org.jitsi.jigasi.transcription.audio;

import org.jitsi.impl.neomedia.*;
import org.jitsi.impl.neomedia.transform.*;
import org.jitsi.service.neomedia.*;

public class OpusAudioPacketForwarder
    extends AbstractForwarder
{
    private final static int DEFAULT_SAMPLE_RATE = 48000;

    private InternalTransformerAdapter adapter
        = new InternalTransformerAdapter();

    public InternalTransformerAdapter getAdapter()
    {
        return adapter;
    }

    public class InternalTransformerAdapter
        extends SinglePacketTransformerAdapter
        implements TransformEngine
    {
        private InternalTransformerAdapter()
        {
            super(RTPPacketPredicate.INSTANCE);
        }

        @Override
        public RawPacket transform(RawPacket pkt)
        {
            // This handles packets on the egress side (that jigasi sends),
            // so we do not need to do anything with it
            return pkt;
        }

        @Override
        public RawPacket reverseTransform(RawPacket pkt)
        {
            // This handles packets on the egress side (that jigasi sends),
            // so we forward the information to the transcriber before
            // returning it without any modification
            forwardPacket(pkt);

            return pkt;
        }

        private void forwardPacket(RawPacket pkt)
        {
            long ssrc = pkt.getSSRCAsLong();
            byte[] audio = pkt.getBuffer();

            AudioFormat format
                = new AudioFormat(AudioFormat.OPUS_ENCODING, 48000);

            AudioSegment segment = new AudioSegment(ssrc, audio, format);

            OpusAudioPacketForwarder.this.processSegment(segment);
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
            return null;
        }
    }


}