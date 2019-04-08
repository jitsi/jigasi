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

import com.timgroup.statsd.*;
import org.jitsi.xmpp.extensions.*;
import org.jitsi.xmpp.extensions.jibri.*;
import org.jitsi.xmpp.extensions.jitsimeet.*;
import org.jitsi.xmpp.extensions.rayo.*;
import net.java.sip.communicator.service.gui.*;
import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.shutdown.*;
import net.java.sip.communicator.util.*;
import org.jitsi.jigasi.health.*;
import org.jitsi.jigasi.stats.*;
import org.jitsi.jigasi.xmpp.*;
import org.jitsi.service.configuration.*;
import org.jivesoftware.smack.provider.*;
import org.osgi.framework.*;

import java.util.*;

/**
 * Jigasi bundle activator. Registers {@link SipGateway} and waits for the first
 * SIP protocol provider service to be registered. Once SIP providers is
 * retrieved it is used by the sipGateway for establishing SIP calls.
 *
 * Also registers {@link TranscriptionGateway}, which will only join a room
 * when the DialIQ is "transcriber"
 *
 * @author Pawel Domas
 * @author Nik Vaessen
 */
public class JigasiBundleActivator
    implements BundleActivator,
               ServiceListener
{
    /**
     * The logger
     */
    private final static Logger logger
        = Logger.getLogger(JigasiBundleActivator.class);

    public static BundleContext osgiContext;

    /**
     * The property name for the boolean value whether transcription should be
     * enabled.
     */
    public final static String P_NAME_ENABLE_TRANSCRIPTION
        = "org.jitsi.jigasi.ENABLE_TRANSCRIPTION";

    /**
     * The property name for the boolean value whether SIP calls should be
     * enabled.
     */
    public final static String P_NAME_ENABLE_SIP
        = "org.jitsi.jigasi.ENABLE_SIP";

    /**
     * The default value for enabling transcription
     */
    public final static boolean ENABLE_TRANSCRIPTION_DEFAULT_VALUE = false;

    /**
     * The default value for enabling sip
     */
    public final static boolean ENABLE_SIP_DEFAULT_VALUE = true;

    /**
     * The Gateway which will manage bridging between a jvb conference and a sip
     * call
     */
    private static SipGateway sipGateway;

    /**
     * The Gateway which will manage a bridge between a jvb conference and a
     * transcription service
     */
    private static TranscriptionGateway transcriptionGateway;

    /**
     * Keep a list of available gateway instances
     * (sipGateway, transcriptionGateway).
     */
    private static List<AbstractGateway> gateways = new ArrayList<>();

    /**
     * Indicates if jigasi instance has entered graceful shutdown mode.
     */
    private static boolean shutdownInProgress;

    private UIServiceStub uiServiceStub = new UIServiceStub();

    /**
     * Returns <tt>ConfigurationService</tt> instance.
     * @return <tt>ConfigurationService</tt> instance.
     */
    public static ConfigurationService getConfigurationService()
    {
        return ServiceUtils.getService(
            osgiContext, ConfigurationService.class);
    }

    /**
     * Get whether transcription is currently enabled
     *
     * @return true if transcription is enabled, false otherwise
     */
    public static boolean isTranscriptionEnabled()
    {
        return JigasiBundleActivator.getConfigurationService()
            .getBoolean(P_NAME_ENABLE_TRANSCRIPTION,
                ENABLE_TRANSCRIPTION_DEFAULT_VALUE);
    }

    /**
     * Get whether sip is currently enabled
     *
     * @return true if sip is enabled, false otherwise
     */
    public static boolean isSipEnabled()
    {
        return JigasiBundleActivator.getConfigurationService()
            .getBoolean(P_NAME_ENABLE_SIP, ENABLE_SIP_DEFAULT_VALUE);
    }

    /**
     * Returns a {@link StatsDClient} instance to push statistics to datadog
     *
     * @return the {@link StatsDClient}
     */
    public static StatsDClient getDataDogClient()
    {
        return ServiceUtils.getService(osgiContext, StatsDClient.class);
    }

    @Override
    public void start(final BundleContext bundleContext)
        throws Exception
    {
        osgiContext = bundleContext;

        bundleContext.registerService(UIService.class, uiServiceStub, null);

        if(isSipEnabled())
        {
            logger.info("initialized SipGateway");
            sipGateway = new SipGateway(bundleContext)
            {
                @Override
                void notifyCallEnded(CallContext callContext)
                {
                    super.notifyCallEnded(callContext);

                    maybeDoShutdown();
                }
            };
            gateways.add(sipGateway);
            osgiContext.registerService(SipGateway.class, sipGateway, null);
        }
        else
        {
            logger.info("skipped initialization of SipGateway");
        }

        if (isTranscriptionEnabled())
        {
            logger.info("initialized TranscriptionGateway");
            transcriptionGateway = new TranscriptionGateway(bundleContext)
            {
                @Override
                void notifyCallEnded(CallContext callContext)
                {
                    super.notifyCallEnded(callContext);

                    maybeDoShutdown();
                }
            };
            gateways.add(transcriptionGateway);
            osgiContext.registerService(TranscriptionGateway.class,
                transcriptionGateway, null);
        }
        else
        {
            logger.info("skipped initialization of TranscriptionGateway");

        }

        bundleContext.addServiceListener(this);

        Collection<ServiceReference<ProtocolProviderService>> refs
            = ServiceUtils.getServiceReferences(
                    osgiContext,
                    ProtocolProviderService.class);

        for (ServiceReference<ProtocolProviderService> ref : refs)
        {
            ProtocolProviderService pps = osgiContext.getService(ref);

            if (ProtocolNames.SIP.equals(pps.getProtocolName()))
            {
                if (sipGateway != null)
                    sipGateway.setSipProvider(pps);
            }
        }

        Health.start();
    }

    @Override
    public void stop(BundleContext bundleContext)
        throws Exception
    {
        logger.info("Stopping JigasiBundleActivator");

        gateways.forEach(AbstractGateway::stop);
        gateways.clear();

        Health.stop();
    }

    @Override
    public void serviceChanged(ServiceEvent serviceEvent)
    {
        if (serviceEvent.getType() != ServiceEvent.REGISTERED)
        {
            return;
        }

        ServiceReference<?> ref = serviceEvent.getServiceReference();
        Object service = osgiContext.getService(ref);
        if (!(service instanceof ProtocolProviderService))
        {
            return;
        }

        // Register Jitsi Meet media presence extension.
        MediaPresenceExtension.registerExtensions();

        // Register Rayo IQs
        new RayoIqProvider().registerRayoIQs();

        // recording status, to detect recording start/stop
        ProviderManager.addExtensionProvider(
            RecordingStatus.ELEMENT_NAME,
            RecordingStatus.NAMESPACE,
            new DefaultPacketExtensionProvider<>(RecordingStatus.class)
        );

        ProtocolProviderService pps = (ProtocolProviderService) service;
        if (sipGateway != null && sipGateway.getSipProvider() == null &&
            ProtocolNames.SIP.equals(pps.getProtocolName()))
        {
            sipGateway.setSipProvider(pps);
        }
    }

    /**
     * Returns {@code true} if this instance has entered graceful shutdown mode.
     *
     * @return {@code true} if this instance has entered graceful shutdown mode;
     * otherwise, {@code false}
     */
    public static boolean isShutdownInProgress()
    {
        return shutdownInProgress;
    }

    /**
     * Enables graceful shutdown mode on this jigasi instance and eventually
     * starts the shutdown immediately if no conferences are currently being
     * hosted. Otherwise jigasi will shutdown once all conferences expire.
     */
    public static void enableGracefulShutdownMode()
    {
        if (!shutdownInProgress)
        {
            logger.info("Entered graceful shutdown mode");
        }
        shutdownInProgress = true;
        maybeDoShutdown();

        // lets send the status if using brewery
        if (getConfigurationService().getBoolean(
                CallControlMucActivator.BREWERY_ENABLED_PROP, false))
        {
            Statistics.updatePresenceStatusForXmppProviders();
        }
    }

    /**
     * Triggers the shutdown given that we're in graceful shutdown mode and
     * there are no conferences currently in progress.
     */
    private static void maybeDoShutdown()
    {
        if (!shutdownInProgress)
            return;

        List<AbstractGatewaySession> sessions = new ArrayList<>();
        gateways.forEach(gw -> sessions.addAll(gw.getActiveSessions()));

        if (sessions.isEmpty())
        {
            gateways.forEach(AbstractGateway::stop);

            ShutdownService shutdownService
                = ServiceUtils.getService(
                osgiContext,
                ShutdownService.class);

            logger.info("Jigasi is shutting down NOW");
            shutdownService.beginShutdown();
        }
    }

    /**
     * Returns the list of enabled gateways.
     * @return
     */
    public static List<AbstractGateway> getAvailableGateways()
    {
        return gateways;
    }
}
