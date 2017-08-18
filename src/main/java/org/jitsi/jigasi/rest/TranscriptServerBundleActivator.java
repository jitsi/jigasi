package org.jitsi.jigasi.rest;

import org.eclipse.jetty.server.*;
import org.jitsi.rest.*;
import org.osgi.framework.*;

/**
 *
 */
public class TranscriptServerBundleActivator
    extends AbstractJettyBundleActivator
{
    /**
     * The prefix of the property names for the Jetty instance managed by
     * this {@link AbstractJettyBundleActivator}.
     */
    public static final String JETTY_PROPERTY_PREFIX
        = "org.jitsi.jigasi.rest.transcript";

    /**
     * Create a new Bundle which serves locally stored
     * {@link org.jitsi.jigasi.transcription.Transcript}s formatted by a
     * {@link org.jitsi.jigasi.transcription.TranscriptPublisher}
     */
    public TranscriptServerBundleActivator()
    {
        super(JETTY_PROPERTY_PREFIX);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected int getDefaultPort()
    {
        // a default port that is not clashing other components ports
        return 8789;
    }

    @Override
    protected Handler initializeHandlerList(BundleContext bundleContext,
                                            Server server)
        throws Exception
    {
        return new TranscriptHandler();
    }
}
