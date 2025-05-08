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

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import jakarta.ws.rs.core.*;
import kotlin.*;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.*;
import org.jitsi.jigasi.metrics.*;
import org.jitsi.jigasi.stats.*;

import java.io.*;
import java.util.*;
import java.util.stream.*;

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

            Pair<String, String> metricsAndContentType
                    = JigasiMetricsContainer.INSTANCE.getMetrics(getMediaTypes(accept));
            response.setContentType(metricsAndContentType.getSecond());

            Writer writer = response.getWriter();
            writer.write(metricsAndContentType.getFirst());
            response.setStatus(200);
        }
    }

    private List<String> getMediaTypes(String accept)
    {
        if (accept == null)
        {
            return Collections.emptyList();
        }

        if (accept.startsWith("Accept: "))
        {
            accept = accept.substring("Accept: ".length());
        }

        return Arrays.stream(accept.split(","))
            .map(String::trim)
            .map(MediaType::valueOf)
            .sorted(new MediaTypeComparator())
            .map(m -> m.getType() + "/" + m.getSubtype())
            .collect(Collectors.toList());
    }

    private static class MediaTypeComparator implements Comparator<MediaType>
    {
        @Override
        public int compare(MediaType o1, MediaType o2)
        {
            double q1 = Double.parseDouble(o1.getParameters().getOrDefault("q", "1.0"));
            double q2 = Double.parseDouble(o2.getParameters().getOrDefault("q", "1.0"));
            return Double.compare(q1, q2);
        }
    }
}
