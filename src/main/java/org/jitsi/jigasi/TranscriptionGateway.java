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
import org.jitsi.utils.logging.Logger;
import org.json.*;
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
     * The logger
     */
    private final static Logger logger
        = Logger.getLogger(TranscriptionGateway.class);

    /**
     * Property for the class name of a custom transcription service.
     */
    private static final String CUSTOM_TRANSCRIPTION_SERVICE_PROP
        = "org.jitsi.jigasi.transcription.customService";

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

    /**
     * Tries to retrieve a transcriber assigned to a tenant
     * if the property value is a json. Returns the value as
     * is if no JSON is found.
     *
     * @param tenant the tenant which is retrieved from the context
     */
    private String getCustomTranscriptionServiceClass(String tenant)
    {
        String customTranscriptionServiceProp
                = JigasiBundleActivator.getConfigurationService()
                .getString(
                        CUSTOM_TRANSCRIPTION_SERVICE_PROP,
                        null);
        if (customTranscriptionServiceProp.strip().startsWith("{"))
        {
            if (tenant == null)
            {
                return null;
            }
            try
            {
                JSONObject obj = new JSONObject(customTranscriptionServiceProp);
                return obj.getString(tenant);
            }
            catch (JSONException ex)
            {
                logger.warn("Could not find '" + tenant + "' tenant in custom transcription service JSON property.");
                return null;
            }
        }
        return customTranscriptionServiceProp;
    }

    @Override
    public TranscriptionGatewaySession createOutgoingCall(CallContext ctx)
    {
        String customTranscriptionServiceClass
            = getCustomTranscriptionServiceClass(ctx.getTenant());
        AbstractTranscriptionService service = null;
        if (customTranscriptionServiceClass != null)
        {
            try
            {
                service = (AbstractTranscriptionService)Class.forName(
                        customTranscriptionServiceClass).getDeclaredConstructor().newInstance();
            }
            catch(Exception e)
            {
                logger.error("Cannot instantiate custom transcription service", e);
            }
        }

        if (service == null)
        {
            service = new GoogleCloudTranscriptionService();
        }

        TranscriptionGatewaySession outgoingSession =
                new TranscriptionGatewaySession(
                    this,
                    ctx,
                    service,
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
