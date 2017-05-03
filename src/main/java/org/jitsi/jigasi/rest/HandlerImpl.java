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

import java.io.*;
import java.util.concurrent.*;

import javax.servlet.*;
import javax.servlet.http.*;

import net.java.sip.communicator.util.*;

import org.eclipse.jetty.server.*;
import org.jitsi.jigasi.*;
import org.jitsi.rest.*;
import org.osgi.framework.*;

/**
 * Implements a Jetty <tt>Handler</tt> which is to provide the HTTP interface of
 * the JSON public API of <tt>Jigasi</tt>.
 * <p>
 * The REST API of Jigasi serves resources with
 * <tt>Content-Type: application/json</tt> under the base target
 * <tt>/about</tt>:
 * <table>
 *   <thead>
 *     <tr>
 *       <th>HTTP Method</th>
 *       <th>Resource</th>
 *       <th>Response</th>
 *     </tr>
 *   </thead>
 *   <tbody>
 *     <tr>
 *       <td>GET</td>
 *       <td>/about/health</td>
 *       <td>
 *         200 OK with a JSON array/list of JSON objects which represent
 *         the health of Jigasi and the registrationState of the sip provider.
 *         In case of error adds the registrationState and the possible error
 *         reason. For example:
 * <code>
 * [
 *   { &quot;registrationState&quot; : &quot;Unregistered&quot; },
 *   { &quot;reason&quot; : &quot;Some error reason.&quot; }
 * ]
 * </code>
 *       </td>
 *     </tr>
 *     <tr>
 *       <td>POST</td>
 *       <td>/about/stats</td>
 *       <td>
 *         <p>
 *         200 OK with a JSON object which represents the statistics of the
 *         currently served conferences. Total number of participants,
 *         conference distribution, number of threads.
 *         </p>
 *       </td>
 *     </tr>
 *     <tr>
 *       <td>POST</td>
 *       <td>/about/shutdown</td>
 *       <td>
 *         200 OK if shuting down through rest is enabled will put Jigasi in
 *         graceful shutdown and will wait for all conferences to end and will
 *         shutdown after that.
 *       </td>
 *     </tr>
 *   </tbody>
 * </table>
 * </p>
 *
 * @author Damian Minkov
 */
public class HandlerImpl
    extends AbstractJSONHandler
    implements SipGatewayListener
{
    /**
     * The HTTP resource which is used to trigger graceful shutdown.
     */
    private static final String SHUTDOWN_TARGET = "/about/shutdown";

    /**
     * The HTTP resource which lists the JSON representation of the
     * <tt>Statistics</tt>s of <tt>Jigasi</tt>.
     */
    private static final String STATISTICS_TARGET = "/about/stats";

    /**
     * Indicates if graceful shutdown mode is enabled. If not then
     * SC_SERVICE_UNAVAILABLE status will be returned for
     * {@link #SHUTDOWN_TARGET} requests.
     */
    private final boolean shutdownEnabled;

    /**
     * The sip gateway instance.
     */
    private SipGateway gateway;

    /**
     * Initializes a new {@code HandlerImpl} instance within a specific
     * {@code BundleContext}.
     *
     * @param bundleContext  the {@code BundleContext} within which the new
     *                       instance is to be initialized
     * @param enableShutdown {@code true} if graceful shutdown is to be
     *                       enabled; otherwise, {@code false}
     */
    protected HandlerImpl(BundleContext bundleContext, boolean enableShutdown)
    {
        super(bundleContext);

        shutdownEnabled = enableShutdown;

        this.gateway = ServiceUtils.getService(bundleContext, SipGateway.class);

        if (this.gateway != null)
        {
            this.gateway.addSipGatewayListener(this);
        }
        else
        {
            bundleContext.addServiceListener(new ServiceListener()
            {
                @Override
                public void serviceChanged(ServiceEvent serviceEvent)
                {
                    if (serviceEvent.getType() != ServiceEvent.REGISTERED)
                        return;

                    ServiceReference ref = serviceEvent.getServiceReference();
                    BundleContext bundleContext
                        = ref.getBundle().getBundleContext();

                    Object service = bundleContext.getService(ref);

                    if (!(service instanceof SipGateway))
                        return;

                    bundleContext.removeServiceListener(this);

                    SipGateway sipGw = (SipGateway) service;
                    HandlerImpl.this.gateway = sipGw;
                    sipGw.addSipGatewayListener(HandlerImpl.this);
                }
            });
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void doGetHealthJSON(
        Request baseRequest,
        HttpServletRequest request,
        HttpServletResponse response)
        throws IOException,
        ServletException
    {
        beginResponse(/* target */ null, baseRequest, request, response);

        if (this.gateway == null)
        {
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        }
        else
        {
            Health.getJSON(this.gateway, baseRequest, request, response);
        }

        endResponse(/* target */ null, baseRequest, request, response);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void handleJSON(
        String target,
        Request baseRequest,
        HttpServletRequest request,
        HttpServletResponse response)
        throws IOException,
        ServletException
    {
        super.handleJSON(target, baseRequest, request, response);

        if (baseRequest.isHandled())
            return; // The super implementation has handled the request.

        // FIXME In order to not invoke beginResponse() and endResponse() in
        // each and every one of the methods to which handleColibriJSON()
        // delegates/forwards, we will invoke them here. However, we do not
        // know whether handleColibriJSON() will actually handle the
        // request. As a workaround we will mark the response with a status
        // code that we know handleColibriJSON() does not utilize (at the
        // time of this writing) and we will later recognize whether
        // handleColibriJSON() has handled the request by checking whether
        // the response is still marked with the unused status code.
        int oldResponseStatus = response.getStatus();
        beginResponse(target, baseRequest, request, response);

        if (SHUTDOWN_TARGET.equals(target))
        {
            if (!shutdownEnabled)
            {
                response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                return;
            }

            if (POST_HTTP_METHOD.equals(request.getMethod()))
            {
                // Update graceful shutdown state
                doPostShutdownJSON(baseRequest, request, response);
            }
            else
            {
                response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            }
        }
        else if (STATISTICS_TARGET.equals(target))
        {
            if (GET_HTTP_METHOD.equals(request.getMethod()))
            {
                // Get the Statistics of Jigasi.
                doGetStatisticsJSON(baseRequest, request, response);
            }
            else
            {
                response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            }
        }

        int newResponseStatus = response.getStatus();

        if (newResponseStatus == HttpServletResponse.SC_NOT_IMPLEMENTED)
        {
            // Restore the status code which was in place before we replaced
            // it with our workaround.
            response.setStatus(oldResponseStatus);
        }
        else
        {
            // It looks like handleColibriJSON() indeed handled the request.
            endResponse(target, baseRequest, request, response);
        }
    }

    private void doPostShutdownJSON(Request baseRequest,
        HttpServletRequest request,
        HttpServletResponse response)
        throws IOException
    {
        if (this.gateway == null)
        {
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            return;
        }

        this.gateway.enableGracefulShutdownMode();
    }

    /**
     * Gets a JSON representation of the <tt>Statistics</tt> of (the
     * associated) <tt>Jigasi</tt>.
     *
     * @param baseRequest the original unwrapped {@link Request} object
     * @param request the request either as the {@code Request} object or a
     * wrapper of that request
     * @param response the response either as the {@code Response} object or a
     * wrapper of that response
     * @throws IOException
     * @throws ServletException
     */
    private void doGetStatisticsJSON(
        Request baseRequest,
        HttpServletRequest request,
        HttpServletResponse response)
        throws IOException,
        ServletException
    {
        if (this.gateway != null)
        {
            Statistics.getJSON(this.gateway, baseRequest, request, response);
        }
        else
        {
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        }
    }

    @Override
    public void onSessionAdded(GatewaySession session)
    {}

    /**
     * When a session ends we add all the cumulative statistics.
     *
     * @param session the session that was removed.
     */
    @Override
    public void onSessionRemoved(GatewaySession session)
    {
        Statistics.addTotalConferencesCount(1);
        Statistics.addTotalParticipantsCount(
            session.getParticipantsCount() - 1); // do not count focus
        Statistics.addCumulativeConferenceSeconds(
            TimeUnit.MILLISECONDS.toSeconds(
                System.currentTimeMillis()
                    - session.getCallContext().getTimestamp()));
    }
}
