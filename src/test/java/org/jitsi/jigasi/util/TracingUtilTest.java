/*
 * Copyright @ 2026 - present, 8x8 Inc
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
package org.jitsi.jigasi.util;

import static org.junit.jupiter.api.Assertions.*;

import io.opentelemetry.api.trace.*;
import io.opentelemetry.context.*;
import org.junit.jupiter.api.*;

/**
 * Tests parsing and formatting of W3C trace context header values.
 */
public class TracingUtilTest
{
    private static final String TRACE_ID
        = "4bf92f3577b34da6a3ce929d0e0e4736";

    private static final String SPAN_ID = "00f067aa0ba902b7";

    @Test
    public void testParseValidW3CHeader()
    {
        Context context = TracingUtil.remoteContextFromW3CHeader(
            "00-" + TRACE_ID + "-" + SPAN_ID + "-01");

        Span span = Span.fromContextOrNull(context);
        assertNotNull(span);

        SpanContext spanContext = span.getSpanContext();
        assertTrue(spanContext.isValid());
        assertTrue(spanContext.isRemote());
        assertEquals(TRACE_ID, spanContext.getTraceId());
        assertEquals(SPAN_ID, spanContext.getSpanId());
        assertTrue(spanContext.getTraceFlags().isSampled());
    }

    @Test
    public void testParseInvalidW3CHeader()
    {
        assertNull(Span.fromContextOrNull(
            TracingUtil.remoteContextFromW3CHeader(null)));
        assertNull(Span.fromContextOrNull(
            TracingUtil.remoteContextFromW3CHeader("")));
        assertNull(Span.fromContextOrNull(
            TracingUtil.remoteContextFromW3CHeader("garbage")));
        assertNull(Span.fromContextOrNull(
            TracingUtil.remoteContextFromW3CHeader("00-abc-def-01")));
        // all-zero ids are not valid per the W3C spec
        assertNull(Span.fromContextOrNull(
            TracingUtil.remoteContextFromW3CHeader(
                "00-00000000000000000000000000000000-0000000000000000-01")));
    }

    @Test
    public void testW3CHeaderRoundTrip()
    {
        String header = "00-" + TRACE_ID + "-" + SPAN_ID + "-01";

        Context context = TracingUtil.remoteContextFromW3CHeader(header);
        Span span = Span.fromContextOrNull(context);
        assertNotNull(span);

        assertEquals(header, TracingUtil.toW3CHeader(span.getSpanContext()));
    }
}
