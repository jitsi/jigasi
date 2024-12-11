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
package org.jitsi.jigasi;

import java.util.*;
import net.java.sip.communicator.impl.certificate.*;
import net.java.sip.communicator.impl.configuration.*;
import net.java.sip.communicator.impl.credentialsstorage.*;
import net.java.sip.communicator.impl.dns.*;
import net.java.sip.communicator.impl.globaldisplaydetails.*;
import net.java.sip.communicator.impl.neomedia.*;
import net.java.sip.communicator.impl.netaddr.*;
import net.java.sip.communicator.impl.packetlogging.*;
import net.java.sip.communicator.impl.phonenumbers.*;
import net.java.sip.communicator.impl.protocol.jabber.*;
import net.java.sip.communicator.impl.protocol.sip.*;
import net.java.sip.communicator.impl.resources.*;
import net.java.sip.communicator.plugin.defaultresourcepack.*;
import net.java.sip.communicator.plugin.reconnectplugin.*;
import net.java.sip.communicator.service.gui.internal.*;
import net.java.sip.communicator.service.notification.*;
import net.java.sip.communicator.service.protocol.*;

import net.java.sip.communicator.service.protocol.media.*;
import net.java.sip.communicator.util.*;
import org.apache.commons.lang3.*;
import org.jitsi.impl.osgi.framework.launch.*;
import org.jitsi.jigasi.cmd.*;
import org.jitsi.jigasi.osgi.*;
import org.jitsi.jigasi.rest.*;
import org.jitsi.jigasi.version.*;
import org.jitsi.jigasi.xmpp.*;
import org.jitsi.meet.*;
import org.jitsi.osgi.framework.*;
import org.jitsi.service.configuration.*;
import org.jitsi.service.libjitsi.*;
import org.jitsi.service.neomedia.*;
import org.jivesoftware.smack.*;
import org.jivesoftware.smack.debugger.*;
import org.jivesoftware.smack.tcp.*;
import org.osgi.framework.*;
import org.osgi.framework.launch.*;
import org.osgi.framework.startlevel.*;

/**
 * The gateway for Jitsi Videobridge conferences. Requires one SIP
 * account to be configured in sip-communicator.properties file that will be
 * used for getting incoming calls and placing outgoing calls. Commands placing
 * outgoing calls can be received through XMPP connection, either a component
 * one or multiple XMPP client connections connecting to the so called brewery
 * rooms (MUCs). Currently after start the
 * conference held in {@link JvbConference} MUC is joined using a common client
 * connection. SIP account is used to dial {@link SipGateway} once we join
 * the conference or to receive the incoming calls.
 *
 * @author Pawel Domas
 * @author Damian Minkov
 */
public class Main
{
    /**
     * The name of the command-line argument which specifies the value of the
     * <tt>System</tt> property
     * {@link DefaultStreamConnector#MAX_PORT_NUMBER_PROPERTY_NAME}.
     */
    private static final String MAX_PORT_ARG_NAME = "--max-port";

    /**
     * The default value of the {@link #MAX_PORT_ARG_NAME} command-line argument
     * if it is not explicitly provided.
     */
    private static final int MAX_PORT_ARG_VALUE = 20000;

    /**
     * The name of the command-line argument which specifies the value of the
     * <tt>System</tt> property
     * {@link DefaultStreamConnector#MIN_PORT_NUMBER_PROPERTY_NAME}.
     */
    private static final String MIN_PORT_ARG_NAME = "--min-port";

    /**
     * The default value of the {@link #MIN_PORT_ARG_NAME} command-line argument
     * if
     * it is not explicitly provided.
     */
    private static final int MIN_PORT_ARG_VALUE = 10000;

    /**
     * The name of the command-line argument which specifies log folder to use.
     */
    private static final String LOGDIR_ARG_NAME = "--logdir";

    /**
     * The name of the property that stores the home dir for application log
     * files (not history).
     */
    public static final String PNAME_SC_LOG_DIR_LOCATION =
        "net.java.sip.communicator.SC_LOG_DIR_LOCATION";

    /**
     * The name of the property that stores the home dir for cache data, such
     * as avatars and spelling dictionaries.
     */
    public static final String PNAME_SC_CACHE_DIR_LOCATION =
        "net.java.sip.communicator.SC_CACHE_DIR_LOCATION";

    /**
     * The name of the command-line argument which specifies config folder to use.
     */
    private static final String CONFIG_DIR_ARG_NAME = "--configdir";

    /**
     * The name of the command-line argument which specifies config folder to use.
     */
    private static final String CONFIG_DIR_NAME_ARG_NAME = "--configdirname";

    /**
     * The name of the command-line argument which specifies that configuration
     * file is writable.
     */
    private static final String CONFIG_WRITABLE_ARG_NAME = "--configwritable";

    /**
     * A list of packages(iq providers) not used by jigasi.
     * Removing them from smack, removes a lot of weakHashMaps and
     * connection listeners.
     */
    private static final String[] disabledSmackPackages
        = new String[]
        {
            "org.jivesoftware.smackx.iqlast",
            "org.jivesoftware.smackx.bytestreams",
            "org.jivesoftware.smackx.filetransfer",
            "org.jivesoftware.smackx.hoxt",
            "org.jivesoftware.smackx.httpfileupload",
            "org.jivesoftware.smackx.iot",
            "org.jivesoftware.smackx.si",
            "org.jivesoftware.smackx.vcardtemp",
            "org.jivesoftware.smackx.xhtmlim",
            "org.jivesoftware.smackx.eme",
            "org.jivesoftware.smackx.iqprivate",
            "org.jivesoftware.smackx.bookmarks",
            "org.jivesoftware.smackx.receipts",
            "org.jivesoftware.smackx.commands",
            "org.jivesoftware.smackx.privacy",
            "org.jivesoftware.smackx.time",
            "org.jivesoftware.smackx.muc.bookmarkautojoin"
        };

    public static void main(String[] args)
        throws ParseException, BundleException, InterruptedException
    {
        // Parse the command-line arguments.
        CmdLine cmdLine = new CmdLine();

        cmdLine.parse(args);

        int maxPort
            = cmdLine.getIntOptionValue(
                    MAX_PORT_ARG_NAME, MAX_PORT_ARG_VALUE);

        int minPort
            = cmdLine.getIntOptionValue(
                    MIN_PORT_ARG_NAME, MIN_PORT_ARG_VALUE);

        // Jingle Raw UDP transport
        System.setProperty(
            DefaultStreamConnector.MAX_PORT_NUMBER_PROPERTY_NAME,
            String.valueOf(maxPort));
        // Jingle ICE-UDP transport
        System.setProperty(
            OperationSetBasicTelephony.MAX_MEDIA_PORT_NUMBER_PROPERTY_NAME,
            String.valueOf(maxPort));

        // Jingle Raw UDP transport
        System.setProperty(
            DefaultStreamConnector.MIN_PORT_NUMBER_PROPERTY_NAME,
            String.valueOf(minPort));
        // Jingle ICE-UDP transport
        System.setProperty(
            OperationSetBasicTelephony.MIN_MEDIA_PORT_NUMBER_PROPERTY_NAME,
            String.valueOf(minPort));

        // skips some unused signalling signalling
        System.setProperty(
            CallPeerJabberImpl.SKIP_DISCO_INFO_ON_SESSION_INITIATE,
            "true");
        System.setProperty(
            CallPeerJabberImpl.SKIP_RINGING_ON_SESSION_INITIATE,
            "true");

        // FIXME: properties used for debug purposes
        // jigasi-home will be create in current directory (from where the
        // process is launched). It must contain sip-communicator.properties
        // with one XMPP and one SIP account configured.
        String configDir
            = cmdLine.getOptionValue(
                    CONFIG_DIR_ARG_NAME, System.getProperty("user.dir"));

        System.setProperty(
            ConfigurationService.PNAME_SC_HOME_DIR_LOCATION, configDir);

        String configDirName
            = cmdLine.getOptionValue(CONFIG_DIR_NAME_ARG_NAME, "jigasi-home");

        System.setProperty(
            ConfigurationService.PNAME_SC_HOME_DIR_NAME,
            configDirName);

        boolean isConfigReadonly =
            !Boolean.parseBoolean(cmdLine.getOptionValue(CONFIG_WRITABLE_ARG_NAME));
        System.setProperty(
            ConfigurationService.PNAME_CONFIGURATION_FILE_IS_READ_ONLY,
            Boolean.toString(isConfigReadonly));

        String logdir = cmdLine.getOptionValue(LOGDIR_ARG_NAME);
        if (StringUtils.isNotEmpty(logdir))
        {
            System.setProperty(PNAME_SC_LOG_DIR_LOCATION, logdir);
            // set it same as cache dir so if something is written lets write it
            // there, currently only empty avatarcache folders, if something
            // is really needed to cache we can chanege it to /var/lib/jigasi
            // or something similar
            System.setProperty(PNAME_SC_CACHE_DIR_LOCATION, logdir);
        }

        // make sure we use the properties files for configuration
        System.setProperty(ConfigurationActivator.PNAME_USE_PROPFILE_CONFIG,
            "true");

        // reported to drop calls on asterisk, as it does not reply
        // to our re-invites
        System.setProperty("net.java.sip.communicator.impl.protocol.sip" +
                ".SKIP_REINVITE_ON_FOCUS_CHANGE_PROP",
            "true");

        // Those are not used so disable them
        String deviceSystemPackage = "org.jitsi.impl.neomedia.device";
        System.setProperty(deviceSystemPackage + ".MacCoreaudioSystem.disabled", "true");
        System.setProperty(deviceSystemPackage + ".PulseAudioSystem.disabled", "true");
        System.setProperty(deviceSystemPackage + ".PortAudioSystem.disabled", "true");

        System.setProperty("jdk.xml.entityExpansionLimit", "0");
        System.setProperty("jdk.xml.maxOccurLimit", "0");
        System.setProperty("jdk.xml.elementAttributeLimit", "524288");
        System.setProperty("jdk.xml.totalEntitySizeLimit", "0");
        System.setProperty("jdk.xml.maxXMLNameLimit", "524288");
        System.setProperty("jdk.xml.entityReplacementLimit", "0");

        // disable smack packages before loading smack
        disableSmackProviders();

        ReflectionDebuggerFactory.setDebuggerClass(JulDebugger.class);
        SmackConfiguration.setDefaultReplyTimeout(15000);
        // temporary workaround for stalled bosh connections that can block smack, will be removed in 4.5.
        SmackConfiguration.TRUELY_ASYNC_SENDS = true;

        // Disable stream management as it could lead to unexpected behaviour,
        // because we do not account for that to happen.
        XMPPTCPConnection.setUseStreamManagementDefault(false);
        XMPPTCPConnection.setUseStreamManagementResumptionDefault(false);

        JigasiBundleConfig.setSystemPropertyDefaults();
        List<Class<? extends BundleActivator>> protocols = List.of(
            SipActivator.class,
            JabberActivator.class);
        var fw = start(protocols);

        // register shutdown service so we can cleanly stop the framework
        fw.getBundleContext().registerService(
            ShutdownService.class, () -> {
                try
                {
                    fw.stop();

                    // give some time to tear down everything before exiting
                    Thread.sleep(3000);

                    System.exit(0);
                }
                catch(Exception e)
                {
                    e.printStackTrace();

                    System.exit(-1);
                }
            }, null);
        fw.waitForStop(0);
    }

    public static Framework start(List<Class<? extends BundleActivator>> protocols)
        throws BundleException, InterruptedException
    {
        List<Class<? extends BundleActivator>> activators = new ArrayList<>(List.of(
            LibJitsiActivator.class,
            ConfigurationActivator.class,
            UtilActivator.class,
            DefaultResourcePackActivator.class,
            ResourceManagementActivator.class,
            NotificationServiceActivator.class,
            DnsUtilActivator.class,
            CredentialsStorageActivator.class,
            NetaddrActivator.class,
            PacketLoggingActivator.class,
            GuiServiceActivator.class,
            ProtocolMediaActivator.class,
            NeomediaActivator.class,
            CertificateVerificationActivator.class,
            VersionActivator.class,
            ProtocolProviderActivator.class,
            GlobalDisplayDetailsActivator.class,
            ReconnectPluginActivator.class,
            PhoneNumberServiceActivator.class,
            EmptyHidServiceActivator.class,
            EmptyUiServiceActivator.class,
            EmptyMasterPasswordInputServiceActivator.class
        ));
        activators.addAll(protocols);
        activators.addAll(List.of(
            JigasiBundleActivator.class,
            RESTBundleActivator.class,
            TranscriptServerBundleActivator.class,
            CallControlMucActivator.class
        ));
        var options = new HashMap<String, String>();
        options.put(Constants.FRAMEWORK_BEGINNING_STARTLEVEL, "3");
        Framework fw = new FrameworkImpl(options, Main.class.getClassLoader());
        fw.init();
        var bundleContext = fw.getBundleContext();
        for (Class<? extends BundleActivator> activator : activators)
        {
            var url = activator.getProtectionDomain().getCodeSource().getLocation().toString();
            var bundle = bundleContext.installBundle(url);
            var startLevel = bundle.adapt(BundleStartLevel.class);
            startLevel.setStartLevel(2);
            var bundleActivator = bundle.adapt(BundleActivatorHolder.class);
            bundleActivator.addBundleActivator(activator);
        }
        fw.start();
        return fw;
    }

    /**
     * Disables some unused smack packages.
     */
    private static void disableSmackProviders()
    {
        for (String classPackage: disabledSmackPackages)
        {
            SmackConfiguration.addDisabledSmackClass(classPackage);
        }
    }
}
