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
package org.jitsi.jigasi.rest;

import org.eclipse.jetty.server.*;
import org.jitsi.rest.*;
import org.osgi.framework.*;

/**
 * Implements <tt>BundleActivator</tt> for the OSGi bundle which implements a
 * REST API for Jigasi.
 * <p>
 * The REST API of Jigasi is currently served over HTTP on port
 * <tt>8788</tt> by default. The default port value may be overridden by the
 * <tt>System</tt> and <tt>ConfigurationService</tt> property with name
 * <tt>org.jitsi.jigasi.rest.jetty.port</tt>.
 * </p>
 *
 * @author Damian Minkov
 */
public class RESTBundleActivator
    extends AbstractJettyBundleActivator
{
    /**
     * The REST-like HTTP/JSON API of Jigasi.
     */
    public static final String REST_API = "rest";

    /**
     * The (base) <tt>System</tt> and/or <tt>ConfigurationService</tt> property
     * of the REST-like HTTP/JSON API of Jigasi.
     */
    public static final String REST_API_PNAME
        = "org.jitsi.jigasi." + REST_API;

    /**
     * The name of the <tt>System</tt> and <tt>ConfigurationService</tt>
     * boolean property which enables graceful shutdown through REST API.
     * It is disabled by default.
     */
    private static final String ENABLE_REST_SHUTDOWN_PNAME
        = "org.jitsi.jigasi.ENABLE_REST_SHUTDOWN";

    /**
     * Initializes a new {@code RESTBundleActivator} instance.
     */
    public RESTBundleActivator()
    {
        super(REST_API_PNAME);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Handler initializeHandlerList(BundleContext bundleContext,
        Server server) throws Exception
    {
        return new HandlerImpl(bundleContext,
            getCfgBoolean(ENABLE_REST_SHUTDOWN_PNAME, false));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected int getDefaultPort()
    {
        // a default port that is not clashing other components ports
        return 8788;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected int getDefaultTlsPort()
    {
        // a default port that is not clashing other components ports
        return 8743;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void doStop(BundleContext bundleContext)
        throws Exception
    {
        if (server != null)
        {
            // FIXME graceful Jetty shutdown
            // When shutdown request is accepted, empty response is sent back
            // instead of 200, because Jetty is not being shutdown gracefully.
            Thread.sleep(1000);
        }

        super.doStop(bundleContext);
    }
}
