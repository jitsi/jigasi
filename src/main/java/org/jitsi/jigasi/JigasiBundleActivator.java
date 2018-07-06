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

import net.java.sip.communicator.impl.protocol.jabber.extensions.*;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jibri.*;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jitsimeet.*;
import net.java.sip.communicator.impl.protocol.jabber.extensions.rayo.*;
import net.java.sip.communicator.service.gui.*;
import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.util.*;
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
    private SipGateway sipGateway;

    /**
     * The Gateway which will manage a bridge between a jvb conference and a
     * transcription service
     */
    private TranscriptionGateway transcriptionGateway;

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

    @Override
    public void start(final BundleContext bundleContext)
        throws Exception
    {
        osgiContext = bundleContext;

        bundleContext.registerService(UIService.class, uiServiceStub, null);

        if(isSipEnabled())
        {
            logger.info("initialized SipGateway");
            sipGateway = new SipGateway(bundleContext);
            osgiContext.registerService(SipGateway.class, sipGateway, null);
        }
        else
        {
            logger.info("skipped initialization of SipGateway");
        }

        if(isTranscriptionEnabled())
        {
            logger.info("initialized TranscriptionGateway");
            transcriptionGateway = new TranscriptionGateway(bundleContext);
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
    }

    @Override
    public void stop(BundleContext bundleContext)
        throws Exception
    {
        logger.info("Stopping JigasiBundleActivator");

        if(sipGateway != null)
            sipGateway.stop();

        if(transcriptionGateway != null)
            transcriptionGateway.stop();
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
}
