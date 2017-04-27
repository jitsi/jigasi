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
import org.jitsi.service.neomedia.*;
import org.jitsi.service.neomedia.format.*;
import org.jitsi.util.*;

/**
 * Various utility methods.
 *
 * @author Pawel Domas
 */
public class Util
{
    /**
     * A default value for domain that is used.
     */
    public static String domain = null;

    /**
     * A default value for sub domain that is used.
     */
    public static String subDomain = null;

    /**
     * The account property to search in configuration service for the custom
     * bosh URL pattern which will be used when xmpp provider joins a room.
     */
    private static final String BOSH_URL_ACCOUNT_PROP = "BOSH_URL_PATTERN";

    /**
     * An account property to specify custom muc domain prefix, by default it is
     * 'conference'.
     */
    private static final String MUC_DOMAIN_PREFIX_PROP = "MUC_DOMAIN_PREFIX";

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
     * Generate resource based on the timestamp and the subDomain + domain.
     * @return a unique resource.
     */
    public static String generateNextCallResource()
    {
        //FIXME: fix resource generation and check if created resource
        // is already taken
        return Long.toHexString(System.currentTimeMillis())
            + "@" + subDomain + "." + domain;
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
     * A custom bosh URL is needed in some deployments where multidomain is
     * supported. In these deployments there is a virtual conference muc, which
     * address contains the subdomain to use. Those deployments are accessible
     * by URL https://service/subdomain, this means that bosh url used to
     * connect to such deployments must use the same path.
     *
     * When the room name address is in form of
     * roomName@conference.subdomain.domain
     * and there is an account property BOSH_URL_PATTERN which by default
     * will be: https://{host}{subdomain}/http-bind....
     * We need to extract the subdomain and then replace host and subdomain
     * parameters.
     *
     * If room address is just the node (roomname without @.... part) than
     * we return null.
     *
     * If pattern account property is missing this feature is not enabled and we
     * return null.
     *
     * If domain parameter is missing we return null.
     *
     * @param pps the protocol provider configured for custom bosh URL
     * @param roomAddress the room address can be node or node@server
     * @param domain the base domain used to find the possible subdomain
     * @return the new custom bosh url, with replaced {host} and {subdomain}
     * or null if not enabled or not able to process.
     */
    public static String obtainCustomBoshURL(
        ProtocolProviderService pps, String roomAddress, String domain)
    {
        AccountID acc = pps.getAccountID();
        String boshURLPattern
            = acc.getAccountPropertyString(BOSH_URL_ACCOUNT_PROP);

        if (boshURLPattern == null
            || !roomAddress.contains("@")
            || StringUtils.isNullOrEmpty(domain))
        {
            return null;
        }

        String mucAddress = roomAddress.substring(roomAddress.indexOf("@") + 1);
        String mucAddressPrefix = acc.getAccountPropertyString(
            MUC_DOMAIN_PREFIX_PROP, "conference");

        // checks whether the string starts and ends with expected strings
        // and also checks the length of strings that we will extract are not
        // longer than the actual length
        if (!mucAddress.startsWith(mucAddressPrefix)
            || !mucAddress.endsWith(domain))
        {
            // mucAddress not matching settings and passed domain, so skipping
            return null;
        }

        String subdomain = "";
        if (mucAddressPrefix.length() + domain.length() + 2
                    < mucAddress.length())
        {
            // the pattern expects no / between host and subdomain, so we add it
            // extracting prefix + suffix plus two dots
            subdomain = "/"
                + mucAddress.substring(
                    mucAddressPrefix.length() + 1,
                    mucAddress.length() - domain.length() - 1);
        }

        boshURLPattern = boshURLPattern.replace("{host}", domain)
            .replace("{subdomain}", subdomain);

        return boshURLPattern;
    }
}
