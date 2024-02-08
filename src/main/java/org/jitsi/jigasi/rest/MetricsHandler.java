/*
 * Copyright @ 2024 - present, 8x8 Inc
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

import io.prometheus.client.exporter.common.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.eclipse.jetty.server.*;
import org.eclipse.jetty.server.handler.*;
import org.jitsi.jigasi.metrics.*;
import org.jitsi.jigasi.stats.*;

import java.io.*;

public class MetricsHandler extends AbstractHandler
{
    @Override
    public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException
    {
        if ("/metrics".equals(target))
        {
            String accept = request.getHeader("Accept");

            Statistics.updateMetrics();

            String responseBody;
            if (accept != null && accept.startsWith("application/openmetrics-text"))
            {
                responseBody = JigasiMetricsContainer.INSTANCE.getPrometheusMetrics(
                        TextFormat.CONTENT_TYPE_OPENMETRICS_100);
                response.setContentType(TextFormat.CONTENT_TYPE_OPENMETRICS_100);
            }
            else if (accept != null && accept.startsWith("text/plain"))
            {
                responseBody = JigasiMetricsContainer.INSTANCE.getPrometheusMetrics(TextFormat.CONTENT_TYPE_004);
                response.setContentType(TextFormat.CONTENT_TYPE_004);
            }
            else
            {
                responseBody = JigasiMetricsContainer.INSTANCE.getJsonString();
                response.setContentType(RESTUtil.JSON_CONTENT_TYPE_WITH_CHARSET);
            }

            Writer writer = response.getWriter();
            writer.write(responseBody);
            response.setStatus(200);
        }
    }
}
