/*
 * Jigasi, the JItsi GAteway to SIP.
 *
 * Copyright @ 2023 8x8 Inc.
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

import org.jitsi.jigasi.util.Util;
import org.jitsi.utils.logging.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * The singleton class manages the WebSocket connections to the Whisper
 * service. We chose to have a single connection per room instead
 * of a single connection per Participant.
 * The WebSocket will disconnect when all Participants left the room.
 *
 * @author rpurdel
 */
public class WhisperConnectionPool
{
    /**
     * The logger class
     */
    private final static Logger logger = Logger.getLogger(WhisperConnectionPool.class);

    /**
     * The singleton instance to be returned
     */
    private static WhisperConnectionPool instance = null;

    /**
     * A hashmap holding the state for each connection
     */
    private final Map<String, WhisperWebsocket> pool = new ConcurrentHashMap<>();

    /**
     * Gets a connection if it exists, creates one if it doesn't.
     * @param roomId The room jid.
     * @return The websocket.
     */
    public WhisperWebsocket getConnection(String roomId)
    {
        if (!pool.containsKey(roomId))
        {
            logger.info("Room " + roomId + " doesn't exist. Creating a new connection.");
            final WhisperWebsocket socket = new WhisperWebsocket();

            socket.connect();

            pool.put(roomId, socket);
        }

        return pool.get(roomId);
    }

    /**
     * Ends the connection if all participants have left the room
     * @param roomId The room jid.
     * @param participantId The participant id.
     */
    public void end(String roomId, String participantId)
    {
        WhisperWebsocket wsConn = pool.getOrDefault(roomId, null);
        if (wsConn == null)
        {
            return;
        }

        wsConn.disconnectParticipant(participantId, allDisconnected ->
        {
            if (allDisconnected)
            {
                pool.remove(roomId);
            }
        });
    }

    /**
     * Static method to return the instance of the class
     * @return The connection pool.
     */
    public static WhisperConnectionPool getInstance()
    {
        synchronized (WhisperConnectionPool.class)
        {
            if (instance == null)
            {
                instance = new WhisperConnectionPool();
            }
        }

        return instance;
    }
}
