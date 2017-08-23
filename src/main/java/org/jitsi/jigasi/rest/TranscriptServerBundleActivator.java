/*
 * Jigasi, the JItsi GAteway to SIP.
 *
 * Copyright @ 2017 Atlassian Pty Ltd
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
import org.eclipse.jetty.server.handler.*;
import org.jitsi.jigasi.*;
import org.jitsi.jigasi.transcription.*;
import org.jitsi.rest.*;
import org.osgi.framework.*;

/**
 * Activate a jetty instance which is able to serve
 * {@link org.jitsi.jigasi.transcription.Transcript} which are locally stored
 * with a {@link org.jitsi.jigasi.transcription.LocalTxtTranscriptHandler} or
 * {@link org.jitsi.jigasi.transcription.LocalJsonTranscriptHandler}
 *
 * @author Nik Vaessen
 */
public class TranscriptServerBundleActivator
    extends AbstractJettyBundleActivator
{
    /**
     * The prefix of the property names for the Jetty instance managed by
     * this {@link AbstractJettyBundleActivator}.
     */
    public static final String JETTY_PROPERTY_PREFIX
        = "org.jitsi.jigasi.transcription";

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
        return -1;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected int getDefaultTlsPort()
    {
        return -1;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Handler initializeHandlerList(BundleContext bundleContext,
                                            Server server)
        throws Exception
    {
        ResourceHandler fileHandler = new ResourceHandler();

        fileHandler.setDirectoriesListed(false);
        fileHandler.setResourceBase(
            AbstractTranscriptPublisher.getLogDirPath());

        return fileHandler;
    }
}
