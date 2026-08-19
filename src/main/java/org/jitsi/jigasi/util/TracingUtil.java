/*
 * Jigasi, the JItsi GAteway to SIP.
 *
 * Copyright @ 2026 - present 8x8, Inc.
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

import io.opentelemetry.api.trace.*;
import io.opentelemetry.context.*;
import org.jitsi.tracing.*;
import org.jitsi.xmpp.extensions.*;
import org.jivesoftware.smack.packet.*;

/**
 * Helpers for distributed tracing: extracting remote span contexts from
 * XMPP stanzas (the <tt>traceparent</tt> extension used by jicofo) and from
 * W3C trace context header values (as used over SIP/HTTP), and attaching
 * our own context to outgoing stanzas.
 *
 * The IQ-to-span-context logic is intentionally inlined here rather than
 * shared via jicoco-tracing, see https://github.com/jitsi/jicoco/pull/241.
 */
public final class TracingUtil
{
    /**
     * The name of the header (Rayo header on dial IQs, and SIP INVITE
     * header) carrying a W3C trace context <tt>traceparent</tt> value.
     * The <tt>X-</tt> prefix is used so intermediaries (e.g. VoxImplant)
     * pass the header through.
     */
    public static final String TRACEPARENT_HEADER = "X-Traceparent";

    /**
     * The tracer used for all jigasi spans. Backed by a no-op implementation
     * unless <tt>tracing.enabled</tt> is set.
     */
    private static final Tracer tracer
        = TracingGlobal.Companion.getSdk().getTracer("org.jitsi.jigasi");

    private TracingUtil()
    {}

    /**
     * Returns the tracer to use for jigasi spans.
     */
    public static Tracer getTracer()
    {
        return tracer;
    }

    /**
     * Extracts a remote span context from the <tt>traceparent</tt> extension
     * of an IQ, if present, or returns the root context otherwise.
     *
     * @param iq the IQ to extract the context from.
     * @return the remote context or {@link Context#root()}.
     */
    public static Context remoteContextFromIq(IQ iq)
    {
        TraceParent extension = iq.getExtension(TraceParent.class);
        if (extension == null)
        {
            return Context.root();
        }

        return remoteContext(
            extension.getTraceId(),
            extension.getParentId(),
            extension.getTraceFlags());
    }

    /**
     * Parses a W3C trace context <tt>traceparent</tt> value in the form
     * <tt>00-&lt;trace-id&gt;-&lt;parent-id&gt;-&lt;trace-flags&gt;</tt>
     * as received in a SIP or HTTP header.
     *
     * @param value the header value, may be null.
     * @return the remote context or {@link Context#root()} when the value is
     * missing or malformed.
     */
    public static Context remoteContextFromW3CHeader(String value)
    {
        if (value == null)
        {
            return Context.root();
        }

        String[] parts = value.trim().split("-");
        if (parts.length < 4)
        {
            return Context.root();
        }

        return remoteContext(parts[1], parts[2], parts[3]);
    }

    /**
     * Formats a span context as a W3C trace context <tt>traceparent</tt>
     * value, suitable for a SIP or HTTP header.
     *
     * @param spanContext the span context to format.
     * @return the header value.
     */
    public static String toW3CHeader(SpanContext spanContext)
    {
        return "00-" + spanContext.getTraceId()
            + "-" + spanContext.getSpanId()
            + "-" + spanContext.getTraceFlags().asHex();
    }

    /**
     * Attaches a <tt>traceparent</tt> extension carrying the span's context
     * to an outgoing stanza, so the receiving side can join the trace.
     * Does nothing when the span has no valid (e.g. no-op) context.
     *
     * @param stanza the stanza to add the extension to.
     * @param span the span whose context to propagate.
     */
    public static void attachTraceParent(Stanza stanza, Span span)
    {
        if (span == null)
        {
            return;
        }

        SpanContext spanContext = span.getSpanContext();
        if (!spanContext.isValid())
        {
            return;
        }

        stanza.addExtension(new TraceParent(
            spanContext.getTraceId(),
            spanContext.getSpanId(),
            spanContext.getTraceFlags().asHex()));
    }

    /**
     * Builds a remote parent context from raw trace id/span id/flags values.
     * Returns the root context when the ids are not valid.
     */
    private static Context remoteContext(
        String traceId, String spanId, String flagsHex)
    {
        if (!TraceId.isValid(traceId) || !SpanId.isValid(spanId))
        {
            return Context.root();
        }

        TraceFlags flags;
        try
        {
            flags = TraceFlags.fromHex(flagsHex, 0);
        }
        catch (Exception e)
        {
            flags = TraceFlags.getDefault();
        }

        Span span = Span.wrap(
            SpanContext.createFromRemoteParent(
                traceId, spanId, flags, TraceState.getDefault()));

        return Context.root().with(span);
    }
}
