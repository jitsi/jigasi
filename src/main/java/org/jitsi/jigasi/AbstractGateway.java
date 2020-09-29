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
import net.java.sip.communicator.util.*;
import org.jxmpp.jid.*;
import org.osgi.framework.*;

import java.util.*;

/**
 * An abstract Gateway which can join an jvb conference with an xmpp account
 *
 * Manages {@link AbstractGatewaySession}'s which will do something with
 * information retrieved from jvb conference which is joined
 *
 * @author Pawel Domas
 * @author Nik Vaessen
 */
public abstract class AbstractGateway<T extends AbstractGatewaySession>
    implements GatewaySessionListener<T>
{
    /**
     * The logger
     */
    private final static Logger logger
        = Logger.getLogger(AbstractGateway.class);

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
     * A map which matches CallContext to the specific session of a Gateway.
     */
    private final Map<CallContext, T> sessions = new HashMap<>();

    /**
     * The (OSGi) <tt>BundleContext</tt> in which this <tt>AbstractGateway</tt>
     * has been started.
     */
    private BundleContext bundleContext;

    /**
     * Listeners that will be notified of changes in a Gateway.
     */
    private final ArrayList<GatewayListener> gatewayListeners
        = new ArrayList<>();

    /**
     * Creates new instance of an <tt>AbstractGateway</tt>.
     */
    public AbstractGateway(BundleContext bundleContext)
    {
        this.bundleContext = bundleContext;
    }

    /**
     * This method should handle stopping this gateway.
     */
    public abstract void stop();

    /**
     * Whether this gateway is ready to create sessions.
     * @return whether this gateway is ready to create sessions.
     */
    public abstract boolean isReady();

    /**
     * Notified that current call has ended.
     *
     * @param callContext the context of the call ended.
     */
    void notifyCallEnded(CallContext callContext)
    {
        T session;

        synchronized (sessions)
        {
            session = sessions.remove(callContext);

            if (session == null)
            {
                logger.error(
                    callContext + " Call resource not exists for session.");
                return;
            }
        }

        fireGatewaySessionRemoved(session);

        logger.info(callContext
            + " Removed session for call. Sessions:" + sessions.size());
    }

    /**
     * This method should starts a new outgoing session which should join a JVB
     * conference held in given MUC room.
     *
     * @param ctx the call context for which to create a call

     * @return the newly created GatewaySession.
     */
    public abstract T createOutgoingCall(CallContext ctx);

    /**
     * When a room is joined for incoming/outgoing calls we store the session
     * based on its call context and fire event that session had been added.
     *
     * @param source the {@link AbstractGatewaySession} on which the event takes
     *               place.
     */
    @Override
    public void onJvbRoomJoined(T source)
    {
        synchronized(sessions)
        {
            sessions.put(source.getCallContext(), source);
        }

        fireGatewaySessionAdded(source);
    }

    @Override
    public void onLobbyWaitReview(ChatRoom lobbyRoom)
    {}

    /**
     * Finds {@link AbstractGatewaySession} for given <tt>callResource</tt> if
     * one is currently active.
     *
     * @param callResource the call resource/URI of the
     *                     <tt>AbstractGatewaySession</tt> to be found.
     *
     * @return {@link AbstractGatewaySession} for given <tt>callResource</tt> if
     * there is one currently active or <tt>null</tt> otherwise.
     */
    public T getSession(Jid callResource)
    {
        synchronized (sessions)
        {
            for (Map.Entry<CallContext, T> en
                : sessions.entrySet())
            {
                if (callResource.equals(en.getKey().getCallResource()))
                    return en.getValue();
            }

            return null;
        }
    }

    /**
     * @return the list of <tt>AbstractGatewaySession</tt>s currently active.
     */
    public List<T> getActiveSessions()
    {
        synchronized (sessions)
        {
            return new ArrayList<>(sessions.values());
        }
    }

    /**
     * Returns timeout for waiting for the JVB conference invite from the focus.
     */
    public static long getJvbInviteTimeout()
    {
        return JigasiBundleActivator.getConfigurationService()
                                    .getLong(P_NAME_JVB_INVITE_TIMEOUT,
                                             DEFAULT_JVB_INVITE_TIMEOUT);
    }

    /**
     * Sets new timeout for waiting for the JVB conference invite from the
     * focus.
     * @param newTimeout the new timeout value in ms to set.
     */
    public static void setJvbInviteTimeout(long newTimeout)
    {
        JigasiBundleActivator.getConfigurationService()
                             .setProperty(
                                     AbstractGateway.P_NAME_JVB_INVITE_TIMEOUT,
                                          newTimeout);
    }

    /**
     * Adds a listener that will be notified of changes in our status.
     *
     * @param listener a gateway status listener.
     */
    public void addGatewayListener(GatewayListener listener)
    {
        synchronized(gatewayListeners)
        {
            if (!gatewayListeners.contains(listener))
                gatewayListeners.add(listener);
        }
    }

    /**
     * Removes a listener that was being notified of changes in the status of
     * the AbstractGateway.
     *
     * @param listener a gateway status listener.
     */
    public void removeGatewayListener(GatewayListener listener)
    {
        synchronized(gatewayListeners)
        {
            gatewayListeners.remove(listener);
        }
    }

    /**
     * Delivers event that new GatewaySession was added to active sessions.
     * @param session the session that was added.
     */
    private void fireGatewaySessionAdded(AbstractGatewaySession session)
    {
        Iterable<GatewayListener> listeners;
        synchronized (gatewayListeners)
        {
            listeners
                = new ArrayList<>(gatewayListeners);
        }

        for (GatewayListener listener : listeners)
        {
            listener.onSessionAdded(session);
        }
    }

    /**
     * Delivers event that a GatewaySession was removed from active sessions.
     * @param session the session that was removed.
     */
    private void fireGatewaySessionRemoved(AbstractGatewaySession session)
    {
        Iterable<GatewayListener> listeners;
        synchronized (gatewayListeners)
        {
            listeners
                = new ArrayList<>(gatewayListeners);
        }

        for (GatewayListener listener : listeners)
        {
            listener.onSessionRemoved(session);
        }
    }

    /**
     * Delivers event that a GatewaySession had failed establishing.
     * Room joining failed.
     * @param session the session that failed.
     */
    void fireGatewaySessionFailed(AbstractGatewaySession session)
    {
        Iterable<GatewayListener> listeners;
        synchronized (gatewayListeners)
        {
            listeners
                = new ArrayList<>(gatewayListeners);
        }

        for (GatewayListener listener : listeners)
        {
            listener.onSessionFailed(session);
        }
    }

    /**
     * Delivers event that Gateway is ready.
     */
    void fireGatewayReady()
    {
        Iterable<GatewayListener> listeners;
        synchronized (gatewayListeners)
        {
            listeners
                = new ArrayList<>(gatewayListeners);
        }

        for (GatewayListener listener : listeners)
        {
            listener.onReady();
        }
    }

}
