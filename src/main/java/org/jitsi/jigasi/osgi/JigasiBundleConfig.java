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

import net.java.sip.communicator.impl.protocol.jabber.*;

import org.jitsi.impl.neomedia.*;
import org.jitsi.impl.neomedia.device.*;
import org.jitsi.impl.neomedia.transform.csrc.*;
import org.jitsi.impl.osgi.framework.launch.*;
import org.jitsi.meet.*;
import org.jitsi.service.configuration.*;
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
                "net/java/sip/communicator/impl/protocol/sip/SipActivator",
                "net/java/sip/communicator/impl/protocol/jabber/JabberActivator"
            };

        String[] mockProtocols =
            {
                "net/java/sip/communicator/service/protocol/mock/MockActivator"
            };

        String[][] bundles = {
            {
                "org/jitsi/service/libjitsi/LibJitsiActivator"
            },
            {
                "net/java/sip/communicator/util/UtilActivator",
                "net/java/sip/communicator/impl/fileaccess/FileAccessActivator"
            },
            {
                "net/java/sip/communicator/impl/configuration/ConfigurationActivator"
            },
            {
                "net/java/sip/communicator/impl/resources/ResourceManagementActivator"
            },
            {
                "net/java/sip/communicator/impl/dns/DnsUtilActivator"
            },
            {
                "net/java/sip/communicator/impl/credentialsstorage/CredentialsStorageActivator"
            },
            {
                "net/java/sip/communicator/impl/netaddr/NetaddrActivator",
                "net/java/sip/communicator/impl/sysactivity/SysActivityActivator"
            },
            {
                "net/java/sip/communicator/impl/packetlogging/PacketLoggingActivator"
            },
            {
                "net/java/sip/communicator/service/gui/internal/GuiServiceActivator"
            },
            {
                "net/java/sip/communicator/service/protocol/media/ProtocolMediaActivator"
            },
            {
                "net/java/sip/communicator/service/notification/NotificationServiceActivator"
            },
            {
                "net/java/sip/communicator/impl/neomedia/NeomediaActivator"
            },
            {
                "net/java/sip/communicator/impl/certificate/CertificateVerificationActivator"
            },
            {
                "org/jitsi/jigasi/version/VersionActivator"
            },
            {
                "net/java/sip/communicator/service/protocol/ProtocolProviderActivator"
            },
            {
                "net/java/sip/communicator/plugin/reconnectplugin/ReconnectPluginActivator"
            },
            // Shall we use mock protocol providers ?
            _useMockProtocols ? mockProtocols : protocols,
            {
                "org/jitsi/jigasi/JigasiBundleActivator"
            },
            {
                "org/jitsi/jigasi/rest/RESTBundleActivator"
            },
            {
                "org/jitsi/jigasi/rest/TranscriptServerBundleActivator"
            },
            {
                "org/jitsi/jigasi/xmpp/CallControlMucActivator"
            },
            {
                "org/jitsi/ddclient/Activator"
            }
        };

        return bundles;
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
