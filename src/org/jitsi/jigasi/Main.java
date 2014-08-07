/*
 * Jitsi Videobridge, OpenSource video conferencing.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.jigasi;

import net.java.sip.communicator.service.protocol.*;
import org.jitsi.jigasi.osgi.*;
import org.jitsi.jigasi.xmpp.*;
import org.jitsi.service.configuration.*;
import org.jitsi.service.neomedia.*;
import org.jivesoftware.whack.*;
import org.osgi.framework.*;
import org.xmpp.component.*;

import java.util.logging.*;
import java.util.logging.Logger;

/**
 * FIXME: update description
 * SIP gateway for Jitsi Videobridge conferences. Requires one XMPP and one SIP
 * account to be configured in sip-communicator.properties file. JVB conference
 * must be held on the same server as XMPP account. Currently after start the
 * conference held in {@link JvbConference#CHAT_ROOM_NAME} MUC is joined.
 * SIP account is used to dial {@link SipGateway#SIP_PEER} once we join
 * the conference. Work in progress...
 *
 * @author Pawel Domas
 */
public class Main
{
    /**
     * The logger.
     */
    private static final Logger logger = Logger.getLogger(Main.class.getName());

    /**
     * The <tt>Object</tt> which synchronizes the access to the state related to
     * the decision whether the application is to exit. At the time of this
     * writing, the application just runs until it is killed.
     */
    private static final Object exitSyncRoot = new Object();

    /**
     * The name of the command-line argument which specifies the XMPP domain
     * to use.
     */
    private static final String DOMAIN_ARG_NAME = "--domain=";

    /**
     * The name of the command-line argument which specifies the IP address or
     * the name of the XMPP host to connect to.
     */
    private static final String HOST_ARG_NAME = "--host=";

    /**
     * The default value of the {@link #HOST_ARG_NAME} command-line argument if
     * it is not explicitly provided.
     */
    private static final String HOST_ARG_VALUE = "localhost";

    /**
     * The name of the command-line argument which specifies the value of the
     * <tt>System</tt> property
     * {@link DefaultStreamConnector#MAX_PORT_NUMBER_PROPERTY_NAME}.
     */
    private static final String MAX_PORT_ARG_NAME = "--max-port=";

    /**
     * The default value of the {@link #MAX_PORT_ARG_NAME} command-line argument
     * if it is not explicitly provided.
     */
    private static final String MAX_PORT_ARG_VALUE = "20000";

    /**
     * The name of the command-line argument which specifies the value of the
     * <tt>System</tt> property
     * {@link DefaultStreamConnector#MIN_PORT_NUMBER_PROPERTY_NAME}.
     */
    private static final String MIN_PORT_ARG_NAME = "--min-port=";

    /**
     * The default value of the {@link #MIN_PORT_ARG_NAME} command-line argument
     * if
     * it is not explicitly provided.
     */
    private static final String MIN_PORT_ARG_VALUE = "10000";

    /**
     * The name of the command-line argument which specifies the port of the
     * XMPP host to connect on.
     */
    private static final String PORT_ARG_NAME = "--port=";

    /**
     * The default value of the {@link #PORT_ARG_NAME} command-line argument if
     * it is not explicitly provided.
     */
    private static final int PORT_ARG_VALUE = 5347;

    /**
     * The name of the command-line argument which specifies the secret key for
     * the sub-domain of the Jabber component implemented by this application
     * with which it is to authenticate to the XMPP server to connect to.
     */
    private static final String SECRET_ARG_NAME = "--secret=";

    /**
     * The name of the command-line argument which specifies sub-domain name for
     * the videobridge component.
     */
    private static final String SUBDOMAIN_ARG_NAME = "--subdomain=";

    public static void main(String[] args)
    {
        // Parse the command-line arguments.
        String maxPort = MAX_PORT_ARG_VALUE;
        String minPort = MIN_PORT_ARG_VALUE;

        String host = null;
        int port = PORT_ARG_VALUE;
        String domain = null;
        String subdomain = null;
        String secret = null;

        for (String arg : args)
        {
            if (arg.startsWith(MAX_PORT_ARG_NAME))
            {
                maxPort = arg.substring(MAX_PORT_ARG_NAME.length());
            }
            else if (arg.startsWith(MIN_PORT_ARG_NAME))
            {
                minPort = arg.substring(MIN_PORT_ARG_NAME.length());
            }
            else if (arg.startsWith(DOMAIN_ARG_NAME))
            {
                domain = arg.substring(DOMAIN_ARG_NAME.length());
            }
            else if (arg.startsWith(HOST_ARG_NAME))
            {
                host = arg.substring(HOST_ARG_NAME.length());
            }
            else if (arg.startsWith(PORT_ARG_NAME))
            {
                port = Integer.parseInt(arg.substring(PORT_ARG_NAME.length()));
            }
            else if (arg.startsWith(SECRET_ARG_NAME))
            {
                secret = arg.substring(SECRET_ARG_NAME.length());
            }
            else if (arg.startsWith(SUBDOMAIN_ARG_NAME))
            {
                subdomain = arg.substring(SUBDOMAIN_ARG_NAME.length());
            }
        }

        if (host == null)
            host = (domain == null) ? HOST_ARG_VALUE : domain;

        if ((maxPort != null) && (maxPort.length() != 0))
        {
            // Jingle Raw UDP transport
            System.setProperty(
                DefaultStreamConnector.MAX_PORT_NUMBER_PROPERTY_NAME,
                maxPort);
            // Jingle ICE-UDP transport
            System.setProperty(
                OperationSetBasicTelephony
                    .MAX_MEDIA_PORT_NUMBER_PROPERTY_NAME,
                maxPort);
        }
        if ((minPort != null) && (minPort.length() != 0))
        {
            // Jingle Raw UDP transport
            System.setProperty(
                DefaultStreamConnector.MIN_PORT_NUMBER_PROPERTY_NAME,
                minPort);
            // Jingle ICE-UDP transport
            System.setProperty(
                OperationSetBasicTelephony
                    .MIN_MEDIA_PORT_NUMBER_PROPERTY_NAME,
                minPort);
        }

        // FIXME: properties used for debug purposes
        // jigasi-home will be create in current directory (from where the
        // process is launched). It must contain sip-communicator.properties
        // with one XMPP and one SIP account configured.
        System.setProperty(
            ConfigurationService.PNAME_SC_HOME_DIR_LOCATION,
            System.getProperty("user.dir"));
        System.setProperty(
            ConfigurationService.PNAME_SC_HOME_DIR_NAME,
            "jigasi-home");

        // FIXME: Always trust mode - prevent failures because there's no GUI
        // to ask the user, but do we always want to trust ?
        System.setProperty(
            "net.java.sip.communicator.service.gui.ALWAYS_TRUST_MODE_ENABLED",
            "true");

        /*
         * Start OSGi. It will invoke the application programming interfaces
         * (APIs) of Jitsi Videobridge. Each of them will keep the application
         * alive.
         */
        BundleActivator activator =
            new BundleActivator()
            {
                @Override
                public void start(BundleContext bundleContext)
                    throws Exception
                {
                    // TODO Auto-generated method stub
                }

                @Override
                public void stop(BundleContext bundleContext)
                    throws Exception
                {
                    // TODO Auto-generated method stub
                }
            };

        OSGi.start(activator);


        ExternalComponentManager componentManager
            = new ExternalComponentManager(host, port);

        componentManager.setSecretKey(subdomain, secret);
        if (domain != null)
            componentManager.setServerName(domain);

        CallControlComponent component
            = new CallControlComponent(subdomain, domain);

        boolean stop = false;

        try
        {
            componentManager.addComponent(subdomain, component);
        }
        catch (ComponentException e)
        {
            logger.log(Level.SEVERE, e.getMessage(), e);
            stop = true;
        }

        component.init();

        if (!stop)
        do
        {
            try
            {
                if(System.in.read() == 'q')
                    break;
                // FIXME: temporary fix when input is redirected and
                // being read all the time
                // Change completely shutdown method !!!
                Thread.sleep(100);
            }
            catch (Exception e)
            {
                logger.log(Level.SEVERE, e.getMessage(), e);
                break;
            }
        }
        while (true);

        OSGi.stop(activator);

        try
        {
            componentManager.removeComponent(subdomain);
        }
        catch (ComponentException e)
        {
            logger.log(Level.SEVERE, e.getMessage(), e);
        }
    }
}
