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
package org.jitsi.jigasi.xmpp;

import net.java.sip.communicator.impl.protocol.jabber.extensions.rayo.*;
import net.java.sip.communicator.util.*;

import org.dom4j.*;
import org.jitsi.jigasi.*;
import org.jitsi.meet.*;
import org.jitsi.service.configuration.*;
import org.jitsi.xmpp.component.*;
import org.jivesoftware.smack.packet.*;
import org.osgi.framework.*;
import org.xmpp.component.*;
import org.xmpp.packet.IQ;
import org.xmpp.packet.Message;

/**
 * Experimental implementation of call control component that is capable of
 * utilizing Rayo XMPP protocol for the purpose of SIP gateway calls management.
 *
 * @author Pawel Domas
 */
public class CallControlComponent
    extends ComponentBase
    implements BundleActivator,
               CallsControl,
               ServiceListener
{
    /**
     * The logger.
     */
    private final static Logger logger
        = Logger.getLogger(CallControlComponent.class);

    /**
     * Name of 'header' attribute that hold JVB room name.
     */
    public static final String ROOM_NAME_HEADER = "JvbRoomName";

    /**
     * Optional header for specifying password required to enter MUC room.
     */
    public static final String ROOM_PASSWORD_HEADER = "JvbRoomPassword";

    /**
     * JID allowed to make outgoing SIP calls.
     */
    public static final String ALLOWED_JID_P_NAME
        = "org.jitsi.jigasi.ALLOWED_JID";

    /**
     * The {@link SipGateway} service which manages gateway sessions.
     */
    private SipGateway gateway;

    /**
     * The only JID that will be allowed to create outgoing SIP calls. If not
     * set then anybody is allowed to do so.
     */
    private String allowedJid;

    /**
     * FIXME: temporary to be removed/fixed
     */
    //private Map<SipGateway, String> hangupMap
      //  = new HashMap<SipGateway, String>();

    /**
     * Creates new instance of <tt>CallControlComponent</tt>.
     * @param host the hostname or IP address to which this component will be
     *             connected.
     * @param port the port of XMPP server to which this component will connect.
     * @param domain the name of main XMPP domain on which this component will
     *               be served.
     * @param subDomain the name of subdomain on which this component will be
     *                  available.
     * @param secret the password used by the component to authenticate with
     *               XMPP server.
     */
    public CallControlComponent(String   host,
                                int      port,
                                String   domain,
                                String   subDomain,
                                String   secret)
    {
        super(host, port, domain, subDomain, secret);
    }

    /**
     * Initializes this component.
     */
    public void init()
    {
        OSGi.start(this);
    }

    @Override
    public void start(BundleContext bundleContext)
        throws Exception
    {
        this.gateway
            = ServiceUtils.getService(
                    bundleContext, SipGateway.class);

        if (this.gateway != null)
            internalStart(bundleContext);
        else
            bundleContext.addServiceListener(new ServiceListener()
            {
                @Override
                public void serviceChanged(ServiceEvent serviceEvent)
                {
                    if (serviceEvent.getType() != ServiceEvent.REGISTERED)
                        return;

                    ServiceReference ref = serviceEvent.getServiceReference();
                    BundleContext bundleContext
                        = ref.getBundle().getBundleContext();

                    Object service = bundleContext.getService(ref);

                    if (!(service instanceof SipGateway))
                        return;

                    CallControlComponent.this.gateway = (SipGateway) service;
                    bundleContext.removeServiceListener(this);
                    internalStart(bundleContext);
                }
            });
    }

    private void internalStart(BundleContext bundleContext)
    {
        ConfigurationService config
            = ServiceUtils.getService(
            bundleContext, ConfigurationService.class);

        Boolean always_trust_mode = config.getBoolean(
            "net.java.sip.communicator.service.gui.ALWAYS_TRUST_MODE_ENABLED",false);
        if (always_trust_mode)
        {
            // Always trust mode - prevent failures because there's no GUI
            // to ask the user, but do we always want to trust so, in this
            // mode, the service is vulnerable to Man-In-The-Middle attacks.
            logger.warn("Always trust in remote TLS certificates mode is enabled");
        }

        this.allowedJid = config.getString(ALLOWED_JID_P_NAME, null);

        if (allowedJid != null)
        {
            logger.info("JID allowed to make outgoing calls: " + allowedJid);
        }

        gateway.setCallsControl(this);

        gateway.setXmppServerName(getDomain());
    }

    @Override
    public void stop(BundleContext bundleContext)
        throws Exception
    {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected String[] discoInfoFeatureNamespaces()
    {
        return
            new String[]
                {
                    "http://jitsi.org/protocol/jigasi",
                    "urn:xmpp:rayo:0"
                };
    }

    @Override
    public String getDescription()
    {
        return "Call control component";
    }

    @Override
    public String getName()
    {
        return "Call control";
    }

    /**
     * Initializes new outgoing call.
     * @param roomName the name of the MUC room that holds JVB conference call.
     * @param roomPass optional password required to enter MUC room.
     * @param from source address(optional)
     * @param to destination call address/URI.
     * @return the call resource string that will identify newly created call.
     */
    String initNewCall(String roomName, String roomPass, String from, String to)
    {
        String callResource = generateNextCallResource();

        gateway.createOutgoingCall(to, roomName, roomPass, callResource);

        return callResource;
    }

    private String generateNextCallResource()
    {
        //FIXME: fix resource generation and check if created resource
        // is already taken
        return Long.toHexString(System.currentTimeMillis())
            + "@" + getSubdomain() + "." + getDomain();
    }

    /**
     * Handles an <tt>org.xmpp.packet.IQ</tt> stanza of type <tt>set</tt> which
     * represents a request.
     *
     * @param iq the <tt>org.xmpp.packet.IQ</tt> stanza of type <tt>set</tt>
     * which represents the request to handle
     * @return an <tt>org.xmpp.packet.IQ</tt> stanza which represents the
     * response to the specified request or <tt>null</tt> to reply with
     * <tt>feature-not-implemented</tt>
     * @throws Exception to reply with <tt>internal-server-error</tt> to the
     * specified request
     * @see AbstractComponent#handleIQSet(IQ)
     */
    @Override
    public IQ handleIQSet(IQ iq)
        throws Exception
    {
        try
        {
            org.jivesoftware.smack.packet.IQ smackIq = IQUtils.convert(iq);

            String fromBareJid = iq.getFrom().toBareJID();
            if (allowedJid != null && !allowedJid.equals(fromBareJid))
            {
                org.jivesoftware.smack.packet.IQ error
                    = org.jivesoftware.smack.packet.IQ
                        .createErrorResponse(
                                smackIq,
                                new XMPPError(XMPPError.Condition.not_allowed));

                return IQUtils.convert(error);
            }
            else if (allowedJid == null)
            {
                logger.warn("Requests are not secured by JID filter!");
            }

            if (smackIq instanceof RayoIqProvider.DialIq)
            {
                RayoIqProvider.DialIq dialIq = (RayoIqProvider.DialIq) smackIq;

                String from = dialIq.getSource();
                String to = dialIq.getDestination();

                String roomName = dialIq.getHeader(ROOM_NAME_HEADER);
                String roomPassword = dialIq.getHeader(ROOM_PASSWORD_HEADER);
                if (roomName == null)
                    throw new RuntimeException("No JvbRoomName header found");

                logger.info(
                    "Got dial request " + from + " -> " + to
                    + " room: " + roomName);

                String callResource
                    = initNewCall(roomName, roomPassword, from, to);

                callResource = "xmpp:" + callResource;

                RayoIqProvider.RefIq ref
                    = RayoIqProvider.RefIq.createResult(smackIq, callResource);

                return IQUtils.convert(ref);
            }
            else if (smackIq instanceof RayoIqProvider.HangUp)
            {
                RayoIqProvider.HangUp hangUp
                    = (RayoIqProvider.HangUp) smackIq;

                String callUri = hangUp.getTo();
                String callResource = callUri;

                GatewaySession session = gateway.getSession(callResource);

                if (session == null)
                    throw new RuntimeException(
                        "No gateway for call: " + callResource);

                //hangupMap.put(gateway, smackIq.getFrom());

                session.hangUp();

                org.jivesoftware.smack.packet.IQ result
                    = org.jivesoftware.smack.packet.IQ.createResultIQ(smackIq);

                return IQUtils.convert(result);
            }
            else
            {
                return super.handleIQSet(iq);
            }
        }
        catch (Exception e)
        {
            logger.error(e, e);
            throw e;
        }
    }

    @Override
    public void callEnded(SipGateway gateway, String callResource)
    {
        // Send confirmation
        // FIXME: we've left the room already at this point
        /*EndExtension end = new EndExtension();
        end.setReason(
            new ReasonExtension(ReasonExtension.HANGUP));
        HeaderExtension header = new HeaderExtension();
        header.setName("uri");
        header.setValue("xmpp:" + gatewaySession.callResource);

        end.addChildExtension(header);

        gateway.sendPresenceExtension(end);*/
    }

    /**
     * Call resource currently has the form of e23gr547@callcontro.server.net.
     * This methods extract random call id part before '@' sign. In the example
     * above it is 'e23gr547'.
     * @param callResource the call resource/URI from which the call ID part
     *                     will be extracted.
     * @return extracted random call ID part from full call resource string.
     */
    @Override
    public String extractCallIdFromResource(String callResource)
    {
        return callResource.substring(0, callResource.indexOf("@"));
    }

    protected void sendPacketXml(String xmlToSend)
    {
        try
        {
            Document doc = DocumentHelper.parseText(xmlToSend);

            org.xmpp.packet.Message toSend
                = new Message(doc.getRootElement());

            send(toSend);
        }
        catch (DocumentException e)
        {
            logger.error(e, e);
        }
    }

    @Override
    public void serviceChanged(ServiceEvent serviceEvent)
    {
        if (serviceEvent.getType() != ServiceEvent.REGISTERED)
            return;

        ServiceReference ref = serviceEvent.getServiceReference();

        Object service = JigasiBundleActivator.osgiContext.getService(ref);

        if (!(service instanceof SipGateway))
            return;

        SipGateway gateway = (SipGateway) service;

        gateway.setCallsControl(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String allocateNewSession(SipGateway gateway)
    {
        return generateNextCallResource();
    }
}
