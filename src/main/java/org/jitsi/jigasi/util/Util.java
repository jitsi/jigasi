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
package org.jitsi.jigasi.util;

import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.protocol.media.*;
import net.java.sip.communicator.util.*;
import org.gagravarr.ogg.*;
import org.gagravarr.opus.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.service.neomedia.codec.*;
import org.jitsi.service.neomedia.format.*;

/**
 * Various utility methods.
 *
 * @author Pawel Domas
 */
public class Util
{
    /**
     * Returns <tt>MediaFormat</tt> of the first {@link CallPeer} that belongs
     * to given {@link Call}(if peer and formats are available).
     *
     * @param call the {@link Call} to which the call peer for whom we want to
     *             retrieve the media format belongs to.
     */
    public static MediaFormat getFirstPeerMediaFormat(Call call)
    {
        if (!(call instanceof MediaAwareCall))
            return null;

        MediaAwareCall mediaCall = (MediaAwareCall) call;
        if (mediaCall.getCallPeerCount() == 0)
            return null;

        CallPeer peer
            = (CallPeer) mediaCall.getCallPeerList().get(0);
        if (!(peer instanceof MediaAwareCallPeer))
            return null;

        MediaAwareCallPeer mediaPeer
            = (MediaAwareCallPeer) peer;

        CallPeerMediaHandler peerMediaHndl
            = mediaPeer.getMediaHandler();
        if (peerMediaHndl == null)
            return null;

        MediaStream peerStream
            = peerMediaHndl.getStream(MediaType.AUDIO);

        if (peerStream == null)
            return null;

        return peerStream.getFormat();
    }

    /**
     * Call resource currently has the form of e23gr547@callcontro.server.net.
     * This methods extract random call id part before '@' sign. In the example
     * above it is 'e23gr547'.
     * @param callResource the call resource/URI from which the call ID part
     *                     will be extracted.
     * @return extracted random call ID part from full call resource string.
     */
    public static String extractCallIdFromResource(String callResource)
    {
        return callResource.substring(0, callResource.indexOf("@"));
    }

    /**
     * Injects as sound file in a call's <tt>MediaStream</tt> using injectPacket
     * method and constructing RTP packets for it.
     * Supports opus only (when using translator mode calls from the jitsi-meet
     * side all of the are using opus and are just translated to the sip side).
     *
     * The file will be played if possible, if there is call passed and that
     * call has call peers of type MediaAwareCallPeer with media handler that
     * has MediaStream for Audio.
     *
     * @param call the call (sip one) to inject the sound as rtp.
     * @param fileName the file name to play.
     */
    public static void injectSoundFile(Call call, String fileName)
    {
        try
        {
            injectSoundFileInternal(call, fileName);
        }
        catch (Throwable t)
        {
            Logger.getLogger(Util.class).error("Error playing:" + fileName, t);
        }
    }

    /**
     * The internal implementation of injectSoundFile.
     * @param call the call (sip one) to inject the sound as rtp.
     * @param fileName the file name to play.
     * @throws Throwable cannot read source sound file or cannot transmit it.
     */
    private static void injectSoundFileInternal(Call call, String fileName)
        throws Throwable
    {
        MediaStream stream = null;

        CallPeer peer;
        if (call != null
            && call.getCallPeers() != null
            && (peer = call.getCallPeers().next()) != null
            && peer instanceof MediaAwareCallPeer)
        {
            MediaAwareCallPeer peerMedia = (MediaAwareCallPeer) peer;

            CallPeerMediaHandler mediaHandler
                = peerMedia.getMediaHandler();
            if (mediaHandler != null)
            {
                stream = mediaHandler.getStream(MediaType.AUDIO);
            }
        }

        // if there is no stream or the calling account is not using translator
        if (stream == null
            || !call.getProtocolProvider()
                    .getAccountID().getAccountPropertyBoolean(
                        ProtocolProviderFactory.USE_TRANSLATOR_IN_CONFERENCE,
                        false))
        {
            return;
        }

        OpusFile of = new OpusFile(new OggPacketReader(
            Util.class.getClassLoader().getResourceAsStream(fileName)));

        OpusAudioData opusAudioData;
        int seq = 0;
        // Random timestamp and ssrc
        long ts = System.currentTimeMillis()/1000;
        long ssrc = System.currentTimeMillis()/10000;
        byte pt = stream.getDynamicRTPPayloadType(Constants.OPUS);

        while ((opusAudioData = of.getNextAudioPacket()) != null)
        {
            int nSamples = opusAudioData.getNumberOfSamples();
            ts += nSamples;
            byte[] data = opusAudioData.getData();
            RawPacket rtp = RawPacket.makeRTP(
                ssrc, // ssrc
                pt,// payload
                ++seq, /// seq
                ts, // ts
                data.length + RawPacket.FIXED_HEADER_SIZE// len
            );

            System.arraycopy(
                data, 0, rtp.getBuffer(), rtp.getPayloadOffset(), data.length);

            stream.injectPacket(rtp, true, null);
            synchronized (of)
            {
                // we wait the time which this packets carries as time of sound
                of.wait(nSamples/48);
            }
        }
    }
}
