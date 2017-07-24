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
import org.osgi.framework.*;

/**
 * A Gateway which creates a TranscriptionGatewaySession when it has an outgoing call
 *
 * @author Nik Vaessen
 */
public class TranscriptionGateway
    extends AbstractGateway<TranscriptionGatewaySession>
{

    public TranscriptionGateway(BundleContext context)
    {
        super(context);
    }

    @Override
    public void stop()
    {
        // nothing to do
    }

    @Override
    public TranscriptionGatewaySession createOutgoingCall(CallContext ctx)
    {
        TranscriptionGatewaySession outgoingSession =
                new TranscriptionGatewaySession(this, ctx,
                        new GoogleCloudTranscriptionService());
        outgoingSession.addListener(this);
        outgoingSession.createOutgoingCall();

        return outgoingSession;
    }
}
