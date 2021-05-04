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
package org.jitsi.jigasi.osgi;

import java.util.stream.Collectors;
import net.java.sip.communicator.impl.certificate.CertificateVerificationActivator;
import net.java.sip.communicator.impl.configuration.ConfigurationActivator;
import net.java.sip.communicator.impl.credentialsstorage.CredentialsStorageActivator;
import net.java.sip.communicator.impl.dns.DnsUtilActivator;
import net.java.sip.communicator.impl.globaldisplaydetails.GlobalDisplayDetailsActivator;
import net.java.sip.communicator.impl.neomedia.NeomediaActivator;
import net.java.sip.communicator.impl.netaddr.NetaddrActivator;
import net.java.sip.communicator.impl.packetlogging.PacketLoggingActivator;
import net.java.sip.communicator.impl.protocol.jabber.*;

import net.java.sip.communicator.impl.protocol.sip.SipActivator;
import net.java.sip.communicator.impl.resources.ResourceManagementActivator;
import net.java.sip.communicator.impl.sysactivity.SysActivityActivator;
import net.java.sip.communicator.plugin.defaultresourcepack.*;
import net.java.sip.communicator.plugin.reconnectplugin.ReconnectPluginActivator;
import net.java.sip.communicator.service.gui.internal.GuiServiceActivator;
import net.java.sip.communicator.service.notification.NotificationServiceActivator;
import net.java.sip.communicator.service.protocol.ProtocolProviderActivator;
import net.java.sip.communicator.service.protocol.media.ProtocolMediaActivator;
import net.java.sip.communicator.util.UtilActivator;
import org.jitsi.impl.neomedia.*;
import org.jitsi.impl.neomedia.device.*;
import org.jitsi.impl.neomedia.transform.csrc.*;
import org.jitsi.impl.osgi.framework.launch.*;
import org.jitsi.jigasi.JigasiBundleActivator;
import org.jitsi.jigasi.rest.RESTBundleActivator;
import org.jitsi.jigasi.rest.TranscriptServerBundleActivator;
import org.jitsi.jigasi.version.VersionActivator;
import org.jitsi.jigasi.xmpp.CallControlMucActivator;
import org.jitsi.meet.*;
import org.jitsi.service.configuration.*;
import org.jitsi.service.libjitsi.LibJitsiActivator;
import org.jitsi.stats.media.*;

import java.util.*;

/**
 * Jigasi OSGi bundle config
 *
 * @author Pawel Domas
 */
public class JigasiBundleConfig
    extends OSGiBundleConfig
{
    /**
     * Indicates whether 'mock' protocol providers should be used instead of
     * original Jitsi protocol providers. For the purpose of unit testing.
     */
    private static boolean _useMockProtocols = false;

    /**
     * Indicates whether mock protocol providers should be used instead of
     * original Jitsi protocol providers.
     */
    public static boolean isUseMockProtocols()
    {
        return _useMockProtocols;
    }

    /**
     * Make OSGi use mock protocol providers instead of original Jitsi protocols
     * implementation.
     * @param useMockProtocols <tt>true</tt> if Jitsi protocol providers should
     *                         be replaced with mock version.
     */
    public static void setUseMockProtocols(boolean useMockProtocols)
    {
        _useMockProtocols = useMockProtocols;
    }

    /**
     * The locations of the OSGi bundles (or rather of the class files of their
     * <tt>BundleActivator</tt> implementations) comprising Jitsi Videobridge.
     * An element of the <tt>BUNDLES</tt> array is an array of <tt>String</tt>s
     * and represents an OSGi start level.
     */
    @Override
    protected String[][] getBundlesImpl()
    {

        String[] protocols =
            {
                SipActivator.class.getName(),
                JabberActivator.class.getName(),
            };

        String[] mockProtocols =
            {
                "net/java/sip/communicator/service/protocol/mock/MockActivator"
            };

        String[][] bundles = {
            {
                LibJitsiActivator.class.getName(),
            },
            {
                ConfigurationActivator.class.getName(),
            },
            {
                UtilActivator.class.getName(),
            },
            {
                DefaultResourcePackActivator.class.getName(),
                ResourceManagementActivator.class.getName(),
            },
            {
                NotificationServiceActivator.class.getName(),
                DnsUtilActivator.class.getName(),
            },
            {
                CredentialsStorageActivator.class.getName(),
            },
            {
                NetaddrActivator.class.getName(),
            },
            {
                PacketLoggingActivator.class.getName(),
            },
            {
                GuiServiceActivator.class.getName(),
            },
            {
                ProtocolMediaActivator.class.getName(),
            },
            {
                NeomediaActivator.class.getName(),
            },
            {
                CertificateVerificationActivator.class.getName(),
            },
            {
                VersionActivator.class.getName(),
            },
            {
                ProtocolProviderActivator.class.getName(),
            },
            {
                GlobalDisplayDetailsActivator.class.getName(),
            },
            {
                ReconnectPluginActivator.class.getName(),
            },
            // Shall we use mock protocol providers ?
            _useMockProtocols ? mockProtocols : protocols,
            {
                JigasiBundleActivator.class.getName(),
            },
            {
                RESTBundleActivator.class.getName(),
            },
            {
                TranscriptServerBundleActivator.class.getName(),
            },
            {
                CallControlMucActivator.class.getName(),
            },
            {
                org.jitsi.ddclient.Activator.class.getName(),
            }
        };

        return Arrays.stream(bundles)
            .map(b -> Arrays.stream(b).map(bb -> bb.replace(".", "/")).toArray(String[]::new))
            .toArray(String[][]::new);
    }

    @Override
    protected Map<String, String> getSystemPropertyDefaults()
    {
        // FIXME: some threads must be kept alive that prevent JVM
        // from shutting down
        FrameworkImpl.killAfterShutdown = true;

        Map<String, String> defaults = super.getSystemPropertyDefaults();
        String true_ = Boolean.toString(true);
        String false_ = Boolean.toString(false);

        // Audio system should not be disabled
        defaults.put(
            MediaServiceImpl.DISABLE_AUDIO_SUPPORT_PNAME,
            false_);

        defaults.put(
            DeviceConfiguration.PROP_AUDIO_SYSTEM,
            AudioSystem.LOCATOR_PROTOCOL_AUDIOSILENCE);
        defaults.put(
            "org.jitsi.impl.neomedia.device.PortAudioSystem.disabled",
            true_);
        defaults.put(
            "org.jitsi.impl.neomedia.device.PulseAudioSystem.disabled",
            true_);

        // Disables COIN notifications
        defaults.put(
            OperationSetTelephonyConferencingJabberImpl.DISABLE_COIN_PROP_NAME,
            true_);

        // FIXME not sure about this one
        // It makes no sense for Jitsi Videobridge to pace its RTP output.
        defaults.put(
            DeviceConfiguration.PROP_VIDEO_RTP_PACING_THRESHOLD,
            Integer.toString(Integer.MAX_VALUE));

        /*
         * Drops silent audio packets that has the
         * rtp extension(rfc6464) with sound level information.
         */
        defaults.put(
            SsrcTransformEngine
                .DROP_MUTED_AUDIO_SOURCE_IN_REVERSE_TRANSFORM,
            true_);

        // override defaults with passed to the Main
        defaults.put(
            ConfigurationService.PNAME_CONFIGURATION_FILE_IS_READ_ONLY,
            System.getProperty(
                ConfigurationService.PNAME_CONFIGURATION_FILE_IS_READ_ONLY,
                true_));

        // java-stats(stats-java-sdk)
        Utils.getCallStatsJavaSDKSystemPropertyDefaults(defaults);

        return defaults;
    }
}
