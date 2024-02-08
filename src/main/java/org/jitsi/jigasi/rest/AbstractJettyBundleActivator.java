/*
 * Copyright @ 2015 - present, 8x8 Inc
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
package org.jitsi.jigasi.rest;

import net.java.sip.communicator.util.osgi.*;
import org.eclipse.jetty.server.*;
import org.eclipse.jetty.server.handler.*;
import org.eclipse.jetty.util.ssl.*;
import org.jitsi.rest.*;
import org.jitsi.service.configuration.*;
import org.jitsi.utils.logging.*;
import org.osgi.framework.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Implements an abstract {@code BundleActivator} which starts and stops a Jetty
 * HTTP(S) server instance within OSGi.
 *
 * @author Lyubomir Marinov
 */
public abstract class AbstractJettyBundleActivator
    extends DependentActivator
{
    /**
     * The {@code Logger} used by the {@code AbstractJettyBundleActivator} class
     * and its instances to print debug information.
     */
    private static final Logger logger
        = Logger.getLogger(AbstractJettyBundleActivator.class);

    static
    {
        // Allow the logging level of Jetty to be configured through the logger
        // of AbstractJettyBundleActivator.
        String jettyLogLevelProperty = "org.eclipse.jetty.LEVEL";

        if (System.getProperty(jettyLogLevelProperty) == null)
        {
            String jettyLogLevelValue;

            if (logger.isDebugEnabled())
                jettyLogLevelValue = "DEBUG";
            else if (logger.isInfoEnabled())
                jettyLogLevelValue = "INFO";
            else
                jettyLogLevelValue = null;
            if (jettyLogLevelValue != null)
                System.setProperty(jettyLogLevelProperty, jettyLogLevelValue);
        }
    }

    private ConfigurationService configService;

    protected ConfigurationService getConfigService()
    {
        return configService;
    }

    /**
     * Initializes a new {@code Handler} which handles HTTP requests by
     * delegating to a specific (consecutive) list of {@code Handler}s.
     *
     * @param handlers the (consecutive) list of {@code Handler}s to which the
     * new instance is to delegate
     * @return a new {@code Handler} which will handle HTTP requests by
     * delegating to the specified {@code handlers}
     */
    protected static Handler initializeHandlerList(List<Handler> handlers)
    {
        int handlerCount = handlers.size();

        if (handlerCount == 1)
        {
            return handlers.get(0);
        }
        else
        {
            HandlerList handlerList = new HandlerList();

            handlerList.setHandlers(
                handlers.toArray(new Handler[handlerCount]));
            return handlerList;
        }
    }

    /**
     * The config instance
     */
    protected final JettyBundleActivatorConfig config;

    /**
     * The Jetty {@code Server} which provides an HTTP(S) interface.
     */
    protected Server server;

    /**
     * Initializes a new {@code AbstractJettyBundleActivator} instance.
     *
     * @param legacyPropertyPrefix the prefix of the names of {@code ConfigurationService} and/or
     *                             {@code System} properties to be utilized by the new instance
     * @param newPropertyPrefix the prefix of the config property names in a new
     *                          config file
     */
    protected AbstractJettyBundleActivator(
        String legacyPropertyPrefix,
        String newPropertyPrefix)
    {
        super(ConfigurationService.class);
        this.config = new JettyBundleActivatorConfig(legacyPropertyPrefix, newPropertyPrefix);
    }

    /**
     * Notifies this {@code AbstractJettyBundleActivator} that a new Jetty
     * {@code Server} instance was initialized and started in a specific
     * {@code BundleContext}.
     *
     * @param bundleContext the {@code BundleContext} in which this
     * {@code BundleActivator} was started and initialized and started a new
     * Jetty {@code Server} instance
     */
    protected void didStart(BundleContext bundleContext)
        throws Exception
    {
    }

    /**
     * Notifies this {@code AbstractJettyBundleActivator} that the Jetty
     * {@code Server} instance associated with this instance was stopped and
     * released for garbage collection in a specific {@code BundleContext}.
     *
     * @param bundleContext the {@code BundleContext} in which this
     * {@code BundleActivator} was stopped
     */
    protected void didStop(BundleContext bundleContext)
        throws Exception
    {
    }

    /**
     * Initializes and starts a new Jetty {@code Server} instance in a specific
     * {@code BundleContext}.
     *
     * @param bundleContext the {@code BundleContext} in which this
     * {@code BundleActivator} is started and to initialize and start a new
     * Jetty {@code Server} instance
     */
    protected void doStart(BundleContext bundleContext)
        throws Exception
    {
        try
        {
            Server server = initializeServer(bundleContext);

            // The server will start a non-daemon background Thread which will
            // keep the application running on success.
            server.start();

            this.server = server;
        }
        catch (Exception e)
        {
            // Log any Throwable for debugging purposes and rethrow.
            logger.error(
                "Failed to initialize and/or start a new Jetty HTTP(S)"
                    + " server instance.",
                e);
            throw e;
        }
    }

    /**
     * Stops and releases for garbage collection the Jetty {@code Server}
     * instance associated with this instance in a specific
     * {@code BundleContext}.
     *
     * @param bundleContext the {@code BundleContext} in which this
     * {@code BundleActivator} is stopped
     */
    protected void doStop(BundleContext bundleContext)
        throws Exception
    {
        if (server != null)
        {
            server.stop();
            server = null;
        }
    }

    /**
     * @return the port which the configuration specifies should be used by
     * this {@link AbstractJettyBundleActivator}, or -1 if the configuration
     * specifies that this instance should be disabled.
     */
    private int getPort()
    {
        if (isTls())
        {
            return config.getTlsPort();
        }
        else
        {
            return config.getPort();
        }
    }

    /**
     * @return true if this instance is configured to use TLS, and false
     * otherwise.
     */
    protected boolean isTls()
    {
        return config.isTls();
    }

    /**
     * Initializes a new {@code Connector} instance to be added to a specific
     * {@code Server} which is to be started in a specific
     * {@code BundleContext}.
     *
     * @param server the {@code Server} to which the new {@code Connector}
     * instance is to be added
     * @return a new {@code Connector} instance which is to be added to
     * {@code server}
     */
    private Connector initializeConnector(Server server)
        throws Exception
    {
        HttpConfiguration httpCfg = new HttpConfiguration();

        httpCfg.setSecurePort(config.getTlsPort());
        httpCfg.setSecureScheme("https");

        Connector connector;

        // If HTTPS is not enabled, serve over HTTP.
        if (!isTls())
        {
            // HTTP
            connector = new ServerConnector(server, new HttpConnectionFactory(httpCfg));
        }
        else
        {
            // HTTPS
            File sslContextFactoryKeyStoreFile = Paths.get(Objects.requireNonNull(config.getKeyStorePath())).toFile();
            SslContextFactory.Server sslContextFactory = new SslContextFactory.Server();

            /* Mozilla Guideline v5.4, Jetty 9.4.15, intermediate configuration
               https://ssl-config.mozilla.org/#server=jetty&version=9.4.15&config=intermediate&guideline=5.4
               */
            /* TLS 1.3 requires Java 11 or later. */
            String version = System.getProperty("java.version");
            if (version.startsWith("1."))
            {
                version = version.substring(2, 3);
            }
            else
            {
                int dot = version.indexOf(".");
                if (dot != -1)
                {
                    version = version.substring(0, dot);
                }
            }
            int javaVersion = Integer.parseInt(version);

            if (javaVersion >= 11)
            {
                sslContextFactory.setIncludeProtocols("TLSv1.2", "TLSv1.3");
            }
            else
            {
                sslContextFactory.setIncludeProtocols("TLSv1.2");
            }
            sslContextFactory.setIncludeCipherSuites(
                "TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384",
                "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384",
                "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256",
                "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",
                "TLS_ECDHE_ECDSA_WITH_CHACHA20_POLY1305_SHA256",
                "TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256",
                "TLS_DHE_RSA_WITH_AES_256_GCM_SHA384",
                "TLS_DHE_RSA_WITH_AES_128_GCM_SHA256");

            sslContextFactory.setRenegotiationAllowed(false);
            if (config.getKeyStorePassword() != null)
            {
                sslContextFactory.setKeyStorePassword(config.getKeyStorePassword());
            }
            sslContextFactory.setKeyStorePath(sslContextFactoryKeyStoreFile.getPath());
            sslContextFactory.setNeedClientAuth(config.getNeedClientAuth());

            HttpConfiguration httpsCfg = new HttpConfiguration(httpCfg);

            httpsCfg.addCustomizer(new SecureRequestCustomizer());

            connector = new ServerConnector(
                server,
                new SslConnectionFactory(sslContextFactory, "http/1.1"),
                new HttpConnectionFactory(httpsCfg));
        }

        // port
        setPort(connector, getPort());

        // host
        if (config.getHost() != null)
        {
            setHost(connector, config.getHost());
        }

        return connector;
    }

    /**
     * Initializes a new {@link Handler} instance to be set on a specific
     * {@code Server} instance. The default implementation delegates to
     * {@link #initializeHandlerList(BundleContext, Server)}.
     *
     * @param bundleContext the {@code BundleContext} in which the new instance
     * is to be initialized
     * @param server the {@code Server} on which the new instance will be set
     * @return the new {code Handler} instance to be set on {@code server}
     */
    protected Handler initializeHandler(
        BundleContext bundleContext,
        Server server)
        throws Exception
    {
        return initializeHandlerList(bundleContext, server);
    }

    /**
     * Initializes a new {@link HandlerList} instance to be set on a specific
     * {@code Server} instance.
     *
     * @param bundleContext the {@code BundleContext} in which the new instance
     * is to be initialized
     * @param server the {@code Server} on which the new instance will be set
     * @return the new {code HandlerList} instance to be set on {@code server}
     */
    protected abstract Handler initializeHandlerList(
        BundleContext bundleContext,
        Server server)
        throws Exception;

    /**
     * Initializes a new {@code Server} instance to be started in a specific
     * {@code BundleContext}.
     *
     * @param bundleContext the {@code BundleContext} in which the new
     * {@code Server} instance is to be started
     * @return a new {@code Server} instance to be started in
     * {@code bundleContext}
     */
    protected Server initializeServer(BundleContext bundleContext)
        throws Exception
    {
        Server server = new Server();
        Connector connector = initializeConnector(server);

        server.addConnector(connector);

        Handler handler = initializeHandler(bundleContext, server);
        Handler metricsHandler = new MetricsHandler();

        HandlerCollection handlers = new HandlerCollection();
        if (handler != null)
        {
            handlers.addHandler(handler);
        }
        handlers.addHandler(metricsHandler);

        server.setHandler(handlers);

        return server;
    }

    /**
     * Sets the host on which a specific {@code Connector} is to listen for
     * incoming network connections.
     *
     * @param connector the {@code Connector} to set {@code host} on
     * @param host the host on which {@code connector} is to listen for incoming
     * network connections
     */
    protected void setHost(Connector connector, String host)
        throws Exception
    {
        // Provide compatibility with Jetty 8 and invoke the method
        // setHost(String) using reflection because it is in different
        // interfaces/classes in Jetty 8 and 9.
        connector
            .getClass()
            .getMethod("setHost", String.class)
            .invoke(connector, host);
    }

    /**
     * Sets the port on which a specific {@code Connector} is to listen for
     * incoming network connections.
     *
     * @param connector the {@code Connector} to set {@code port} on
     * @param port the port on which {@code connector} is to listen for incoming
     * network connections
     */
    protected void setPort(Connector connector, int port)
        throws Exception
    {
        // Provide compatibility with Jetty 8 and invoke the method setPort(int)
        // using reflection because it is in different interfaces/classes in
        // Jetty 8 and 9.
        connector
            .getClass()
            .getMethod("setPort", int.class)
            .invoke(connector, port);
    }

    /**
     * Starts this OSGi bundle in a specific {@code BundleContext}.
     *
     * @param bundleContext the {@code BundleContext} in which this OSGi bundle
     * is starting
     * @throws Exception if an error occurs while starting this OSGi bundle in
     * {@code bundleContext}
     */
    @Override
    public void startWithServices(BundleContext bundleContext)
        throws Exception
    {
        configService = getService(ConfigurationService.class);
        if (willStart(bundleContext))
        {
            doStart(bundleContext);
            didStart(bundleContext);
        }
        else
        {
            logger.info("Not starting the Jetty service for "
                + getClass().getName() + "(port=" + getPort() + ")");
        }
    }

    /**
     * Stops this OSGi bundle in a specific {@code BundleContext}.
     *
     * @param bundleContext the {@code BundleContext} in which this OSGi bundle
     * is stopping
     * @throws Exception if an error occurs while stopping this OSGi bundle in
     * {@code bundleContext}
     */
    @Override
    public void stop(BundleContext bundleContext)
        throws Exception
    {
        super.stop(bundleContext);
        if (willStop(bundleContext))
        {
            doStop(bundleContext);
            didStop(bundleContext);
        }
    }

    /**
     * Notifies this {@code AbstractJettyBundleActivator} that a new Jetty
     * {@code Server} instance is to be initialized and started in a specific
     * {@code BundleContext}.
     *
     * @param bundleContext the {@code BundleContext} in which this
     * {@code BundleActivator} is started and to initialize and start a new
     * Jetty {@code Server} instance
     * @return {@code true} if this {@code AbstractJettyBundleActivator} is to
     * continue and initialize and start a new Jetty {@code Server} instance;
     * otherwise, {@code false}
     */
    protected boolean willStart(BundleContext bundleContext)
        throws Exception
    {
        return getPort() > 0;
    }

    /**
     * Notifies this {@code AbstractJettyBundleActivator} that the Jetty
     * {@code Server} instance associated with this instance is to be stopped
     * and released for garbage collection in a specific {@code BundleContext}.
     *
     * @param bundleContext the {@code BundleContext} in which this
     * {@code BundleActivator} is stopped
     * @return {@code true} if this {@code AbstractJettyBundleActivator} is to
     * continue and stop the Jetty {@code Server} instance associated with this
     * instance; otherwise, {@code false}
     */
    protected boolean willStop(BundleContext bundleContext)
        throws Exception
    {
        return true;
    }
}
