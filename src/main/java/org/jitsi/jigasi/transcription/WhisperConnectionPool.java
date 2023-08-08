package org.jitsi.jigasi.transcription;

import org.jitsi.utils.logging.Logger;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;


public class WhisperConnectionPool
{
    /**
     * The logger class
     */
    private final static Logger logger
            = Logger.getLogger(WhisperConnectionPool.class);

    /**
     * The singleton instance to be returned
     */
    private static WhisperConnectionPool instance = null;

    /**
     * The participants which use the roomId connection
     */
    private final Map<String, Set<String>> participants = new HashMap<>();

    /**
     * The connection pool
     */
    private HashMap<String, WhisperWebsocket> connections = new HashMap<>();

    /**
     * Gets a connection if it exists, creates one if it doesn't.
     * @param roomId
     * @param participantId
     * @return
     * @throws Exception
     */
    public WhisperWebsocket getConnection(String roomId, String participantId) throws Exception {
        if (!connections.containsKey(roomId))
        {
            logger.info("Room " + roomId + " doesn't exist. Creating a new connection.");
            connections.put(roomId, new WhisperWebsocket());
            HashSet participantSet = new HashSet();
            participantSet.add(participantId);
            participants.put(roomId, participantSet);
        }
        else
        {
            logger.info("Participant " + participantId + " already exists in room " + roomId + ".");
            participants.get(roomId).add(participantId);
        }

        return connections.get(roomId);
    }

    /**
     * Ends the connection if all participants have left the room
     * @param roomId
     * @param participantId
     * @throws IOException
     */
    public void end(String roomId, String participantId) throws IOException
    {
        Set<String> participantsSet = participants.get(roomId);
        if (!participantsSet.contains(participantId))
        {
            return;
        }

        participantsSet.remove(participantId);
        if (!participantsSet.isEmpty())
        {
            return;
        }

        WhisperWebsocket conn = this.connections.get(roomId);
        boolean noMoreParticipantsLeft = conn.disconnectParticipant(participantId);
        if (noMoreParticipantsLeft)
        {
            connections.remove(roomId);
        }
    }

    /**
     * Static method to return the instance of the class
     * @return
     * @throws Exception
     */
    public static WhisperConnectionPool getInstance() throws Exception
    {
        if (instance == null)
        {
            synchronized (WhisperConnectionPool.class)
            {
                if (instance == null)
                {
                    instance = new WhisperConnectionPool();
                }
            }
        }
        return instance;
    }
}
