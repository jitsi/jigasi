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
package org.jitsi.jigasi.sip;

import net.java.sip.communicator.service.protocol.*;

import org.jitsi.utils.logging.Logger;
import org.json.simple.*;

import java.util.*;

/**
 *
 * Message JSON format
 *
 * Mandatory header :
 *
 * {
 *      i: <Message Id>,
 *      t: <Message Type>,
 *      d: <Data>
 * }
 *
 * <Message Type> integer to identify message type. This integer should be documented.
 *
 * <Message Id> simple integer counter
 *
 * <Data> OPTIONAL data can be any type respecting the message format.
 *
 * This class is used to define sip info protocol.
 */
public class SipInfoJsonProtocol
{
    /**
     * The logger.
     */
    private final static Logger logger = Logger.getLogger(SipInfoJsonProtocol.class);

    /**
     * The message types to be used when creating a new message for sip info.
     */
    public static class MESSAGE_TYPE
    {
        public static final int LOBBY_JOINED = 3;
        public static final int REQUEST_ROOM_ACCESS = 4;
        public static final int LOBBY_LEFT = 5;
        public static final int LOBBY_ALLOWED_JOIN = 6;
        public static final int LOBBY_REJECTED_JOIN = 7;
        public static final int AV_MODERATION_ENABLED = 8;
        public static final int AV_MODERATION_APPROVED = 9;
        public static final int AV_MODERATION_DENIED = 10;
        public static final int SIP_CALL_HEARTBEAT = 11;
        public static final int SIP_CALL_VISITOR = 12;
    }

    private static class MESSAGE_HEADER
    {
        public static final String MESSAGE_ID = "i";
        public static final String MESSAGE_TYPE = "t";
        public static final String MESSAGE_DATA = "d";
    }

    /**
     * Message counter used for message id.
     */
    private int messageCount = 0;

    /**
     * The {@link OperationSetJitsiMeetTools} for SIP leg.
     */
    private final OperationSetJitsiMeetTools jitsiMeetTools;

    /**
     * Constructor.
     *
     * @param jmt Jitsi operation set to use for communication.
     */
    public SipInfoJsonProtocol(OperationSetJitsiMeetTools jmt)
    {
        jitsiMeetTools = jmt;
    }

    /**
     * @return current message count.
     */
    private int getMessageCount()
    {
        return messageCount++;
    }

    /**
     * Sends a SIP INFO with json payload.
     *
     * @param callPeer CallPeer to send the info message.
     * @param jsonObject JSONObject to be sent.
     * @throws OperationFailedException failed sending the json.
     */
    public void sendJson(CallPeer callPeer, JSONObject jsonObject)
        throws OperationFailedException
    {
        try
        {
            jitsiMeetTools.sendJSON(callPeer, jsonObject,
                new HashMap<String, Object>(){{ put("VIA", "SIP.INFO"); }});
        }
        catch (Exception ex)
        {
            int msgId = -1;
            if (jsonObject.containsKey(MESSAGE_HEADER.MESSAGE_ID))
            {
                msgId = (int)jsonObject.get(MESSAGE_HEADER.MESSAGE_ID);
            }

            logger.error("Error when sending message " + msgId);
            throw ex;
        }
    }

    /**
     * Returns the password from a REQUEST_ROOM_ACCESS request type.
     *
     * @param request JSONObject that represents a room access request.
     * @return String that represents a password.
     */
    public static String getPasswordFromRoomAccessRequest(JSONObject request)
    {
        String roomPwd = null;
        if (request.containsKey(MESSAGE_HEADER.MESSAGE_DATA))
        {
            JSONObject jsonData = (JSONObject)request.get(MESSAGE_HEADER.MESSAGE_DATA);
            if (jsonData.containsKey("pwd"))
            {
                roomPwd = (String)jsonData.get("pwd");
            }
        }
        return roomPwd;
    }

    /**
     * Creates new JSONObject to notify lobby was joined.
     *
     * @return JSONObject representing a message to be sent over SIP.
     */
    public JSONObject createLobbyJoinedNotification()
    {
        JSONObject lobbyInitJson = new JSONObject();
        lobbyInitJson.put(MESSAGE_HEADER.MESSAGE_ID, getMessageCount());
        lobbyInitJson.put(MESSAGE_HEADER.MESSAGE_TYPE, MESSAGE_TYPE.LOBBY_JOINED);
        return lobbyInitJson;
    }

    /**
     * Creates new JSONObject to notify lobby was left.
     *
     * @return JSONObject representing a message to be sent over SIP.
     */
    public JSONObject createLobbyLeftNotification()
    {
        JSONObject lobbyLeftJson = new JSONObject();
        lobbyLeftJson.put(MESSAGE_HEADER.MESSAGE_ID, getMessageCount());
        lobbyLeftJson.put(MESSAGE_HEADER.MESSAGE_TYPE, MESSAGE_TYPE.LOBBY_LEFT);
        return lobbyLeftJson;
    }

    /**
     * Create new JSONObject to notify user was allowed to join the main room.
     *
     * @return JSONObject representing a message to be sent over SIP.
     */
    public JSONObject createLobbyAllowedJoinNotification()
    {
        JSONObject lobbyAllowedJson = new JSONObject();
        lobbyAllowedJson.put(MESSAGE_HEADER.MESSAGE_ID, getMessageCount());
        lobbyAllowedJson.put(MESSAGE_HEADER.MESSAGE_TYPE, MESSAGE_TYPE.LOBBY_ALLOWED_JOIN);
        return lobbyAllowedJson;
    }

    /**
     * Create new JSONObject to notify user was rejected to join main room.
     *
     * @return JSONObject representing a message to be sent over SIP.
     */
    public JSONObject createLobbyRejectedJoinNotification()
    {
        JSONObject lobbyRejectedJson = new JSONObject();
        lobbyRejectedJson.put(MESSAGE_HEADER.MESSAGE_ID, getMessageCount());
        lobbyRejectedJson.put(MESSAGE_HEADER.MESSAGE_TYPE, MESSAGE_TYPE.LOBBY_REJECTED_JOIN);
        return lobbyRejectedJson;
    }

    /**
     * Creates a basic JSONObject to be sent over SIP.
     *
     * @param type Used to identify the JSON.
     * @param data Used for JSON additional attributed.
     * @param id Used for identification.
     * @return Formed JSONObject.
     */
    private static JSONObject createSIPJSON(String type, JSONObject data, String id)
    {
        JSONObject req = new JSONObject();
        req.put("type", type);
        req.put("data", data);
        req.put("id", id == null ? UUID.randomUUID().toString() : id);
        return req;
    }

    /**
     * Creates a JSONObject as response to a muteRequest.
     *
     * @param muted <tt>true</tt> if audio was muted, <tt>false</tt> otherwise.
     * @param bSucceeded <tt>true</tt> if muteRequest succeeded, <tt>false</tt> otherwise.
     * @param id Represents id of muteRequest.
     * @return Formed JSONObject.
     */
    public static JSONObject createSIPJSONAudioMuteResponse(boolean muted,
                                                      boolean bSucceeded,
                                                      String id)
    {
        JSONObject muteSettingsJson = new JSONObject();
        muteSettingsJson.put("audio", muted);
        JSONObject muteResponseJson = createSIPJSON("muteResponse", muteSettingsJson, id);
        muteResponseJson.put("status", bSucceeded ? "OK" : "FAILED");
        return muteResponseJson;
    }

    /**
     * Creates a JSONObject to request audio to be muted over SIP.
     *
     * @param muted <tt>true</tt> if audio is to be muted, <tt>false</tt> otherwise.
     * @return Formed JSONObject.
     */
    public static JSONObject createSIPJSONAudioMuteRequest(boolean muted)
    {
        JSONObject muteSettingsJson = new JSONObject();
        muteSettingsJson.put("audio", muted);

        return createSIPJSON("muteRequest", muteSettingsJson, null);
    }

    /**
     * Creates new JSONObject to notify that AV moderation is enabled/disabled.
     *
     * @return JSONObject representing a message to be sent over SIP.
     */
    public static JSONObject createAVModerationEnabledNotification(boolean value)
    {
        JSONObject obj = new JSONObject();

        obj.put(MESSAGE_HEADER.MESSAGE_TYPE, MESSAGE_TYPE.AV_MODERATION_ENABLED);
        obj.put(MESSAGE_HEADER.MESSAGE_DATA, value);

        return obj;
    }

    /**
     * Creates new JSONObject to notify that AV moderation is enabled/disabled.
     *
     * @return JSONObject representing a message to be sent over SIP.
     */
    public static JSONObject createAVModerationApprovedNotification()
    {
        JSONObject obj = new JSONObject();

        obj.put(MESSAGE_HEADER.MESSAGE_TYPE, MESSAGE_TYPE.AV_MODERATION_APPROVED);

        return obj;
    }

    /**
     * Creates new JSONObject to notify that AV moderation is enabled/disabled.
     *
     * @return JSONObject representing a message to be sent over SIP.
     */
    public static JSONObject createAVModerationDeniedNotification()
    {
        JSONObject obj = new JSONObject();

        obj.put(MESSAGE_HEADER.MESSAGE_TYPE, MESSAGE_TYPE.AV_MODERATION_DENIED);

        return obj;
    }

    /**
     * Creates new JSONObject for sip call heartbeat.
     *
     * @return JSONObject representing a message to be sent over SIP.
     */
    public static JSONObject createSIPCallHeartBeat()
    {
        JSONObject obj = new JSONObject();

        obj.put(MESSAGE_HEADER.MESSAGE_TYPE, MESSAGE_TYPE.SIP_CALL_HEARTBEAT);

        return obj;
    }

    /**
     * Creates new JSONObject for sip call visitors.
     *
     * @return JSONObject representing a message to be sent over SIP.
     */
    public static JSONObject createSIPCallVisitors(boolean enabled)
    {
        JSONObject obj = new JSONObject();

        obj.put(MESSAGE_HEADER.MESSAGE_TYPE, MESSAGE_TYPE.SIP_CALL_VISITOR);
        obj.put(MESSAGE_HEADER.MESSAGE_DATA, enabled);

        return obj;
    }
}
