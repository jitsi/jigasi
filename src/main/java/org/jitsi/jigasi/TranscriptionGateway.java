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
import org.jitsi.utils.logging.*;
import org.json.*;
import org.osgi.framework.*;

import java.io.*;
import java.net.*;

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
     * Property for the class name of a custom transcription service.
     */
    private static final String REMOTE_TRANSCRIPTION_CONFIG_URL
            = "org.jitsi.jigasi.transcription.remoteTranscriptionConfigUrl";

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
        String transcriberClass = null;
        String remoteTranscriptionConfigUrl
                = JigasiBundleActivator.getConfigurationService()
                .getString(
                        REMOTE_TRANSCRIPTION_CONFIG_URL,
                        null);

        if (remoteTranscriptionConfigUrl != null && tenant != null)
        {
            String tsConfigUrl = remoteTranscriptionConfigUrl + "/" + tenant;
            transcriberClass = getTranscriberFromRemote(tsConfigUrl);
        }

        if (transcriberClass == null)
        {
            transcriberClass
                    = JigasiBundleActivator.getConfigurationService()
                    .getString(
                            CUSTOM_TRANSCRIPTION_SERVICE_PROP,
                            null);
        }
        return transcriberClass;
    }

    private String getTranscriberFromRemote(String remoteTsConfigUrl)
    {
        String transcriberClass = null;
        if (logger.isDebugEnabled())
        {
            logger.debug("Calling  " + remoteTsConfigUrl + " to retrieve transcriber.");
        }
        try
        {
            URL url = new URL(remoteTsConfigUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setConnectTimeout(3000);
            int responseCode = conn.getResponseCode();
            if (responseCode == 200)
            {
                BufferedReader inputStream = new BufferedReader(
                        new InputStreamReader(conn.getInputStream()));
                String inputLine;
                StringBuilder responseBody = new StringBuilder();
                while ((inputLine = inputStream.readLine()) != null)
                {
                    responseBody.append(inputLine);
                }
                inputStream.close();
                JSONObject obj = new JSONObject(responseBody.toString());
                transcriberClass = obj.getString("transcriber");
            }
            conn.disconnect();
        }
        catch (Exception ex)
        {
            logger.error("Could not retrieve transcriber from remote URL." + ex);
        }
        return transcriberClass;
    }

    @Override
    public TranscriptionGatewaySession createOutgoingCall(CallContext ctx)
    {
        String customTranscriptionServiceClass = getCustomTranscriptionServiceClass(ctx.getTenant());
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
