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

import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.protocol.event.*;
import net.java.sip.communicator.util.*;
import org.jitsi.jigasi.util.*;
import org.osgi.framework.*;

/**
 * SIP gateway uses first registered SIP account. Manages
 * {@link SipGatewaySession} created for either outgoing or
 * incoming SIP connections.
 *
 * @author Pawel Domas
 * @author Nik Vaessen
 */
public class SipGateway
    extends AbstractGateway<SipGatewaySession>
{
    /**
     * The logger
     */
    private final static Logger logger = Logger.getLogger(SipGateway.class);
    
    /**
     * SIP protocol provider instance.
     */
    private ProtocolProviderService sipProvider;

    /**
     * FIXME: fix synchronization
     */
    private final Object syncRoot = new Object();

    /**
     * Object listens for incoming SIP calls.
     */
    private final SipCallListener callListener = new SipCallListener();

    /**
     * Creates new instance of <tt>SipGateway</tt>.
     */
    public SipGateway(BundleContext bundleContext)
    {
        super(bundleContext);
    }

    /**
     * Stopping the gateway and unregistering.
     */
    public void stop()
    {
        if (this.sipProvider == null)
            throw new IllegalStateException("SIP provider not present");

        try
        {
            this.sipProvider.unregister();
        }
        catch(OperationFailedException e)
        {
            logger.error("Cannot unregister");
        }
    }

    /**
     * Sets SIP provider that will be used by this gateway.
     * @param sipProvider new SIP provider to set.
     */
    public void setSipProvider(ProtocolProviderService sipProvider)
    {
        if (this.sipProvider != null)
            throw new IllegalStateException("SIP provider already set");

        this.sipProvider = sipProvider;

        initProvider(sipProvider);

        new RegisterThread(sipProvider).start();
    }

    /**
     * Returns SIP provider used by this instance.
     * @return the SIP provider used by this instance.
     */
    public ProtocolProviderService getSipProvider()
    {
        return sipProvider;
    }

    private void initProvider(ProtocolProviderService pps)
    {
        pps.addRegistrationStateChangeListener(this);

        OperationSetBasicTelephony telephony = pps.getOperationSet(
            OperationSetBasicTelephony.class);

        telephony.addCallListener(callListener);
    }

    /**
     * Starts new outgoing session by dialing given SIP number and joining JVB
     * conference held in given MUC room.
     * @param ctx the call context for which to create a call
     * @return the newly created SipGatewaySession.
     */
    public SipGatewaySession createOutgoingCall(CallContext ctx)
    {
        SipGatewaySession outgoingSession = new SipGatewaySession(this, ctx);
        outgoingSession.addListener(this);
        outgoingSession.createOutgoingCall();

        return outgoingSession;
    }

    class SipCallListener
        implements CallListener
    {
        @Override
        public void incomingCallReceived(CallEvent event)
        {
            synchronized (syncRoot)
            {

                Call call = event.getSourceCall();

                logger.info("Incoming call received...");

                // create a call context reusing the domain stored in
                // sip account properties if any
                CallContext ctx = new CallContext(call.getProtocolProvider());
                ctx.setDomain(sipProvider.getAccountID()
                    .getAccountPropertyString(
                        CallContext.DOMAIN_BASE_ACCOUNT_PROP));
                call.setData(CallContext.class, ctx);

                SipGatewaySession incomingSession
                    = new SipGatewaySession(
                            SipGateway.this, ctx, call);
                incomingSession.addListener(SipGateway.this);

                incomingSession.initIncomingCall();
            }
        }

        @Override
        public void outgoingCallCreated(CallEvent event) { }

        @Override
        public void callEnded(CallEvent event)
        {
            // FIXME: is it required ?
            //sipCallEnded();
        }
    }

}
