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
package org.jitsi.jigasi.visitor;

import org.apache.commons.io.*;
import org.bouncycastle.util.io.pem.*;
import org.jitsi.jigasi.*;
import org.jitsi.jigasi.util.*;
import org.jitsi.utils.logging.*;
import org.json.simple.*;
import org.json.simple.parser.*;
import org.springframework.http.*;
import org.springframework.lang.*;
import org.springframework.messaging.converter.*;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.scheduling.concurrent.*;
import org.springframework.web.socket.*;
import org.springframework.web.socket.client.*;
import org.springframework.web.socket.client.standard.*;
import org.springframework.web.socket.messaging.*;

import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.*;
import java.util.*;

/**
 * The websocket client to connect to visitors queue.
 */
public class WebsocketClient
{
    /**
     * The logger.
     */
    private final static Logger logger = Logger.getLogger(WebsocketClient.class);

    /**
     * The name of the property of the private key we use for jwt token to connect to visitors queue service.
     */
    public static final String P_NAME_VISITORS_QUEUE_SERVICE_PRIVATE_KEY_PATH
            = "org.jitsi.jigasi.VISITOR_QUEUE_SERVICE_PRIVATE_KEY_PATH";

    /**
     * The name of the property which holds the key id (kid) we use for jwt to connect to visitors queue service.
     */
    public static final String P_NAME_VISITORS_QUEUE_SERVICE_PRIVATE_KEY_ID
            = "org.jitsi.jigasi.VISITOR_QUEUE_SERVICE_PRIVATE_KEY_ID";

    /**
     * The private key to use for generating jwt token to access service.
     */
    private static final String privateKeyFilePath;
    private static final String privateKeyId;
    static
    {
        privateKeyFilePath
            = JigasiBundleActivator.getConfigurationService().getString(P_NAME_VISITORS_QUEUE_SERVICE_PRIVATE_KEY_PATH);
        privateKeyId
            = JigasiBundleActivator.getConfigurationService().getString(P_NAME_VISITORS_QUEUE_SERVICE_PRIVATE_KEY_ID);
    }

    /**
     * The thread pool for heartbeat of the sockets.
     */
    private static final ThreadPoolTaskScheduler taskScheduler = new ThreadPoolTaskScheduler();
    static
    {
        taskScheduler.setPoolSize(1);
        taskScheduler.setThreadNamePrefix("stomp-heartbeat-client-thread-");
        taskScheduler.initialize();
    }

    /**
     * The call context used to for the current conference.
     */
    private final CallContext callContext;

    /**
     * The service URL to use to connect to for visitor queue.
     */
    private final String serviceUrl;

    /**
     * The parent conference.
     */
    private final JvbConference conference;

    /**
     * The stomp client that is active when connected.
     */
    private WebSocketStompClient stompClient;

    /**
     * A timer which will be used to schedule connection to conference after going live.
     */
    private static final Timer connectTimer = new Timer();

    public WebsocketClient(JvbConference conference, String serviceUrl, CallContext callContext)
    {
        this.conference = conference;
        this.serviceUrl = serviceUrl;
        this.callContext = callContext;
    }

    /**
     * Connects to the service.
     */
    public void connect()
    {
        String token;
        try (
            PemReader pemReader = new PemReader(new InputStreamReader(new FileInputStream(privateKeyFilePath)));
        )
        {
            PemObject pemObject = pemReader.readPemObject();
            token = Util.generateAsapToken(
                Base64.getEncoder().encodeToString(pemObject.getContent()),
                privateKeyId,
                "jitsi",
                "jitsi");
        }
        catch (Exception e)
        {
            logger.error(this.callContext + " Error generating token", e);
            return;
        }

        WebSocketClient webSocketClient = new StandardWebSocketClient();

        this.stompClient = new WebSocketStompClient(webSocketClient);
        this.stompClient.setMessageConverter(new JsonbMessageConverter());
        this.stompClient.setTaskScheduler(taskScheduler); // for heartbeats

        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        // to avoid the connection being closed by the server, client sends every 100ms, server sends every 100ms
        connectHeaders.add(StompHeaders.HEARTBEAT, "100,100");

        this.stompClient.connectAsync(this.serviceUrl, new WebSocketHttpHeaders(), connectHeaders,
            new StompSessionHandlerAdapter()
            {
                public void handleException(StompSession session,
                                            @Nullable StompCommand command,
                                            StompHeaders headers,
                                            byte[] payload,
                                            Throwable exception)
                {
                    logger.error(WebsocketClient.this.callContext + " headers:" + headers
                            + " payload:" + new String(payload), exception);
                }

                public void handleTransportError(StompSession session, Throwable exception)
                {
                    logger.error(WebsocketClient.this.callContext + " Transport error.", exception);
                }

                @Override
                public void afterConnected(StompSession session, StompHeaders connectedHeaders)
                {
                    session.subscribe(
                        "/secured/conference/visitor/topic." + WebsocketClient.this.callContext.getRoomJid().toString(),
                        new StompFrameHandler()
                        {
                            @Override
                            public Type getPayloadType(StompHeaders headers)
                            {
                                return Object.class;
                            }

                            @Override
                            public void handleFrame(StompHeaders headers, Object payload)
                            {
                                if (payload instanceof byte[])
                                {
                                    try
                                    {
                                        Object o = new JSONParser().parse(new String((byte[])payload));

                                        if (o instanceof JSONObject)
                                        {
                                            JSONObject obj = (JSONObject)o;
                                            if (obj.get("status").equals("live"))
                                            {
                                                WebsocketClient.this.callContext.setRequestVisitor(true);

                                                Long delayMs = (Long)obj.get("randomDelayMs");
                                                // now let's connect as visitor after some random delay.

                                                disconnect();

                                                connectTimer.schedule(new TimerTask()
                                                {
                                                    @Override
                                                    public void run()
                                                    {
                                                        WebsocketClient.this.conference.joinConferenceRoom();
                                                    }
                                                }, (long)(Math.random() * delayMs));
                                            }
                                        }
                                    }
                                    catch (Exception e)
                                    {
                                        logger.error(WebsocketClient.this.callContext
                                            + " Error parsing payload:" + new String((byte[])payload), e);
                                    }
                                }
                                else
                                {
                                    logger.warn(WebsocketClient.this.callContext + " Wrong payload type: " + payload);
                                }
                            }
                    });
                }
            });


    }

    public void disconnect()
    {
        if (this.stompClient != null)
        {
            this.stompClient.stop();
        }
    }
}
