package org.jitsi.jigasi.transcription;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;


public class WhisperConnectionPoolSingleton
{

    private static WhisperConnectionPoolSingleton instance = null;

    private Map<String, Set<String>> participants = new HashMap<>();

    private HashMap<String, WhisperWebsocket> connections = new HashMap<>();

    public WhisperWebsocket getConnection(String roomId, Participant participant) throws Exception {

        if (!this.connections.containsKey(roomId))
        {
            this.connections.put(roomId, new WhisperWebsocket());
            HashSet participantSet = new HashSet();
            participantSet.add(participant.getDebugName());
            this.participants.put(roomId, participantSet);
        }
        else
        {
            this.participants.get(roomId).add(participant.getDebugName());
        }

        return this.connections.get(roomId);
    }


    public void end(String roomId, String participantId) throws IOException {
        Set<String> participantsSet = this.participants.get(roomId);
        if (participantsSet.contains(participantId)) {
            participantsSet.remove(participantId);
        }

        if (participantsSet.isEmpty()) {
            WhisperWebsocket conn = this.connections.get(roomId);
            conn.disconnectParticipant(participantId);
            if (conn.ended) {
                this.connections.remove(roomId);
            }
        }
    }


    public static WhisperConnectionPoolSingleton getInstance() throws Exception
    {
        if (instance == null)
        {
            synchronized (WhisperConnectionPoolSingleton.class)
            {
                if (instance == null)
                {
                    instance = new WhisperConnectionPoolSingleton();
                }
            }
        }
        return instance;
    }
}
