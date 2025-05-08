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

import org.bouncycastle.util.io.pem.*;
import org.eclipse.jetty.websocket.client.*;
import org.eclipse.jetty.websocket.api.*;
import org.jitsi.jigasi.*;
import org.jitsi.jigasi.util.*;
import org.jitsi.utils.concurrent.*;
import org.jitsi.utils.logging2.*;
import org.json.simple.*;
import org.json.simple.parser.*;

import java.io.*;
import java.net.*;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.*;

import static org.jitsi.jigasi.visitor.StompUtils.*;

/**
 * The websocket client to connect to the visitors queue.
 * It implements the necessary parts to use STOMP (https://stomp.github.io/stomp-specification-1.2.html).
 */
public class WebsocketClient
    implements WebSocketListener
{
    /**
     * The logger.
     */
    private final Logger logger;

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
    private static final ScheduledExecutorService heartbeatThreadPool = Executors.newScheduledThreadPool(
            1, new CustomizableThreadFactory("stomp-heartbeat-client-thread-", true));

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
     * The websocket session used to communicate with the service.
     */
    private Session websocketSession;

    /**
     * The last time we saw a message from the server. It should be sending us pings every 15 seconds or so.
     */
    private long lastServerActivity;

    /**
     * The outgoing heartbeat sent to server interval in ms.
     */
    private long heartbeatOutgoing = 15000;

    /**
     * The incoming heartbeat sent to server interval in ms.
     */
    private long heartbeatIncoming = 15000;

    /**
     * The tasks for sending heartbeats, used to cancel the pinger.
     */
    private ScheduledFuture pinger;

    /**
     * The tasks for checking of received heartbeats, used to cancel the ponger.
     */
    private ScheduledFuture ponger;

    private final JSONParser jsonParser = new JSONParser();

    /**
     * A timer which will be used to schedule connection to conference after going live.
     */
    private static final Timer connectTimer = new Timer();

    public WebsocketClient(JvbConference conference, String serviceUrl, CallContext callContext)
    {
        this.conference = conference;
        this.serviceUrl = serviceUrl;
        this.callContext = callContext;
        this.logger = callContext.getLogger().createChildLogger(WebsocketClient.class.getName());
    }

    /**
     * Connects to the service.
     */
    public void connect()
    {
        try
        {
            WebSocketClient client = new WebSocketClient();
            client.start();

            Future<Session> fut = client.connect(this, new URI(this.serviceUrl));

            fut.get(5, TimeUnit.SECONDS);
        }
        catch (Exception e)
        {
            logger.error("Error starting websocket client", e);
        }
    }

    public void disconnect()
    {
        if (this.websocketSession != null)
        {
            this.websocketSession.close();
        }

        if (this.pinger != null)
        {
            this.pinger.cancel(false);
        }
        if (this.ponger != null)
        {
            this.ponger.cancel(false);
        }
    }

    @Override
    public void onWebSocketClose(int statusCode, String reason)
    {
        // local close
        if (statusCode == 1006)
        {
            return;
        }

        logger.error("Visitors queue websocket closed: " + statusCode + " " + reason);
    }

    @Override
    public void onWebSocketConnect(Session session)
    {
        this.websocketSession = session;

        sendConnect();
    }

    @Override
    public void onWebSocketError(Throwable cause)
    {
        // local close
        if (cause instanceof ClosedChannelException)
        {
            return;
        }

        logger.error("Visitors queue websocket error: " + cause);

        reconnect();
    }

    /**
     * Sends Stomp connect message over the websocket. Includes the jwt to authorize us.
     */
    private void sendConnect()
    {
        String token;
        try (
                PemReader pemReader = new PemReader(new InputStreamReader(new FileInputStream(privateKeyFilePath)))
        )
        {
            PemObject pemObject = pemReader.readPemObject();
            token = Util.generateAsapToken(Base64.getEncoder().encodeToString(pemObject.getContent()),
                privateKeyId, "jitsi", "jitsi");
        }
        catch (Exception e)
        {
            logger.error("Error generating token", e);
            this.disconnect();

            return;
        }

        this.websocketSession.getRemote().sendString(
            buildConnectMessage(token, this.heartbeatOutgoing, this.heartbeatIncoming), WriteCallback.NOOP);
    }

    /**
     * Receives data over the websocket.
     * @param message the message that is received.
     */
    @Override
    public void onWebSocketText(String message)
    {
        lastServerActivity = System.currentTimeMillis();

        String[] splitMessage = message.split(NEW_LINE);

        if (splitMessage.length == 0)
        {
            // this is a ping from server.
            return;
        }

        String command = splitMessage[0];

        int cursor = 1;
        for (int i = cursor; i < splitMessage.length; i++)
        {
            // empty line
            if (splitMessage[i].equals(EMPTY_LINE))
            {
                // this is where the body starts
                cursor = i;
                break;
            }
            else
            {
                String[] header = splitMessage[i].split(DELIMITER);

                if (header[0].equals("heart-beat"))
                {
                    processHeartbeat(header[1]);
                }
            }
        }

        StringBuilder bodyBuffer = new StringBuilder();

        for (int i = cursor; i < splitMessage.length; i++)
        {
            bodyBuffer.append(splitMessage[i]);
        }

        handleCommand(command, bodyBuffer.toString());
    }

    /**
     * Process the header about heartbeat coming from server on CONNECTED command, gets the max as per spec
     * between local default values and remote settings.
     * @param value the header value.
     */
    private void processHeartbeat(String value)
    {
        String[] splitMessage = value.trim().split(",");

        if (splitMessage.length > 2)
        {
            return;
        }

        long sx = Long.parseLong(splitMessage[0]);
        long sy = Long.parseLong(splitMessage[1]);

        if (sy == 0)
        {
            this.heartbeatOutgoing = 0;
        }
        else
        {
            this.heartbeatOutgoing = Math.max(this.heartbeatOutgoing, sy);
        }

        if (sx == 0)
        {
            this.heartbeatIncoming = 0;
        }
        else
        {
            this.heartbeatIncoming = Math.max(this.heartbeatIncoming, sx);
        }
    }

    /**
     * Starts the executions of sending pings and checking for received pings from server.
     */
    private void setupHeartbeat()
    {
        if (this.heartbeatOutgoing > 0)
        {
            this.pinger = heartbeatThreadPool.scheduleAtFixedRate(() -> {
                if (!this.websocketSession.isOpen())
                {
                    return;
                }

                try
                {
                    this.websocketSession.getRemote().sendBytes(PING_BODY);
                }
                catch (IOException e)
                {
                    logger.error("Error pinging websocket", e);
                }
            }, this.heartbeatOutgoing, this.heartbeatOutgoing, TimeUnit.MILLISECONDS);

        }

        if (this.heartbeatIncoming > 0)
        {
            this.ponger = heartbeatThreadPool.scheduleAtFixedRate(() -> {
                // wait twice the interval to be tolerant of timing inaccuracies
                if (System.currentTimeMillis() - lastServerActivity > this.heartbeatIncoming * 2)
                {
                    logger.error("Visitors queue websocket heartbeat incoming time out");

                    reconnect();
                }
            }, this.heartbeatIncoming, this.heartbeatIncoming, TimeUnit.MILLISECONDS);
        }
    }

    private void handleCommand(String command, String body)
    {
        if (command.equals("CONNECTED"))
        {
            setupHeartbeat();

            this.websocketSession.getRemote().sendString(
                    buildSubscribeMessage("/secured/conference/visitor/topic."
                            + this.callContext.getRoomJid().toString()), WriteCallback.NOOP);
        }
        else if (command.equals("MESSAGE"))
        {
            try
            {
                Object o = jsonParser.parse(body.replace(END, ""));

                if (o instanceof JSONObject)
                {
                    JSONObject obj = (JSONObject)o;
                    if (obj.get("status").equals("live"))
                    {
                        logger.info("Conference is live now.");

                        WebsocketClient.this.callContext.setRequestVisitor(true);

                        Long delayMs = (Long)obj.get("randomDelayMs");
                        // now let's connect as a visitor after some random delay.

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
                logger.error("Error parsing payload:" + body, e);
            }
        }
        else
        {
            logger.warn("Unknown command: " + command);
        }
    }

    private void reconnect()
    {
        this.disconnect();
        long delay = (long)(Math.random() * 5000);

        logger.info("Reconnecting visitors queue in " + delay + " ms.");

        connectTimer.schedule(new TimerTask()
        {
            @Override
            public void run()
            {
                connect();
            }
        }, delay); // let's reconnect randomly in the next 5 seconds
    }
}
