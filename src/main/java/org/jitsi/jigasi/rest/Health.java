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
import java.util.*;

import javax.servlet.*;
import javax.servlet.http.*;

import net.java.sip.communicator.service.protocol.*;

import org.eclipse.jetty.server.*;
import org.jitsi.jigasi.*;
import org.json.simple.*;

/**
 * Checks the health of Jigasi.
 *
 * @author Damian Minkov
 */
public class Health
{
    /**
     * Gets a JSON representation of the health (status) of a specific
     * {@link SipGateway}. The method is synchronized so anything other than
     * the health check itself (which is cached) needs to return very quickly.
     *
     * @param gateway the {@code SipGateway} to get the health (status)
     * of in the form of a JSON representation
     * @param baseRequest the original unwrapped {@link Request} object
     * @param request the request either as the {@code Request} object or a
     * wrapper of that request
     * @param response the response either as the {@code Response} object or a
     * wrapper of that response
     * @throws IOException
     * @throws ServletException
     */
    static synchronized void getJSON(
        SipGateway gateway,
        Request baseRequest,
        HttpServletRequest request,
        HttpServletResponse response)
        throws IOException,
        ServletException
    {
        int status;
        String reason = null;
        Map<String,Object> responseMap = new HashMap<>();

        try
        {
            RegistrationState registrationState =
                gateway.getSipProvider().getRegistrationState();
            responseMap.put(
                "registrationState", registrationState.getStateName());
            if (registrationState == RegistrationState.REGISTERED)
            {
                status = HttpServletResponse.SC_OK;
            }
            else
            {
                status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
                reason = "SIP provider not registered.";
            }
        }
        catch (Exception ex)
        {
            status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
            reason = ex.getMessage();
        }

        if (reason != null)
        {
            responseMap.put("reason", reason);
        }
        response.setStatus(status);
        new JSONObject(responseMap).writeJSONString(response.getWriter());
    }
}
