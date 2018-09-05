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
package org.jitsi.jigasi;

import org.jitsi.jigasi.transcription.*;
import org.jitsi.jigasi.transcription.action.*;
import org.osgi.framework.*;

/**
 * A Gateway which creates a TranscriptionGatewaySession when it has an outgoing
 * call
 *
 * @author Nik Vaessen
 * @author Damian Minkov
 */
public class TranscriptionGateway
    extends AbstractGateway<TranscriptionGatewaySession>
{
    /**
     * Class which manages the desired {@link TranscriptPublisher} and
     * {@link TranscriptionResultPublisher}
     */
    private TranscriptHandler handler = new TranscriptHandler();

    /**
     * The actions service handler.
     */
    private ActionServicesHandler actionServicesHandler;

    /**
     * Create a new TranscriptionGateway, which manages
     * TranscriptionGatewaySessions in conferences, such that the audio
     * in those conferences can be transcribed
     *
     * @param context the context containing information about calls
     */
    public TranscriptionGateway(BundleContext context)
    {
        super(context);

        // init action handler
        actionServicesHandler = ActionServicesHandler.init(context);
    }

    @Override
    public void stop()
    {
        // stop action handler
        if (actionServicesHandler != null)
        {
            actionServicesHandler.stop();
            actionServicesHandler = null;
        }
    }

    @Override
    public TranscriptionGatewaySession createOutgoingCall(CallContext ctx)
    {
        TranscriptionGatewaySession outgoingSession =
                new TranscriptionGatewaySession(
                    this,
                    ctx,
                    new GoogleCloudTranscriptionService(),
                    this.handler);
        outgoingSession.addListener(this);
        outgoingSession.createOutgoingCall();

        return outgoingSession;
    }

    /**
     * Whether this gateway is ready to create sessions.
     * @return whether this gateway is ready to create sessions.
     */
    public boolean isReady()
    {
        return true;
    }
}
