/*
 * Jigasi, the JItsi GAteway to SIP.
 *
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
 */
package org.jitsi.jigasi.util;

import io.jsonwebtoken.*;
import net.java.sip.communicator.impl.protocol.jabber.*;
import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.protocol.media.*;
import org.apache.commons.lang3.StringUtils;
import org.jitsi.jigasi.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.service.neomedia.format.*;
import org.jitsi.utils.*;
import org.jitsi.utils.concurrent.*;
import org.jitsi.utils.logging.Logger;
import org.jitsi.xmpp.extensions.jitsimeet.*;
import org.jivesoftware.smack.bosh.*;
import org.jivesoftware.smack.packet.*;
import org.jivesoftware.smackx.muc.packet.*;
import org.json.simple.*;
import org.json.simple.parser.*;

import java.io.*;
import java.lang.reflect.*;
import java.security.spec.*;
import java.util.*;

import java.math.*;
import java.security.*;
import java.util.concurrent.*;

/**
 * Various utility methods.
 *
 * @author Pawel Domas
 */
public class Util
{
    /**
     * The name of XMPP feature which is used to recognize jibri participants.
     */
    public static final String JIBRI_FEATURE_NAME = "http://jitsi.org/protocol/jibri";

    /**
     * The name of XMPP feature which states for Jigasi SIP Gateway and can be
     * used to recognize gateway client.
     */
    public static final String JIGASI_FEATURE_NAME = "http://jitsi.org/protocol/jigasi";

    /**
     * The name of XMPP feature which states this Jigasi is participating as transcriber.
     */
    public static final String TRANSCRIBER_FEATURE_NAME = "http://jitsi.org/protocol/transcriber";

    /**
     * The name of the property to get the array of trusted domains. To be used when checking
     * presences for jibri/jigasi features.
     */
    public static final String P_NAME_TRUSTED_DOMAINS = "org.jitsi.jigasi.TRUSTED_DOMAINS";

    /**
     * List of trusted domains to check when checking the presence for jigasi/jibri features.
     */
    private static List<String> trustedDomains = null;

    /**
     * The logger.
     */
    private final static Logger logger = Logger.getLogger(Util.class);

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
     * Get the md5 hash of a string
     *
     * received from:
     * https://stackoverflow.com/questions/415953/how-can-i-generate-an-md5-hash
     *
     * @param toHash the string to generate the MD5 hash from
     * @return the md5 hash of the given string
     */
    public static String stringToMD5hash(String toHash)
    {
        try
        {
            MessageDigest m = MessageDigest.getInstance("MD5");
            m.reset();
            m.update(toHash.getBytes());
            byte[] digest = m.digest();
            BigInteger bigInt = new BigInteger(1, digest);
            String hashtext = bigInt.toString(16);

            // Now we need to zero pad it if you actually want the full
            // 32 chars.
            if (hashtext.length() < 32)
            {
                int padLength = 32 - hashtext.length();
                String pad = String.join("",
                    Collections.nCopies(padLength, "0"));
                hashtext = pad + hashtext;
            }

            return hashtext;
        }
        catch (NoSuchAlgorithmException e)
        {
            Logger.getLogger(Util.class).error("Error creating hash", e);
        }

        return null;
    }

    /**
     * Extracts bosh connection sessionId if possible.
     * @param connection the bosh connection which sessionId we will try to
     * extract.
     * @return the sessionId if extracted or null.
     */
    public static Object getConnSessionId(Object connection)
    {
        Field myField = getField(XMPPBOSHConnection.class, "sessionID");

        if (myField != null)
        {
            myField.setAccessible(true);
            try
            {
                return myField.get(connection);
            }
            catch( Exception e)
            {
                Logger.getLogger(Util.class).error("cannot read it", e);
            }
        }

        return null;
    }

    /**
     * Utility method to get the field from a class.
     * @param clazz the class.
     * @param fieldName the field men.
     * @return the field or null.
     */
    private static Field getField(Class clazz, String fieldName)
    {
        try
        {
            return clazz.getDeclaredField(fieldName);
        }
        catch (NoSuchFieldException e)
        {
            Class superClass = clazz.getSuperclass();
            if (superClass == null)
            {
                return null;
            }
            else
            {
                return getField(superClass, fieldName);
            }
        }
    }

    /**
     * Makes a new RTP {@code RawPacket} filled with padding with the specified
     * parameters. Note that because we're creating a packet filled with
     * padding, the length must not exceed 12 + 0xFF.
     *
     * @param ssrc the SSRC of the RTP packet to make.
     * @param pt the payload type of the RTP packet to make.
     * @param seqNum the sequence number of the RTP packet to make.
     * @param ts the RTP timestamp of the RTP packet to make.
     * @param len the length of the RTP packet to make.
     * @return the RTP {@code RawPacket} that was created.
     */
    public static RawPacket makeRTP(
        long ssrc, int pt, int seqNum, long ts, int len)
    {
        byte[] buf = new byte[len];

        RawPacket pkt = new RawPacket(buf, 0, buf.length);

        pkt.setVersion();
        pkt.setPayloadType((byte) pt);
        pkt.setSSRC((int) ssrc);
        pkt.setTimestamp(ts);
        pkt.setSequenceNumber(seqNum);

        return pkt;
    }

    /**
     * Creates new thread pool with one initial thread and can grow up.
     * @param name the threads name prefix.
     * @return the newly created pool.
     */
    public static ExecutorService createNewThreadPool(String name)
    {
        return new ThreadPoolExecutor(
            1, 1000, // a pretty big thread pool size to avoid reaching capacity
            60L, TimeUnit.SECONDS, // time to wait before clearing threads
            new SynchronousQueue<>(),
            new CustomizableThreadFactory(name, true));
    }

    /**
     * Creates a feature xmpp extension, that can be added to the features and used in presence.
     * @param var the value to be added.
     * @return the extension element.
     */
    public static ExtensionElement createFeature(String var)
    {
        FeatureExtension feature = new FeatureExtension();
        feature.setAttribute("var", var);

        return feature;
    }

    @SuppressWarnings("unchecked")
    private static List<String> getTrustedDomains()
    {
        if (Util.trustedDomains == null)
        {
            String trustedDomainsStr
                = JigasiBundleActivator.getConfigurationService().getString(P_NAME_TRUSTED_DOMAINS);

            if (trustedDomainsStr != null)
            {
                JSONParser parser = new JSONParser();
                try
                {
                    logger.info("Initialized trusted domains: " + trustedDomainsStr);
                    JSONArray trustedArray = (JSONArray) parser.parse(trustedDomainsStr);

                    Util.trustedDomains = new ArrayList<String>(trustedArray);
                }
                catch (ParseException e)
                {
                    logger.error("Cannot parse trusted domains:" + trustedDomainsStr, e);
                    Util.trustedDomains = new ArrayList<>();
                }
            }
            else
            {
                Util.trustedDomains = new ArrayList<>();
            }
        }

        return Util.trustedDomains;
    }

    private static boolean checkForFeature(ChatRoomMemberJabberImpl member, String feature)
    {
        Presence presence = member.getLastPresence();

        FeaturesExtension features = presence.getExtension(FeaturesExtension.class);

        if (features == null || !getTrustedDomains().contains(member.getJabberID().asDomainBareJid().toString()))
        {
            return false;
        }

        return features.getFeatureExtensions().stream().anyMatch(f -> f.getVar().equals(feature));
    }

    /**
     * Checks for the transcriber feature in presence.
     * @param presence the presence to check,
     * @return <tt>true</tt> when the presence is from a transcriber.
     */
    public static boolean isTranscriberJigasi(Presence presence)
    {
        FeaturesExtension features = presence.getExtension(FeaturesExtension.class);
        MUCUser mucUser = (MUCUser) presence.getExtensionElement("x", "http://jabber.org/protocol/muc#user");

        if (features == null || mucUser == null || mucUser.getItem() == null
            || !getTrustedDomains().contains(mucUser.getItem().getJid().asDomainBareJid().toString()))
        {
            return false;
        }

        return features.getFeatureExtensions().stream()
            .anyMatch(f -> f.getVar().equals(TRANSCRIBER_FEATURE_NAME));
    }

    /**
     * Checks for the transcriber feature in presence.
     * @param member the member to check
     * @return <tt>true</tt> when the presence is from a transcriber.
     */
    public static boolean isTranscriberJigasi(ChatRoomMemberJabberImpl member)
    {
        return checkForFeature(member, TRANSCRIBER_FEATURE_NAME);
    }

    /**
     * Checks for the jigasi feature in presence.
     * @param member the member to check
     * @return <tt>true</tt> when the presence is from a jigasi.
     */
    public static boolean isJigasi(ChatRoomMemberJabberImpl member)
    {
        return checkForFeature(member, JIGASI_FEATURE_NAME);
    }

    /**
     * Checks for the jibri feature in presence.
     * @param member the member to check
     * @return <tt>true</tt> when the presence is from a jibri.
     */
    public static boolean isJibri(ChatRoomMemberJabberImpl member)
    {
        return checkForFeature(member, JIBRI_FEATURE_NAME);
    }

    /**
     * Generates asap token.
     * @return the generated token.
     */
    public static String generateAsapToken(
            String privateKey, String privateKeyId, String audience, String issuer)
        throws NoSuchAlgorithmException,
               InvalidKeySpecException,
               IOException
    {
        if (StringUtils.isEmpty(privateKey) || StringUtils.isEmpty(privateKeyId))
        {
            throw new IOException("Failed generating JWT. Missing private key or key name.");
        }

        long nowMillis = System.currentTimeMillis();
        Date now = new Date(nowMillis);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        PKCS8EncodedKeySpec keySpecPKCS8 = new PKCS8EncodedKeySpec(Base64.getDecoder().decode(privateKey));
        PrivateKey finalPrivateKey = kf.generatePrivate(keySpecPKCS8);

        JwtBuilder builder = Jwts.builder()
            .setHeaderParam("kid", privateKeyId)
            .setIssuedAt(now)
            .setAudience(audience)
            .setIssuer(issuer)
            .signWith(finalPrivateKey, SignatureAlgorithm.RS256);
        builder.setExpiration(new Date(nowMillis + (60 * 5 * 1000)));

        return builder.compact();
    }
}
