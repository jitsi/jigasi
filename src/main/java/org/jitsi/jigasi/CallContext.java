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

import org.jitsi.jigasi.util.*;
import org.jitsi.utils.*;
import org.jxmpp.jid.*;
import org.jxmpp.jid.impl.*;
import org.jxmpp.stringprep.*;

import java.util.*;

/**
 * The call context with all the parameters needed while
 * processing call requests.
 *
 * @author Damian Minkov
 */
public class CallContext
{
    /**
     * The account property to search in configuration service for the custom
     * bosh URL pattern which will be used when xmpp provider joins a room.
     */
    public static final String BOSH_URL_ACCOUNT_PROP = "BOSH_URL_PATTERN";

    /**
     * An account property to specify custom muc domain prefix, by default it is
     * 'conference'.
     */
    public static final String MUC_DOMAIN_PREFIX_PROP = "MUC_DOMAIN_PREFIX";

    /**
     * An account property to specify domain served by a sip or xmpp provider.
     */
    public static final String DOMAIN_BASE_ACCOUNT_PROP = "DOMAIN_BASE";

    private static final Random RANDOM = new Random();

    /**
     * The room name of the MUC room that holds JVB conference call.
     */
    private String roomName;

    /**
     * Domain that this call instance is handling.
     */
    private String domain;

    /**
     * Sub-domain that this instance is handling.
     * This property is optional.
     *
     * In case of deployments where multiple domains are managed, the value is
     * subtracted from the full conference room name
     * 'roomName@conference.subdomain.domain'.
     *
     * Also used in deployments where we use jigasi as xmpp component
     * and the value is the one that is passed as command line parameter
     * '--subdomain'.
     */
    private String subDomain;

    /**
     * Optional password required to enter MUC room.
     */
    private String roomPassword;

    /**
     * Optional bosh url that we use to join a room with the
     * xmpp account.
     * The bosh URL is a pattern:
     * https://{host}{subdomain}/http-bind?room={roomName}
     * Call context take care of the parameters {host} and {subdomain}
     * replacing them with domain and if available subdomain separating them
     * with '/', otherwise replaces subdomain with ''.
     */
    private String boshURL;

    /**
     * The destination address to call for outgoing calls.
     */
    private String destination;

    /**
     * Muc address prefix, default is 'conference'.
     * Used when parsing subdomain out of the full
     * room name 'roomName@conference.subdomain.domain'.
     */
    private String mucAddressPrefix;

    /**
     * A timestamp when this context was created, used to construct the
     * callResource.
     */
    private final long timestamp;

    /**
     * Call resource identifying this call context.
     * Generated in the form 'timestamp@domain' or 'timestamp@subdomain.domain'.
     */
    private Jid callResource;

    /**
     * There is an option for setting custom call resource, currently used only
     * in tests.
     */
    private Jid customCallResource = null;

    /**
     * The source creating the context.
     */
    private final Object source;

    /**
     * A unique id of this context.
     */
    private final String ctxId;

    /**
     * Call extra headers. Information coming with the rayo messages.
     * We use this information to pass it to both legs of the calls initiated.
     */
    private final Map<String, String> extraHeaders = new HashMap<>();

    /**
     * Constructs new CallContext saving the timestamp at which it was created.
     */
    public CallContext(Object source)
    {
        this.source = source;
        this.timestamp = System.currentTimeMillis();
        this.ctxId = this.timestamp + String.valueOf(super.hashCode());
    }

    /**
     * The room name of the MUC room that holds JVB conference call.
     * @return the room name.
     */
    public String getRoomName()
    {
        return roomName;
    }

    /**
     * Sets the room name.
     * @param roomName the room name to use.
     */
    public void setRoomName(String roomName)
    {
        this.roomName = roomName;
        update();
    }

    /**
     * Returns the conference name.
     * @return the conference name.
     */
    public String getConferenceName()
    {
        if (this.roomName.contains("@"))
        {
            return Util.extractCallIdFromResource(this.roomName);
        }

        return this.roomName;
    }

    /**
     * Domain that this call instance is handling.
     * @return domain that this call instance is handling.
     */
    public String getDomain()
    {
        return domain;
    }

    /**
     * Sets the domain that this call instance is handling.
     * @param domain to use.
     */
    public void setDomain(String domain)
    {
        // ignore attempts to override already set domain with null value.
        // we set domain from different locations and if there is no value
        // ignore it
        if (domain == null)
            return;

        this.domain = domain;
        update();
        updateCallResource();
    }

    /**
     * Sets the sub domain to use when creating a call resource or to be used
     * when updating bosh url.
     * @param subDomain the subdomain to use.
     */
    public void setSubDomain(String subDomain)
    {
        this.subDomain = subDomain;
        updateCallResource();
    }

    /**
     * Returns sub domain that this call instance is handling.
     * @return sub domain that this call instance is handling.
     */
    public String getSubDomain()
    {
        return subDomain;
    }

    /**
     * Password required to enter MUC room, optional.
     * @return the muc room password or null.
     */
    public String getRoomPassword()
    {
        return roomPassword;
    }

    /**
     * Sets password required to enter MUC room.
     * @param roomPassword the new password.
     */
    public void setRoomPassword(String roomPassword)
    {
        this.roomPassword = roomPassword;
    }

    /**
     * Bosh url that we use to join a room with the xmpp account.
     * @return the bosh url to use or null.
     */
    public String getBoshURL()
    {
        return boshURL;
    }

    /**
     * Sets a bosh url pattern to use.
     * @param boshURL the new bosh url pattern.
     */
    public void setBoshURL(String boshURL)
    {
        this.boshURL = boshURL;
        update();
    }

    /**
     * The destination address to call for outgoing calls.
     * @return the destination address to call for outgoing calls.
     */
    public String getDestination()
    {
        return destination;
    }

    /**
     * Sets destination address to call for outgoing calls.
     * @param destination the address to use for outgoing calls.
     */
    public void setDestination(String destination)
    {
        this.destination = destination;
    }

    /**
     * Sets muc address prefix.
     * @param mucAddressPrefix muc address prefix value.
     */
    public void setMucAddressPrefix(String mucAddressPrefix)
    {
        this.mucAddressPrefix = mucAddressPrefix;
        update();
    }

    /**
     * Returns the call resource to use for this call context.
     * @return the call resource to use for this call context.
     */
    public Jid getCallResource()
    {
        if (customCallResource != null)
            return customCallResource;

        return callResource;
    }

    /**
     * Sets custom call resource to use.
     * @param customCallResource custom call resource to use.
     */
    public void setCustomCallResource(Jid customCallResource)
    {
        this.customCallResource = customCallResource;
    }

    /**
     * Returns the timestamp when this call context was created.
     * @return the timestamp when this call context was created.
     */
    public long getTimestamp()
    {
        return timestamp;
    }

    /**
     * Updates call resource based on timestamp, domain and if available and
     * the subdomain.
     */
    void updateCallResource()
    {
        if (this.domain != null)
        {
            try
            {
                // The local part of this JID ends up being used as the resource
                // part of the JID in the MUC, so make sure it uses the format
                // jitsi-meet and jitsi-videobridge expect: 8 hex digits padded
                // with 0s if necessary.
                long random = RANDOM.nextInt() & 0xffff_ffff;
                this.callResource = JidCreate.entityBareFrom(
                    String.format("%8h", random).replace(' ', '0')
                    + "@"
                    + (this.subDomain != null ? this.subDomain + "." : "")
                    + this.domain);
            }
            catch (XmppStringprepException e)
            {
                throw new RuntimeException(e);
            }
        }
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
     * and there is boshURL patter which by default will be:
     * https://{host}{subdomain}/http-bind....
     * We need to extract the subdomain and then replace host and subdomain
     * parameters.
     *
     * If room address is just the node (roomname without @.... part) than
     * we just replace {host} with domain and {subdomain} with ''.
     *
     * If bosh URL pattern is missing or we are missing domain, we do not update
     * anything.
     */
    private void update()
    {
        // boshURL or domain missing, do nothing
        if (boshURL == null
            || StringUtils.isNullOrEmpty(domain))
        {
            return;
        }

        // we have domain let's update it
        boshURL = boshURL.replace("{host}", domain);

        String subdomain = "";
        if (roomName != null && roomName.contains("@"))
        {
            String mucAddress = roomName.substring(roomName.indexOf("@") + 1);
            String mucAddressPrefix = this.mucAddressPrefix != null ?
                this.mucAddressPrefix : "conference";

            // checks whether the string starts and ends with expected strings
            // and also checks the length of strings that we will extract are not
            // longer than the actual length
            if (mucAddress.startsWith(mucAddressPrefix)
                && mucAddress.endsWith(domain))
            {
                // mucAddress not matching settings and passed domain, so skipping
                if (mucAddressPrefix.length() + domain.length() + 2
                    < mucAddress.length())
                {
                    // the pattern expects no / between host and subdomain, so we add it
                    // extracting prefix + suffix plus two dots
                    subdomain =
                        mucAddress.substring(
                            mucAddressPrefix.length() + 1,
                            mucAddress.length() - domain.length() - 1);
                    this.subDomain = subdomain;
                    subdomain = "/" + subdomain;
                }
            }

            // update subdomain only when roomName is provided
            // otherwise subdomain will be empty and we will loose the template,
            // before we have a chance to check for subdomain in the
            // target room name
            boshURL = boshURL.replace("{subdomain}", subdomain);
        }
    }

    /**
     * Returns the source that created this context.
     * @return the source that created this context.
     */
    public Object getSource()
    {
        return source;
    }

    /**
     * Return the meeting url of this context
     *
     * @return the meeting url
     */
    public String getMeetingUrl()
    {
        String url = getBoshURL();
        String room = getRoomName();

        if(url == null || room == null)
        {
            return null;
        }

        url = url.substring(0, url.indexOf("/http-bind"));
        room = room.substring(0, room.indexOf("@"));

        return url + "/" + room;
    }

    @Override
    public String toString()
    {
        return "[ctx=" + ctxId + ']';
    }

    /**
     * Adds extra headers to use for this call context.
     * @param name the name of the header.
     * @param value the value of the header.
     */
    public synchronized void addExtraHeader(String name, String value)
    {
        if (!this.extraHeaders.containsKey(name))
        {
            this.extraHeaders.put(name, value);
        }
    }

    /**
     * Returns the extra headers.
     * @return the extra headers.
     */
    public Map<String, String> getExtraHeaders()
    {
        return Collections.unmodifiableMap(this.extraHeaders);
    }
}
