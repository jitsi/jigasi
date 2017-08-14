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
 * A Gateway which creates a TranscriptionGatewaySession when it has an outgoing
 * call
 *
 * @author Nik Vaessen
 */
public class TranscriptionGateway
    extends AbstractGateway<TranscriptionGatewaySession>
{
    /**
     * Whether to publish final transcripts by locally saving them in json
     * format
     */
    private final static boolean SAVE_TRANSCRIPT_JSON = true;

    /**
     * Whether to publish final transcripts by locally saving them in txt format
     */
    private final static boolean SAVE_TRANSCRIPT_TXT = false;

    /**
     * Whether to send results in json to
     * {@link net.java.sip.communicator.service.protocol.ChatRoom} of muc
     */
    private final static boolean SEND_RESULT_JSON = false;

    /**
     * Whether to send results in txt to
     * {@link net.java.sip.communicator.service.protocol.ChatRoom} of muc
     */
    private final static boolean SEND_RESULT_TXT = true;

    /**
     * Class which manages the desired {@link TranscriptPublisher} and
     * {@link TranscriptionResultPublisher}
     */
    private TranscriptHandler handler = new TranscriptHandler();

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

        LocalJsonTranscriptHandler jsonHandler
            = new LocalJsonTranscriptHandler();
        LocalTxtTranscriptHandler txtHandler = new LocalTxtTranscriptHandler();

        if(SAVE_TRANSCRIPT_JSON)
        {
            handler.add((TranscriptPublisher) jsonHandler);
        }
        if(SAVE_TRANSCRIPT_TXT)
        {
            handler.add((TranscriptPublisher) txtHandler);
        }
        if(SEND_RESULT_JSON)
        {
            handler.add((TranscriptionResultPublisher) jsonHandler);
        }
        if(SEND_RESULT_TXT)
        {
            handler.add((TranscriptionResultPublisher) txtHandler);
        }
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
                new TranscriptionGatewaySession(
                    this,
                    ctx,
                    new GoogleCloudTranscriptionService(),
                    this.handler);
        outgoingSession.addListener(this);
        outgoingSession.createOutgoingCall();

        return outgoingSession;
    }
}
