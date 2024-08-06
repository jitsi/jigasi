/*
 * Jigasi, the JItsi GAteway to SIP.
 *
 * Copyright @ 2018 - present 8x8, Inc.
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
package org.jitsi.jigasi.transcription;

import com.oracle.bmc.ConfigFileReader;
import com.oracle.bmc.aispeech.model.*;
import com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider;
import org.jitsi.impl.neomedia.device.*;
import org.jitsi.jigasi.JigasiBundleActivator;
import org.jitsi.jigasi.transcription.oracle.*;
import org.jitsi.utils.logging.Logger;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;


/**
 * Implements a TranscriptionService which uses the Oracle Cloud Infrastructure
 * to perform live transcriptions.
 *
 * @author Purdel Razvan
 */

public class OracleTranscriptionService
        extends AbstractTranscriptionService
{

    /**
     * Uses the PCMAudioSilenceMediaDevice which performs a transcoding to 16khz PCM audio
     *
     * @param listener
     * @return AudioMixerMediaDevice
     */


    @Override
    public AudioMixerMediaDevice getMediaDevice(ReceiveStreamBufferListener listener)
    {
        if (this.mediaDevice == null)
        {
            this.mediaDevice = new TranscribingAudioMixerMediaDevice(new PCMAudioSilenceMediaDevice(), listener);
        }

        return this.mediaDevice;
    }

    /**
     * The logger for this class
     */
    private final static Logger logger
            = Logger.getLogger(OracleTranscriptionService.class);

    /**
     * The config key of the websocket to the speech-to-text service.
     */
    public final static String WEBSOCKET_URL
            = "org.jitsi.jigasi.transcription.oci.websocketUrl";

    public final static String COMPARTMENT_ID
            = "org.jitsi.jigasi.transcription.oci.compartmentId";

    private final String compartmentId;

    public final static String DEFAULT_WEBSOCKET_URL = "ws://localhost:8000/ws";

    private final static ConfigFileReader.ConfigFile configFile;

    static
    {
        try
        {
//            TODO: work on the authentication
            configFile = ConfigFileReader.parse("~/.oci/config");
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }


    /**
     * The config value of the websocket to the speech-to-text service.
     */
    private final String websocketUrlConfig;


    /**
     * Create a TranscriptionService which will send audio to the OCI service
     * platform to get a transcription
     */
    public OracleTranscriptionService()
    {
        websocketUrlConfig = JigasiBundleActivator.getConfigurationService()
                .getString(WEBSOCKET_URL, DEFAULT_WEBSOCKET_URL);
        compartmentId = JigasiBundleActivator.getConfigurationService()
                .getString(COMPARTMENT_ID, "");
    }

    public boolean supportsLanguageRouting()
    {
        return false;
    }


    @Override
    public boolean supportsStreamRecognition()
    {
        return true;
    }

    @Override
    public boolean supportsFragmentTranscription()
    {
        return false;
    }

    @Override
    public void sendSingleRequest(final TranscriptionRequest request,
                                  final Consumer<TranscriptionResult> resultConsumer)
    {
        logger.warn("The OCI transcription service does not support single requests.");
    }

    @Override
    public StreamingRecognitionSession initStreamingSession(
            Participant participant)
    {
        return new OracleStreamingSession(websocketUrlConfig);
    }

    @Override
    public boolean isConfiguredProperly()
    {
        return true;
    }

    public class OracleStreamingSession implements StreamingRecognitionSession, OracleRealtimeClientListener
    {
        private OracleRealtimeClient client;

        private UUID uuid = UUID.randomUUID();

        private boolean sessionEnding = false;

        private final String websocketUrl;


        /**
         * List of TranscriptionListeners which will be notified when a
         * result comes in
         */
        private final List<TranscriptionListener> listeners = new ArrayList<>();

        private Instant sessionStart;


        public OracleStreamingSession(String websocketUrl)
        {
            this.websocketUrl = websocketUrl;
            try
            {
                client = new OracleRealtimeClient(
                        this,
                        new ConfigFileAuthenticationDetailsProvider(configFile),
                        compartmentId);
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }

        private void connect()
        {
            final RealtimeParameters realtimeClientParameters = RealtimeParameters.builder()
                    .isAckEnabled(false)
                    .finalSilenceThresholdInMs(1000)
                    .build();
            if (client.isConnected())
            {
                return;
            }
            try
            {
                client.open(websocketUrl, 443, realtimeClientParameters);
                sessionStart = Instant.now();
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }

        @Override
        public void sendRequest(TranscriptionRequest request)
        {
            connect();
            try
            {
                client.sendAudioData(request.getAudio());
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }

        @Override
        public void end()
        {
            logger.info("+++++Ending OCI session and waiting for the last results");
            try
            {
                // Wait for 5 seconds for the last results to come in
                Thread.sleep(5000);
            }
            catch (InterruptedException e)
            {
                if (logger.isDebugEnabled())
                {
                    logger.debug("Interrupted while waiting for the last results");
                }
            }

            try
            {
                client.close();
            }
            catch (Exception e)
            {
                if (logger.isDebugEnabled())
                {
                    logger.debug("Error while closing the OCI connection", e);
                }
            }
            sessionEnding = true;
        }

        @Override
        public boolean ended()
        {
            return sessionEnding;
        }

        @Override
        public void addTranscriptionListener(TranscriptionListener listener)
        {
            listeners.add(listener);
        }

        @Override
        public void onClose(int statusCode, String statusMessage)
        {
            if (logger.isDebugEnabled())
            {
                logger.debug("Connection closed: " + statusCode + " " + statusMessage);
            }
        }

        @Override
        public void onAckMessage(RealtimeMessageAckAudio ackMessage)
        {
            if (logger.isDebugEnabled())
            {
                logger.debug("Received ack message: " + ackMessage);
            }
        }

        @Override
        public void onResult(RealtimeMessageResult result)
        {
            if (!result.getTranscriptions().isEmpty())
            {
                String tsResult = result.getTranscriptions().get(0).getTranscription().trim();
                logger.info("++++++++++ Received result: " + tsResult);
                Boolean isFinal = result.getTranscriptions().get(0).getIsFinal();

                for (TranscriptionListener l : listeners)
                {
                    l.notify(new TranscriptionResult(
                            null,
                            uuid,
                            sessionStart.plusMillis(result.getTranscriptions().get(0).getStartTimeInMs()),
                            !isFinal,
                            "en-US",
                            1.0,
                            new TranscriptionAlternative(tsResult)));
                }

                if (isFinal)
                {
                    uuid = UUID.randomUUID();
                }
            }

        }

        @Override
        public void onError(Throwable error)
        {
            logger.error("Error" + error.toString());
            error.printStackTrace();
        }

        @Override
        public void onConnect()
        {
            logger.info("Connected to OCI service");
        }

        @Override
        public void onConnectMessage(RealtimeMessageConnect connectMessage)
        {
            logger.info("Received connect message: " + connectMessage);
        }
    }
}
