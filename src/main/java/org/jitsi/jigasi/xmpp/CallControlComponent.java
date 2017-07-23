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

import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.util.*;

import org.jitsi.jigasi.*;
import org.jitsi.meet.*;
import org.jitsi.service.configuration.*;
import org.jitsi.xmpp.component.*;
import org.osgi.framework.*;
import org.xmpp.component.*;
import org.xmpp.packet.*;

import java.util.*;

/**
 * Experimental implementation of call control component that is capable of
 * utilizing Rayo XMPP protocol for the purpose of SIP gateway calls management.
 *
 * @author Pawel Domas
 */
public class CallControlComponent
    extends ComponentBase
    implements BundleActivator,
               ServiceListener
{
    /**
     * The logger.
     */
    private final static Logger logger
        = Logger.getLogger(CallControlComponent.class);

    /**
     * The call controlling logic.
     */
    private CallControl callControl = null;

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
     * Called as part of the execution of {@link AbstractComponent#shutdown()}
     * to enable this <tt>Component</tt> to finish cleaning resources up after
     * it gets completely shutdown.
     *
     * @see AbstractComponent#postComponentShutdown()
     */
    @Override
    public void postComponentShutdown()
    {
        super.postComponentShutdown();

        OSGi.stop(this);
    }

    /**
     * Called as part of the execution of {@link AbstractComponent#start()} to
     * enable this <tt>Component</tt> to finish initializing resources after it
     * gets completely started.
     *
     * @see AbstractComponent#postComponentStart()
     */
    @Override
    public void postComponentStart()
    {
        super.postComponentStart();

        OSGi.start(this);
    }

    @Override
    public void start(BundleContext bundleContext)
        throws Exception
    {
        SipGateway sipGateway
                = ServiceUtils.getService(
                bundleContext, SipGateway.class);

        TranscriptionGateway transcriptionGateway
                = ServiceUtils.getService(
                bundleContext, TranscriptionGateway.class);

        bundleContext.addServiceListener(this);

        internalStart(bundleContext, sipGateway, transcriptionGateway);
    }

    @Override
    public void serviceChanged(ServiceEvent serviceEvent)
    {
        if (serviceEvent.getType() != ServiceEvent.REGISTERED)
            return;

        ServiceReference ref = serviceEvent.getServiceReference();
        BundleContext bundleContext
            = ref.getBundle().getBundleContext();

        Object service = bundleContext.getService(ref);

        if(this.callControl == null)
        {
            internalStart(bundleContext, null, null);
        }

        if (service instanceof SipGateway
                && this.callControl.getSipGateway() == null)
        {
            this.callControl.setSipGateway((SipGateway) service);
        }
        else if(service instanceof TranscriptionGateway
                && this.callControl.getTranscriptionGateway() == null)
        {
            this.callControl.setTranscriptionGateway(
                    (TranscriptionGateway) service);
        }
        else if (service instanceof ProtocolProviderService)
        {
            ProtocolProviderService pps = (ProtocolProviderService) service;

            if (ProtocolNames.SIP.equals(pps.getProtocolName()))
            {
                updateSipProviderProperties(pps);
            }
        }
    }

    /**
     * Start CallControl
     *
     * @param bundleContext the bundleContext used to start CallControl
     * @param sipGateway the potential SipGateway
     * @param transcriptionGateway the potential TranscriptionGateway
     */
    private void internalStart(BundleContext bundleContext,
                               SipGateway sipGateway,
                               TranscriptionGateway transcriptionGateway)
    {
        ConfigurationService config = ServiceUtils.getService(
                bundleContext, ConfigurationService.class);
        this.callControl = new CallControl(config);

        if (sipGateway != null)
        {
            internalStartSip(sipGateway);
        }
        if(transcriptionGateway != null)
        {
            internalStartTranscription(transcriptionGateway);
        }
    }

    /**
     * Make the CallControl instance handle sip calls
     *
     * @param sipGateway the SipGateway instance.
     */
    private void internalStartSip(SipGateway sipGateway)
    {

        this.callControl.setSipGateway(sipGateway);

        if (sipGateway.getSipProvider() != null)
        {
            updateSipProviderProperties(sipGateway.getSipProvider());
        }
    }

    /**
     * Make the CallControl instance handles transcription
     *
     * @param gateway the TranscriptionGateway
     */
    private void internalStartTranscription(TranscriptionGateway gateway)
    {
        this.callControl.setTranscriptionGateway(gateway);
    }

    /**
     * As incoming calls depend on domain to set it to call context
     * and to generate call resource, we use component domain to set it
     * to the sip provider account properties
     * @param pps the sip protocol provider service
     */
    private void updateSipProviderProperties(ProtocolProviderService pps)
    {
        AccountID sipAccountID = pps.getAccountID();

        Map<String, String> accountProps =
            sipAccountID.getAccountProperties();

        // do not update if property is present
        if (accountProps.containsKey(CallContext.DOMAIN_BASE_ACCOUNT_PROP))
            return;

        accountProps.put(CallContext.DOMAIN_BASE_ACCOUNT_PROP, getDomain());

        sipAccountID.setAccountProperties(accountProps);
    }

    @Override
    public void stop(BundleContext bundleContext)
        throws Exception
    {
        bundleContext.removeServiceListener(this);
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
    protected IQ handleIQSetImpl(IQ iq)
        throws Exception
    {
        IQ resultIQ = handleIQ(iq);

        return (resultIQ == null) ? super.handleIQSetImpl(iq) : resultIQ;
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
     */
    public IQ handleIQ(IQ iq)
        throws Exception
    {
        org.jivesoftware.smack.packet.IQ resultIQ;
        if (callControl == null)
        {
            logger.warn("Call controller not initialized");

            // send service unavailable
            IQ error = IQ.createResultIQ(iq);
            error.setError(PacketError.Condition.service_unavailable);
            return error;
        }
        else
        {
            CallContext ctx = new CallContext();
            ctx.setDomain(getDomain());
            ctx.setSubDomain(getSubdomain());

            resultIQ = callControl.handleIQ(IQUtils.convert(iq), ctx);
        }

        if (resultIQ != null)
            return IQUtils.convert(resultIQ);

        return super.handleIQSet(iq);
    }
}
