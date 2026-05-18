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
import org.jitsi.utils.version.*;
import org.jivesoftware.smack.packet.*;
import org.json.simple.*;
import org.osgi.framework.*;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;

/**
 * Implements an abstract Jetty servlet which provides content in JSON format.
 *
 * @author Lyubomir Marinov
 * @author Pawel Domas
 */
public abstract class AbstractJSONHandler
    extends HttpServlet
{
    /**
     * The HTTP GET method.
     */
    protected static final String GET_HTTP_METHOD = "GET";

    /**
     * The HTTP resource which checks the health of the server/service exposed
     * through {@code AbstractJSONHandler}. Explicitly defined as an attempt to
     * encourage consistency among the extenders.
     */
    private static final String HEALTH_TARGET = "/about/health";

    /**
     * The HTTP PATCH method.
     */
    protected static final String PATCH_HTTP_METHOD = "PATCH";

    /**
     * The HTTP POST method.
     */
    protected static final String POST_HTTP_METHOD = "POST";

    /**
     * The HTTP resource which returns the JSON representation of the
     * {@code Version} of the server/service exposed through
     * {@code AbstractJSONHandler}. Explicitly defined as an attempt to
     * encourage consistency among the extenders.
     */
    private static final String VERSION_TARGET = "/about/version";

    /**
     * Analyzes response IQ returned by {@code AbstractJSONHandler#handle()}
     * method(s) and translates XMPP error into HTTP status code.
     *
     * @param responseIQ the IQ that is not ColibriConferenceIQ from
     *        which XMPP error will be extracted.
     * @return HTTP status code
     */
    protected static int getHttpStatusCodeForResultIq(IQ responseIQ)
    {
        StanzaError.Condition condition = responseIQ.getError().getCondition();

        if (StanzaError.Condition.not_authorized.equals(condition))
        {
            return HttpServletResponse.SC_UNAUTHORIZED;
        }
        else if (StanzaError.Condition.service_unavailable.equals(
            condition))
        {
            return HttpServletResponse.SC_SERVICE_UNAVAILABLE;
        }
        else
        {
            return HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
        }
    }

    /**
     * The {@code BundleContext} within which this instance is initialized.
     */
    protected final BundleContext bundleContext;

    /**
     * Initializes a new {@code AbstractJSONHandler} instance within a specific
     * {@code BundleContext}.
     *
     * @param bundleContext the {@code BundleContext} within which the new
     * instance is to be initialized
     */
    protected AbstractJSONHandler(BundleContext bundleContext)
    {
        this.bundleContext = bundleContext;
    }

    /**
     * Begins an {@link HttpServletResponse} the handling of which appears to
     * have chances of success.
     */
    protected void beginResponse(
        String target,
        HttpServletRequest request,
        HttpServletResponse response)
    {
        beginResponse(
            target,
            request,
            response,
            RESTUtil.JSON_CONTENT_TYPE_WITH_CHARSET);
    }

    /**
     * Begins an {@link HttpServletResponse} the handling of which appears to
     * have chances of success.
     */
    protected void beginResponse(
        String target,
        HttpServletRequest request,
        HttpServletResponse response,
        String contentType)
    {
        response.setContentType(contentType);
        // Cross-origin resource sharing (CORS)
        response.setHeader("Access-Control-Allow-Origin", "*");
    }

    /**
     * Gets a JSON representation of the health (status) of the associated
     * server/service. The default implementation does nothing because it serves
     * as a placeholder for extenders.
     */
    protected void doGetHealthJSON(
        HttpServletRequest request,
        HttpServletResponse response)
        throws IOException,
        ServletException
    {
    }

    /**
     * Gets a JSON representation of the {@code Version} of the associated
     * server/service.
     */
    protected void doGetVersionJSON(
        HttpServletRequest request,
        HttpServletResponse response)
        throws IOException,
        ServletException
    {
        beginResponse(/*target*/ null, request, response);

        BundleContext bundleContext = getBundleContext();
        int status = HttpServletResponse.SC_SERVICE_UNAVAILABLE;

        if (bundleContext != null)
        {
            VersionService versionService
                = ServiceUtils.getService(bundleContext, VersionService.class);

            if (versionService != null)
            {
                org.jitsi.utils.version.Version version
                    = versionService.getCurrentVersion();
                JSONObject versionJSONObject = new JSONObject();

                versionJSONObject.put(
                    "name",
                    version.getApplicationName());
                versionJSONObject.put("version", version.toString());
                versionJSONObject.put("os", System.getProperty("os.name"));

                Writer writer = response.getWriter();

                response.setStatus(status = HttpServletResponse.SC_OK);
                versionJSONObject.writeJSONString(writer);
            }
        }

        if (response.getStatus() != status)
            response.setStatus(status);
    }

    /**
     * Gets the {@code BundleContext} in which this Jetty {@code Handler} has
     * been started.
     *
     * @return the {@code BundleContext} in which this Jetty {@code Handler}
     * has been started or {@code null} if this Jetty {@code Handler} has not
     * been started in a {@code BundleContext}
     */
    public BundleContext getBundleContext()
    {
        return bundleContext;
    }

    /**
     * Gets the OSGi service instance of a specific {@code Class} available to
     * this Jetty {@code Handler}.
     *
     * @param <T> the type of the OSGi service to retrieve
     * @param serviceClass the {@code Class} of the OSGi service to retrieve
     * @return the OSGi service instance of the specified {@code serviceClass}
     * available to this Jetty {@code Handler} or {@code null} if no such
     * service instance is available
     */
    public <T> T getService(Class<T> serviceClass)
    {
        BundleContext bundleContext = getBundleContext();
        T service = ServiceUtils.getService(bundleContext, serviceClass);

        return service;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void service(
        HttpServletRequest request,
        HttpServletResponse response)
        throws IOException, ServletException
    {
        String target = request.getRequestURI();

        if (!handleJSON(target, request, response))
        {
            if (response.getStatus() == 0
                || response.getStatus() == HttpServletResponse.SC_OK)
            {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            }
        }
    }

    /**
     * Handles an HTTP request for a {@link #HEALTH_TARGET}-related resource.
     */
    protected void handleHealthJSON(
        String target,
        HttpServletRequest request,
        HttpServletResponse response)
        throws IOException,
        ServletException
    {
        if (GET_HTTP_METHOD.equals(request.getMethod()))
        {
            // Check/get the health (status) of the associated server/service.
            doGetHealthJSON(request, response);
        }
        else
        {
            response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        }
    }

    /**
     * Handles a specific HTTP request for JSON content. Returns {@code true}
     * if the request was handled.
     */
    protected boolean handleJSON(
        String target,
        HttpServletRequest request,
        HttpServletResponse response)
        throws IOException,
        ServletException
    {
        if (HEALTH_TARGET.equals(target))
        {
            handleHealthJSON(target, request, response);
            return true;
        }
        else if (VERSION_TARGET.equals(target))
        {
            handleVersionJSON(target, request, response);
            return true;
        }
        return false;
    }

    /**
     * Handles an HTTP request for a {@link #VERSION_TARGET}-related resource.
     */
    protected void handleVersionJSON(
        String target,
        HttpServletRequest request,
        HttpServletResponse response)
        throws IOException,
        ServletException
    {
        if (GET_HTTP_METHOD.equals(request.getMethod()))
        {
            // Get the Version of the associated server/service.
            doGetVersionJSON(request, response);
        }
        else
        {
            response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        }
    }
}
