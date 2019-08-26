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
package org.jitsi.jigasi.rest;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;

import javax.servlet.*;
import javax.servlet.http.*;

import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.util.*;
import org.jitsi.jigasi.*;
import org.jitsi.jigasi.health.*;
import org.jitsi.jigasi.stats.*;
import org.jitsi.jigasi.xmpp.*;
import org.jitsi.rest.*;

import org.eclipse.jetty.server.*;
import org.json.simple.*;
import org.json.simple.parser.*;
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
 *         200 OK if shutting down through rest is enabled will put Jigasi in
 *         graceful shutdown and will wait for all conferences to end and will
 *         shutdown after that.
 *       </td>
 *     </tr>
 *     <tr>
 *       <td>POST</td>
 *       <td>/configure/call-control-muc/add</td>
 *       <td>
 *         200 OK if adding an XMPP call control MUC was successful.
 *       </td>
 *     </tr>
 *     </tr>
 *     <tr>
 *       <td>POST</td>
 *       <td>/configure/call-control-muc/remove</td>
 *       <td>
 *         200 OK if removing an XMPP call control MUC was successful.
 *       </td>
 *     </tr>
 *     </tr>
 *   </tbody>
 * </table>
 * </p>
 *
 * @author Damian Minkov
 * @author Nik Vaessen
 */
public class HandlerImpl
    extends AbstractJSONHandler
    implements GatewayListener
{
    /**
     * The logger
     */
    private final static Logger logger = Logger.getLogger(HandlerImpl.class);

    /**
     * The HTTP resource which is used to add/remove new XMPP control MUC.
     */
    private static final String CONFIGURE_MUC_TARGET
        = "/configure/call-control-muc";

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

        List<AbstractGateway> gatewayList
            = JigasiBundleActivator.getAvailableGateways();
        gatewayList.forEach(gw -> gw.addGatewayListener(this));

        if (gatewayList.isEmpty())
        {
            // in case somebody moves the osgi activators order
            // and we no longer get the gateways
            logger.error("No gateways found. "
                + "Total statistics count will be missing!");
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
        throws IOException
    {
        beginResponse(/* target */ null, baseRequest, request, response);

        // if there is a gateway that is not ready, that means unhealthy
        if (JigasiBundleActivator.getAvailableGateways()
            .stream().anyMatch(g -> !g.isReady()))
        {
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        }
        else
        {
            sendJSON(response);
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
        throws IOException, ServletException
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
                JigasiBundleActivator.enableGracefulShutdownMode();
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
        else if (target.startsWith(CONFIGURE_MUC_TARGET + "/"))
        {
            doHandleConfigureMucRequest(
                target.substring((CONFIGURE_MUC_TARGET + "/").length()),
                request,
                response);
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
     */
    private void doGetStatisticsJSON(
            Request baseRequest,
            HttpServletRequest request,
            HttpServletResponse response)
        throws IOException
    {
        if (JigasiBundleActivator.getAvailableGateways().isEmpty())
        {
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        }
        else
        {
            Statistics.sendJSON(baseRequest, request, response);
        }
    }

    /**
     * When a session ends we add all the cumulative statistics.
     *
     * @param session the session that was removed.
     */
    @Override
    public void onSessionRemoved(AbstractGatewaySession session)
    {
        Statistics.addTotalConferencesCount(1);
        Statistics.addTotalParticipantsCount(
            session.getParticipantsCount() - 1); // do not count focus
        Statistics.addCumulativeConferenceSeconds(
            TimeUnit.MILLISECONDS.toSeconds(
                System.currentTimeMillis()
                    - session.getCallContext().getTimestamp()));
    }

    /**
     * Gets a JSON representation of the health (status) of a specific
     * {@link SipGateway}. The method is synchronized so anything other than
     * the health check itself (which is cached) needs to return very quickly.
     *
     * @param response the response either as the {@code Response} object or a
     * wrapper of that response
     * @throws IOException
     */
    static synchronized void sendJSON(
        HttpServletResponse response)
        throws IOException
    {
        int status;
        String reason = null;
        Map<String,Object> responseMap = new HashMap<>();
        try
        {
            Health.check();
            status = HttpServletResponse.SC_OK;
        }
        catch (Exception e)
        {
            status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
            reason = e.getMessage();
        }

        if (reason != null)
        {
            responseMap.put("reason", reason);
        }
        response.setStatus(status);
        new JSONObject(responseMap).writeJSONString(response.getWriter());
    }

    /**
     * Configures new MUC control room or removes it. Handles requests:
     * to /configure/call-control-muc.
     *
     * @param target the target URL with the part after
     * {@code CONFIGURE_MUC_TARGET}.
     * @param request the request either as the {@code Request} object or a
     * wrapper of that request
     * @param response the response either as the {@code Response} object or a
     * wrapper of that response
     * @throws IOException
     */
    private void doHandleConfigureMucRequest(
        String target,
        HttpServletRequest request,
        HttpServletResponse response)
        throws IOException
    {
        if (!POST_HTTP_METHOD.equals(request.getMethod()))
        {
            response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return;
        }

        if (!RESTUtil.isJSONContentType(request.getContentType()))
        {
            response.setStatus(HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE);
            return;
        }

        JSONObject requestJSONObject;
        try
        {
            Object o = new JSONParser().parse(request.getReader());
            if (o instanceof JSONObject)
            {
                requestJSONObject = (JSONObject) o;
            }
            else
            {
                requestJSONObject = null;
            }
        }
        catch (Exception e)
        {
            requestJSONObject = null;
        }

        if (requestJSONObject == null)
        {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        if (!JigasiBundleActivator.getConfigurationService()
                .getBoolean(
                    CallControlMucActivator.BREWERY_ENABLED_PROP, false))
        {
            //MUC call control disabled.
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            return;
        }

        if ("add".equals(target))
        {
            if (requestJSONObject == null
                || !(requestJSONObject.get("id") instanceof String))
            {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                return;
            }
            String id = (String) requestJSONObject.get("id");

            try
            {
                CallControlMucActivator.addCallControlMucAccount(
                    id, requestJSONObject.entrySet());
            }
            catch(OperationFailedException e)
            {
                logger.error("Failed to add account:" + id, e);
                response.setStatus(
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                return;
            }

            response.setStatus(HttpServletResponse.SC_OK);
        }
        else if ("remove".equals(target))
        {
            if (requestJSONObject == null
                || !(requestJSONObject.get("id") instanceof String))
            {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                return;
            }

            CallControlMucActivator.removeCallControlMucAccount(
                (String) requestJSONObject.get("id"));

            response.setStatus(HttpServletResponse.SC_OK);
        }
        else
        {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
    }
}
