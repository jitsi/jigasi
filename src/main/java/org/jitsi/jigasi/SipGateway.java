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
import net.java.sip.communicator.service.shutdown.*;
import net.java.sip.communicator.util.*;
import org.jitsi.jigasi.util.*;
import org.osgi.framework.*;

import java.util.*;

/**
 * SIP gateway uses first registered SIP account. Manages {@link GatewaySession}
 * created for either outgoing or incoming SIP connections.
 *
 * @author Pawel Domas
 */
public class SipGateway
    implements RegistrationStateChangeListener
{
    /**
     * The logger
     */
    private final static Logger logger = Logger.getLogger(SipGateway.class);

    /**
     * Name of the property used to override incoming
     */
    public static final String P_NAME_DEFAULT_JVB_ROOM
        = "org.jitsi.jigasi.DEFAULT_JVB_ROOM_NAME";

    /**
     * Name of the property used to set default JVB conference invite timeout.
     */
    public static final String P_NAME_JVB_INVITE_TIMEOUT
        = "org.jitsi.jigasi.JVB_INVITE_TIMEOUT";

    /**
     * Name of the property used to disable advertisement of ICE feature in 
     * XMPP capabilities list. This allows conference manager to allocate
     * channels with RAW transport and speed up connectivity process.
     */
    public static final String P_NAME_DISABLE_ICE
        = "org.jitsi.jigasi.DISABLE_ICE";
    
    /**
     * Default JVB conference invite timeout.
     */
    public static final long DEFAULT_JVB_INVITE_TIMEOUT = 30L * 1000L;

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
     * SIP gateways map.
     */
    private final Map<String, GatewaySession> sessions
        = new HashMap<String, GatewaySession>();

    /**
     * Indicates if jigasi instance has entered graceful shutdown mode.
     */
    private boolean shutdownInProgress;

    /**
     * The (OSGi) <tt>BundleContext</tt> in which this <tt>SipGateway</tt> has
     * been started.
     */
    private BundleContext bundleContext;

    /**
     * Listeners that will be notified of SipGateway changes.
     */
    private final ArrayList<SipGatewayListener> sipGatewayListeners
        = new ArrayList<>();

    /**
     * Creates new instance of <tt>SipGateway</tt>.
     */
    public SipGateway(BundleContext bundleContext)
    {
        this.bundleContext = bundleContext;
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

    @Override
    public void registrationStateChanged(RegistrationStateChangeEvent evt)
    {
        ProtocolProviderService pps = evt.getProvider();

        logger.info("REG STATE CHANGE " + pps + " -> " + evt);
    }

    /**
     * Notified that current call has ended.
     */
    void notifyCallEnded(String callResource)
    {
        GatewaySession session;

        synchronized (sessions)
        {
            session = sessions.remove(callResource);

            if (session == null)
            {
                // FIXME: print some gateway ID or provider here
                logger.error(
                    "Call resource not exists for session " + callResource);
                return;
            }

            fireGatewaySessionRemoved(session);
        }

        logger.info("Removed session for call " + callResource);

        // Check if it's the time to shutdown now
        maybeDoShutdown();
    }

    /**
     * Starts new outgoing session by dialing given SIP number and joining JVB
     * conference held in given MUC room.
     * @param to the destination SIP number that will be called.
     * @param roomName the name of MUC that holds JVB conference that will be
     *                 joined.
     * @param roomPass optional password for joining protected MUC room.
     * @param callResource the call resource that will identify new call.
     */
    public GatewaySession createOutgoingCall(
            String to, String roomName, String roomPass, String callResource)
    {
        GatewaySession outgoingSession = new GatewaySession(this);

        sessions.put(callResource, outgoingSession);

        fireGatewaySessionAdded(outgoingSession);

        outgoingSession.createOutgoingCall(
            to, roomName, roomPass, callResource);

        return outgoingSession;
    }

    /**
     * Finds {@link GatewaySession} for given <tt>callResource</tt> if one is
     * currently active.
     *
     * @param callResource the call resource/URI of the <tt>GatewaySession</tt>
     *                     to be found.
     *
     * @return {@link GatewaySession} for given <tt>callResource</tt> if there
     *         is one currently active or <tt>null</tt> otherwise.
     */
    public GatewaySession getSession(String callResource)
    {
        synchronized (sessions)
        {
            return sessions.get(callResource);
        }
    }

    /**
     * @return the list of <tt>GatewaySession</tt>s currently active.
     */
    public List<GatewaySession> getActiveSessions()
    {
        synchronized (sessions)
        {
            return new ArrayList<GatewaySession>(sessions.values());
        }
    }

    /**
     * Returns timeout for waiting for the JVB conference invite from the focus.
     */
    public static long getJvbInviteTimeout()
    {
        return JigasiBundleActivator.getConfigurationService()
            .getLong(P_NAME_JVB_INVITE_TIMEOUT, DEFAULT_JVB_INVITE_TIMEOUT);
    }

    /**
     * Sets new timeout for waiting for the JVB conference invite from the
     * focus.
     * @param newTimeout the new timeout value in ms to set.
     */
    public static void setJvbInviteTimeout(long newTimeout)
    {
        JigasiBundleActivator.getConfigurationService()
            .setProperty(SipGateway.P_NAME_JVB_INVITE_TIMEOUT, newTimeout);
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

                String callResource = Util.generateNextCallResource();

                GatewaySession incomingSession
                    = new GatewaySession(
                            SipGateway.this, callResource, call);

                sessions.put(callResource, incomingSession);

                fireGatewaySessionAdded(incomingSession);

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

    /**
     * Enables graceful shutdown mode on this jigasi instance and eventually
     * starts the shutdown immediately if no conferences are currently being
     * hosted. Otherwise jigasi will shutdown once all conferences expire.
     */
    public void enableGracefulShutdownMode()
    {
        if (!shutdownInProgress)
        {
            logger.info("Entered graceful shutdown mode");
        }
        this.shutdownInProgress = true;
        maybeDoShutdown();
    }

    /**
     * Returns {@code true} if this instance has entered graceful shutdown mode.
     *
     * @return {@code true} if this instance has entered graceful shutdown mode;
     * otherwise, {@code false}
     */
    public boolean isShutdownInProgress()
    {
        return shutdownInProgress;
    }

    /**
     * Triggers the shutdown given that we're in graceful shutdown mode and
     * there are no conferences currently in progress.
     */
    private void maybeDoShutdown()
    {
        if (!shutdownInProgress)
            return;

        synchronized (sessions)
        {
            if (sessions.isEmpty())
            {
                this.stop();

                ShutdownService shutdownService
                    = ServiceUtils.getService(
                    bundleContext,
                    ShutdownService.class);

                logger.info("Jigasi is shutting down NOW");
                shutdownService.beginShutdown();
            }
        }
    }

    /**
     * Adds a listener that will be notified of changes in our status.
     *
     * @param listener a sip gateway status listener.
     */
    public void addSipGatewayListener(SipGatewayListener listener)
    {
        synchronized(sipGatewayListeners)
        {
            if (!sipGatewayListeners.contains(listener))
                sipGatewayListeners.add(listener);
        }
    }

    /**
     * Removes a listener that was being notified of changes in the status of
     * SipGateway.
     *
     * @param listener a sip gateway status listener.
     */
    public void removeSipGatewayListener(SipGatewayListener listener)
    {
        synchronized(sipGatewayListeners)
        {
            sipGatewayListeners.remove(listener);
        }
    }

    /**
     * Delivers event that new GatewaySession was added to active sessions.
     * @param session the session that was added.
     */
    private void fireGatewaySessionAdded(GatewaySession session)
    {
        Iterable<SipGatewayListener> listeners;
        synchronized (sipGatewayListeners)
        {
            listeners
                = new ArrayList<>(sipGatewayListeners);
        }

        for (SipGatewayListener listener : listeners)
        {
            listener.onSessionAdded(session);
        }
    }

    /**
     * Delivers event that a GatewaySession was removed from active sessions.
     * @param session the session that was removed.
     */
    private void fireGatewaySessionRemoved(GatewaySession session)
    {
        Iterable<SipGatewayListener> listeners;
        synchronized (sipGatewayListeners)
        {
            listeners
                = new ArrayList<>(sipGatewayListeners);
        }

        for (SipGatewayListener listener : listeners)
        {
            listener.onSessionRemoved(session);
        }
    }
}
