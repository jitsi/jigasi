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
package org.jitsi.jigasi.xmpp;

import net.java.sip.communicator.impl.protocol.jabber.*;
import net.java.sip.communicator.plugin.reconnectplugin.*;
import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.protocol.event.*;
import net.java.sip.communicator.util.*;
import org.jitsi.jigasi.*;
import org.jitsi.jigasi.stats.*;
import org.jitsi.jigasi.util.*;
import org.jitsi.xmpp.extensions.rayo.*;
import org.jitsi.xmpp.extensions.rayo.RayoIqProvider.*;
import org.jitsi.service.configuration.*;
import org.jivesoftware.smack.*;
import org.jivesoftware.smack.iqrequest.*;
import org.jivesoftware.smack.packet.*;
import org.jivesoftware.smack.tcp.*;
import org.jxmpp.jid.parts.*;
import org.osgi.framework.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.*;

/**
 * Call control that is capable of utilizing Rayo XMPP protocol for the purpose
 * of SIP gateway calls management.
 * This is based on muc called brewery, where all Jigasi instances join and
 * publish their usage stats using <tt>ColibriStatsExtension</tt> in presence.
 * The focus use the stats to load balance between instances.
 *
 * @author Damian Minkov
 * @author Nik Vaessen
 */
public class CallControlMucActivator
    implements BundleActivator,
               ServiceListener,
               RegistrationStateChangeListener,
               GatewaySessionListener,
               GatewayListener
{
    /**
     * The logger
     */
    private final static Logger logger
        = Logger.getLogger(CallControlMucActivator.class);

    private static BundleContext osgiContext;

    /**
     * The account property to search in configuration service for the room name
     * where all xmpp providers will join.
     */
    public static final String ROOM_NAME_ACCOUNT_PROP = "BREWERY";

    /**
     * A property to enable or disable muc call control, disabled by default.
     */
    public static final String BREWERY_ENABLED_PROP
        = "org.jitsi.jigasi.BREWERY_ENABLED";

    /**
     * The call controlling logic.
     */
    private CallControl callControl = null;

    /**
     * Starts muc control component. Finds all xmpp accounts and listen for
     * new ones registered.
     * @param bundleContext the bundle context
     */
    @Override
    public void start(final BundleContext bundleContext)
        throws Exception
    {
        osgiContext = bundleContext;

        ConfigurationService config = getConfigurationService();

        if (!config.getBoolean(BREWERY_ENABLED_PROP, false))
        {
            logger.warn("MUC call control disabled.");
            return;
        }

        osgiContext.addServiceListener(this);

        Collection<ServiceReference<ProtocolProviderService>> refs
            = ServiceUtils.getServiceReferences(
                osgiContext,
                ProtocolProviderService.class);

        for (ServiceReference<ProtocolProviderService> ref : refs)
        {
            initializeNewProvider(osgiContext.getService(ref));
        }

        SipGateway sipGateway = ServiceUtils.getService(
            bundleContext, SipGateway.class);

        TranscriptionGateway transcriptionGateway = ServiceUtils.getService(
                bundleContext, TranscriptionGateway.class);

        this.callControl = new CallControl(config);

        if (sipGateway != null)
        {
            sipGateway.addGatewayListener(this);
            this.callControl.setSipGateway(sipGateway);
        }
        if (transcriptionGateway != null)
        {
            transcriptionGateway.addGatewayListener(this);
            this.callControl.setTranscriptionGateway(transcriptionGateway);
        }
    }

    /**
     * Stops and unregister everything - providers, listeners.
     * @param bundleContext the bundle context
     */
    @Override
    public void stop(BundleContext bundleContext)
        throws Exception
    {
        osgiContext.removeServiceListener(this);

        Collection<ServiceReference<ProtocolProviderService>> refs
            = ServiceUtils.getServiceReferences(
                osgiContext,
                ProtocolProviderService.class);

        for (ServiceReference<ProtocolProviderService> ref : refs)
        {
            ProtocolProviderService pps = osgiContext.getService(ref);
            if (ProtocolNames.JABBER.equals(pps.getProtocolName()))
            {
                try
                {
                    pps.unregister();
                }
                catch(OperationFailedException e)
                {
                    logger.error("Cannot unregister xmpp provider", e);
                }
            }
        }
    }

    /**
     * Returns <tt>ConfigurationService</tt> instance.
     * @return <tt>ConfigurationService</tt> instance.
     */
    private static ConfigurationService getConfigurationService()
    {
        return ServiceUtils.getService(
            osgiContext, ConfigurationService.class);
    }

    /**
     * Returns timeout value for room join waiting.
     */
    public static long getMucJoinWaitTimeout()
    {
        return JigasiBundleActivator.getConfigurationService()
                                    .getLong(JigasiBundleActivator.P_NAME_MUC_JOIN_TIMEOUT,
                                             JigasiBundleActivator.MUC_JOIN_TIMEOUT_DEFAULT_VALUE);
    }

    @Override
    public void serviceChanged(ServiceEvent serviceEvent)
    {
        if (serviceEvent.getType() != ServiceEvent.REGISTERED)
            return;

        ServiceReference<?> ref = serviceEvent.getServiceReference();

        Object service = osgiContext.getService(ref);

        if (service instanceof ProtocolProviderService)
        {
            initializeNewProvider((ProtocolProviderService) service);
        }
        else if (service instanceof SipGateway)
        {
            SipGateway gateway = (SipGateway) service;
            gateway.addGatewayListener(this);

            if (this.callControl == null)
            {
                this.callControl = new CallControl(
                        gateway, getConfigurationService());
            }
            else
            {
                this.callControl.setSipGateway(gateway);
            }
        }
        else if (service instanceof TranscriptionGateway)
        {
            TranscriptionGateway gateway = (TranscriptionGateway) service;
            gateway.addGatewayListener(this);

            if (this.callControl == null)
            {
                this.callControl = new CallControl(
                        gateway, getConfigurationService());
            }
            else
            {
                this.callControl.setTranscriptionGateway(gateway);
            }
        }
    }

    /**
     * Initialize newly registered into osgi protocol provider.
     * @param pps an xmpp protocol provider
     */
    private void initializeNewProvider(ProtocolProviderService pps)
    {
        // we are interested only in XMPP accounts with BREWERY property
        if (!ProtocolNames.JABBER.equals(pps.getProtocolName())
            || pps.getAccountID()
                .getAccountPropertyString(ROOM_NAME_ACCOUNT_PROP) == null)
        {
            return;
        }

        pps.addRegistrationStateChangeListener(this);

        ProtocolProviderFactory xmppProviderFactory
            = ProtocolProviderFactory.getProtocolProviderFactory(
                osgiContext, ProtocolNames.JABBER);

        new RegisterThread(
            pps, xmppProviderFactory.loadPassword(pps.getAccountID()))
                .start();
    }

    /**
     * When a xmpp provder is registered to xmpp server we join it to the
     * common room.
     *
     * @param evt the registration event
     */
    @Override
    public void registrationStateChanged(RegistrationStateChangeEvent evt)
    {
        ProtocolProviderService provider = evt.getProvider();

        if (logger.isDebugEnabled()
            && provider instanceof ProtocolProviderServiceJabberImpl
            && provider.getAccountID()
                .getAccountPropertyString(ROOM_NAME_ACCOUNT_PROP) != null)
        {
            logger.debug("Got control muc provider " + provider
                + " new state -> " + evt.getNewState() );
        }

        if (evt.getNewState() == RegistrationState.REGISTERED)
        {
            joinCommonRoom(provider);
        }
        else if (evt.getNewState() == RegistrationState.UNREGISTERING)
        {
            leaveCommonRoom(provider);
        }
    }

    /**
     * Joins the common control room.
     * @param pps the provider to join.
     */
    private void joinCommonRoom(ProtocolProviderService pps)
    {
        String roomName = pps.getAccountID()
            .getAccountPropertyString(ROOM_NAME_ACCOUNT_PROP);

        try
        {
            logger.info(
                "Joining call control room: " + roomName + " pps:" + pps);
            Resourcepart connectionResource = null;

            // getting direct access to the xmpp connection in order to add
            // a listener for incoming iqs
            if (pps instanceof ProtocolProviderServiceJabberImpl)
            {
                // we do not care for removing the packet listener
                // as its added to the connection, if the protocol provider
                // gets disconnected and connects again it should create new
                // connection and scrap old
                XMPPConnection conn =
                    ((ProtocolProviderServiceJabberImpl) pps).getConnection();
                conn.registerIQRequestHandler(new DialIqHandler(pps));
                conn.registerIQRequestHandler(new HangUpIqHandler(pps));

                connectionResource = conn.getUser().getResourceOrNull();
            }

            OperationSetMultiUserChat muc
                = pps.getOperationSet(OperationSetMultiUserChat.class);

            ChatRoom mucRoom = muc.findRoom(roomName);
            if (connectionResource != null)
            {
                mucRoom.joinAs(connectionResource.toString());
            }
            else
            {
                mucRoom.join();
            }

            // sends initial stats, used some kind of advertising
            // so jicofo can recognize us as real jigasi and load balance us
            Statistics.updatePresenceStatusForXmppProviders(
                Collections.singletonList(pps));
        }
        catch (Exception e)
        {
            logger.error(e, e);
        }
    }

    /**
     * Leaves the common control room.
     * @param pps the provider to leave.
     */
    private void leaveCommonRoom(ProtocolProviderService pps)
    {
        String roomName = pps.getAccountID()
            .getAccountPropertyString(ROOM_NAME_ACCOUNT_PROP);

        if (roomName == null)
        {
            // this is the common account for connecting which is configured
            // to use authorization
            return;
        }

        try
        {
            logger.info(
                "Leaving call control room: " + roomName + " pps:" + pps);

            OperationSetMultiUserChat muc
                = pps.getOperationSet(OperationSetMultiUserChat.class);

            ChatRoom mucRoom = muc.findRoom(roomName);
            if (mucRoom != null)
            {
                mucRoom.leave();
            }
        }
        catch (Exception e)
        {
            logger.error(e, e);
        }
    }

    @Override
    public void onJvbRoomJoined(AbstractGatewaySession source)
    {
        updatePresenceStatusForXmppProviders();
    }

    @Override
    public void onSessionAdded(AbstractGatewaySession session)
    {
        session.addListener(this);

        // We have to check if we can update anything here (stats),
        // cause session was just created and  might not have joined the jvb
        // room, which means there is no change in participant count yet
        if(session.isInTheRoom())
        {
            updatePresenceStatusForXmppProviders();
        }
    }

    @Override
    public void onSessionRemoved(AbstractGatewaySession session)
    {
        updatePresenceStatusForXmppProviders();
        session.removeListener(this);
    }

    /**
     * Updates the presence in each {@link ProtocolProviderService} registered
     * with OSGi with the current number of conferences and participants.
     */
    private void updatePresenceStatusForXmppProviders()
    {
        Statistics.updatePresenceStatusForXmppProviders();
    }

    /**
     * Adds new call control MUC xmpp account using the provided props.
     *
     * @param id the id to use for this account(should not contain .).
     * @param properties the properties.
     * @throws OperationFailedException if failed loading account.
     */
    public synchronized static void addCallControlMucAccount(
            String id,
            Map<String, String> properties)
        throws OperationFailedException
    {
        if (listCallControlMucAccounts().contains(id))
        {
            logger.warn("Account already exists id:" + id);
            return;
        }

        ConfigurationService config
            = JigasiBundleActivator.getConfigurationService();
        ProtocolProviderFactory xmppProviderFactory
            = ProtocolProviderFactory.getProtocolProviderFactory(
                osgiContext, ProtocolNames.JABBER);
        AccountManager accountManager
            = ProtocolProviderActivator.getAccountManager();
        AccountID xmppAccount
            = xmppProviderFactory.createAccount(properties);

        // A small workaround to make sure we use the id for the
        // account that is provided, this is in case we want to
        // delete the account by the same id.
        // We use the fact that account manager will reuse the id if found
        // to store the properties (like edit of an account).
        {
            String accountConfigPrefix = accountManager
                .getFactoryImplPackageName(xmppProviderFactory) + "." + id;

            config.setProperty(accountConfigPrefix, id);
            config.setProperty(
                accountConfigPrefix
                    + "." + ProtocolProviderFactory.ACCOUNT_UID,
                xmppAccount.getAccountUniqueID());
        }

        // we force the provider to stick and try reconnecting
        // it may happen that the server is in the process of spinning up
        // and we need to be patient
        config.setProperty(
            ReconnectPluginActivator.ATLEAST_ONE_CONNECTION_PROP + "."
                + xmppAccount.getAccountUniqueID(),
            Boolean.TRUE.toString());

        accountManager.loadAccount(xmppAccount);

        logger.info("Added new control muc account:" + id
            + " -> " + xmppAccount);
    }

    /**
     * Removes call control MUC xmpp account identified by the passed id.
     * @param id the id of the account to delete.
     * @return {@code true} if the account with the specified ID was removed.
     * Otherwise returns {@code false}.
     */
    public synchronized static boolean removeCallControlMucAccount(String id)
    {
        ConfigurationService config
            = JigasiBundleActivator.getConfigurationService();
        ProtocolProviderFactory xmppProviderFactory
            = ProtocolProviderFactory.getProtocolProviderFactory(
                osgiContext, ProtocolNames.JABBER);
        AccountManager accountManager
            = ProtocolProviderActivator.getAccountManager();

        String accountIDStr = config.getString(
            accountManager.getFactoryImplPackageName(xmppProviderFactory)
                + "." + id + "." + ProtocolProviderFactory.ACCOUNT_UID);

        AccountID accountID = accountManager.getStoredAccounts().stream()
            .filter(a -> a.getAccountUniqueID().equals(accountIDStr))
            .findFirst().orElse(null);

        if (accountID != null)
        {
            ServiceReference<ProtocolProviderService> serRef
                = xmppProviderFactory.getProviderForAccount(accountID);
            if (serRef != null)
            {
                try
                {
                    osgiContext.getService(serRef).unregister(true);
                }
                catch(OperationFailedException e)
                {
                    logger.error("Error unregistering provider "
                        + id + " / " + accountID);
                }
            }

            boolean result
                = xmppProviderFactory.uninstallAccount(accountID);
            logger.info("Removing muc control account: "
                + id + ", " + accountID + ", successful:" + result);

            // cleanup
            config.removeProperty(
                ReconnectPluginActivator.ATLEAST_ONE_CONNECTION_PROP + "."
                    + accountID.getAccountUniqueID());

            return result;
        }
        else
        {
            logger.warn("No muc control account found for removing id: " + id);
            return false;
        }
    }

    /**
     * Returns call control MUC xmpp accounts that are currently configured.
     * @return lst of ids of call control MUC xmpp accounts.
     */
    public synchronized static List<String> listCallControlMucAccounts()
    {
        ConfigurationService config
            = JigasiBundleActivator.getConfigurationService();
        ProtocolProviderFactory xmppProviderFactory
            = ProtocolProviderFactory.getProtocolProviderFactory(
            osgiContext, ProtocolNames.JABBER);
        AccountManager accountManager
            = ProtocolProviderActivator.getAccountManager();

        String propPrefix
            = accountManager.getFactoryImplPackageName(xmppProviderFactory);

        return
            config.getPropertyNamesByPrefix(propPrefix, false)
            .stream()
            .filter(p -> p.endsWith(ROOM_NAME_ACCOUNT_PROP))
            .map(p -> p.substring(
                propPrefix.length() + 1, // the prefix and '.'
                p.indexOf(ROOM_NAME_ACCOUNT_PROP) - 1)) // property and the '.'
            .collect(Collectors.toList());
    }

    /**
     * Packet listener per protocol provider. Used to get the custom
     * bosh URL property from its account properties and to pass it when
     * processing incoming iq.
     */
    private class DialIqHandler
        extends RayoIqHandler<DialIq>
    {
        DialIqHandler(ProtocolProviderService pps)
        {
            super(DialIq.ELEMENT_NAME, IQ.Type.set, pps);
        }

        @Override
        public IQ processIQ(DialIq packet, CallContext ctx)
        {
            if (logger.isDebugEnabled())
            {
                logger.debug("Processing a RayoIq: " + packet.toXML());
            }

            try
            {
                AbstractGatewaySession[] session = { null };
                RefIq resultIQ = callControl.handleDialIq(packet, ctx, session);

                if(session[0] != null)
                    setDialResponseAndRegisterHangUpHandler(resultIQ,
                        session[0]);

                return resultIQ;
            }
            catch (CallControlAuthorizationException ccae)
            {
                return ccae.getErrorIq();
            }
            catch (Exception e)
            {
                logger.error("Error processing RayoIq", e);
                return IQ.createErrorResponse(packet, XMPPError.from(
                    XMPPError.Condition.internal_server_error, e.getMessage()));
            }
        }

        /**
         * Replaces the uri in RefIq response with the address of the MUC
         * participant.
         *
         * This way the participant can receive hangup IQs, listen and handle
         * them.
         *
         * We do not care for removing handlers because they are added to
         * the xmpp protocol provider which will be unregistered and removed
         * by OSGi.
         *
         * @param response the response to modify.
         * @param session the session created that needs that response.
         */
        private void setDialResponseAndRegisterHangUpHandler(
            RefIq response, final AbstractGatewaySession session)
            throws Exception
        {
            // we need the room from the session, if we haven't joined
            // the room yet, let's wait for it for some time
            WaitToJoinRoom waiter = new WaitToJoinRoom();
            try
            {
                session.addListener(waiter);
                if (!session.isInTheRoom())
                {
                    waiter.waitToJoinRoom();
                }
            }
            finally
            {
                session.removeListener(waiter);
            }

            ChatRoom room = session.getJvbChatRoom();
            response.setUri(
                "xmpp:" + room.getIdentifier() + "/" + room.getUserNickname());

            final XMPPConnection roomConnection
                = ((ProtocolProviderServiceJabberImpl) room.getParentProvider())
                    .getConnection();
            roomConnection.registerIQRequestHandler(
                new HangUpIqHandler(room.getParentProvider()));
        }
    }

    /**
     * Listener which waits to join a room or timeouts.
     */
    private class WaitToJoinRoom
        implements GatewaySessionListener
    {
        /**
         * The countdown we wait.
         */
        private final CountDownLatch countDownLatch = new CountDownLatch(1);

        @Override
        public void onJvbRoomJoined(AbstractGatewaySession source)
        {
            countDownLatch.countDown();
        }

        /**
         * Waits till the on JvbRoomJoined listener is fired or a timeout is
         * reached.
         * @throws Exception when timeout is reached.
         */
        public void waitToJoinRoom()
            throws Exception
        {
            try
            {
                if (!countDownLatch.await(getMucJoinWaitTimeout(), TimeUnit.SECONDS))
                {
                    throw new Exception("Fail to join muc!");
                }
            }
            catch (InterruptedException e)
            {
                logger.error(e);
            }
        }
    }

    private class HangUpIqHandler
        extends RayoIqHandler<HangUp>
    {
        HangUpIqHandler(ProtocolProviderService pps)
        {
            super(HangUp.ELEMENT_NAME, IQ.Type.set, pps);
        }

        @Override
        public IQ processIQ(HangUp iqRequest, CallContext ctx)
        {
            final AbstractGatewaySession session =
                callControl.getSession(ctx.getCallResource());
            session.hangUp();
            return IQ.createResultIQ(iqRequest);
        }
    }

    private abstract class RayoIqHandler<T extends RayoIq>
        extends AbstractIqRequestHandler
    {
        private ProtocolProviderService pps;

        RayoIqHandler(String element, IQ.Type type, ProtocolProviderService pps)
        {
            super(element, RayoIqProvider.NAMESPACE, type, Mode.sync);
            this.pps = pps;
        }

        @Override
        @SuppressWarnings("unchecked")
        public final IQ handleIQRequest(IQ iqRequest)
        {
            AccountID acc = pps.getAccountID();

            final CallContext ctx = new CallContext(pps);
            ctx.setDomain(acc.getAccountPropertyString(
                CallContext.DOMAIN_BASE_ACCOUNT_PROP));
            ctx.setBoshURL(acc.getAccountPropertyString(
                CallContext.BOSH_URL_ACCOUNT_PROP));
            ctx.setMucAddressPrefix(acc.getAccountPropertyString(
                CallContext.MUC_DOMAIN_PREFIX_PROP, "conference"));

            return processIQ((T)iqRequest, ctx);
        }

        protected abstract IQ processIQ(T iq, CallContext ctx);
    }
}
