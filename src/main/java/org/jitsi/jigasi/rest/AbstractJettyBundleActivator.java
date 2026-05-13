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
import org.eclipse.jetty.ee10.servlet.*;
import org.eclipse.jetty.server.*;
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
     */
    protected void didStart(BundleContext bundleContext)
        throws Exception
    {
    }

    /**
     * Notifies this {@code AbstractJettyBundleActivator} that the Jetty
     * {@code Server} instance associated with this instance was stopped and
     * released for garbage collection in a specific {@code BundleContext}.
     */
    protected void didStop(BundleContext bundleContext)
        throws Exception
    {
    }

    /**
     * Initializes and starts a new Jetty {@code Server} instance in a specific
     * {@code BundleContext}.
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

            sslContextFactory.setIncludeProtocols("TLSv1.2", "TLSv1.3");
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
        ((ServerConnector) connector).setPort(getPort());

        // host
        if (config.getHost() != null)
        {
            ((ServerConnector) connector).setHost(config.getHost());
        }

        return connector;
    }

    /**
     * Adds servlets to the {@link ServletContextHandler} which already has the
     * metrics servlet registered at {@code /metrics}. The default
     * implementation is a no-op. Subclasses may override to add their own
     * servlets.
     */
    protected void configureServletContextHandler(
        BundleContext bundleContext,
        Server server,
        ServletContextHandler context)
        throws Exception
    {
    }

    /**
     * Initializes an additional {@link Handler} to be combined with the
     * servlet context (which contains the metrics servlet and any servlets
     * added by {@link #configureServletContextHandler}). Subclasses may
     * override to return a non-servlet handler such as a
     * {@link org.eclipse.jetty.server.handler.ResourceHandler}.
     */
    protected Handler initializeHandlerList(
        BundleContext bundleContext,
        Server server)
        throws Exception
    {
        return null;
    }

    /**
     * Initializes a new {@code Server} instance to be started in a specific
     * {@code BundleContext}.
     */
    protected Server initializeServer(BundleContext bundleContext)
        throws Exception
    {
        Server server = new Server();
        Connector connector = initializeConnector(server);

        server.addConnector(connector);

        ServletContextHandler servletContext = new ServletContextHandler();
        servletContext.setContextPath("/");
        servletContext.addServlet(new ServletHolder(new MetricsHandler()), "/metrics");
        configureServletContextHandler(bundleContext, server, servletContext);

        Handler extra = initializeHandlerList(bundleContext, server);
        if (extra != null)
        {
            Handler.Sequence handlers = new Handler.Sequence();
            handlers.addHandler(extra);
            handlers.addHandler(servletContext);
            server.setHandler(handlers);
        }
        else
        {
            server.setHandler(servletContext);
        }

        return server;
    }

    /**
     * Starts this OSGi bundle in a specific {@code BundleContext}.
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
     */
    protected boolean willStop(BundleContext bundleContext)
        throws Exception
    {
        return true;
    }
}
