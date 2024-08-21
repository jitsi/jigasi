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

import java.nio.*;

/**
 * The utils for sending/receiving STOMP messages.
 */
public class StompUtils
{
    final static String NEW_LINE = "\n";
    final static String END = "\u0000";
    final static String EMPTY_LINE = "";
    final static String DELIMITER = ":";
    final static ByteBuffer PING_BODY = ByteBuffer.wrap(new byte[] {'\n'});

    private static String buildHeader(String key, String value)
    {
        if (value != null)
        {
            return key + ':' + value + NEW_LINE;
        }
        else
        {
            return key + NEW_LINE;
        }
    }

    /**
     * Builds the connect message to send.
     * @param token The token to authenticate.
     * @param heartbeatOutgoing The ms to send for outgoing heartbeat interval.
     * @param heartbeatIncoming The ms to send for incoming heartbeat interval.
     * @return The message.
     */
    static String buildConnectMessage(String token, long heartbeatOutgoing, long heartbeatIncoming)
    {
        String headers = buildHeader("CONNECT", null);
        headers += buildHeader("accept-version", "1.2,1.1,1.0");
        headers += buildHeader("heart-beat", heartbeatOutgoing + "," + heartbeatIncoming);
        headers += buildHeader("Authorization", "Bearer " + token);

        return headers + NEW_LINE + END;
    }

    /**
     * Builds a subscribe message.
     * @param topic The topic to subscribe to.
     * @return The message.
     */
    static String buildSubscribeMessage(String topic)
    {
        String headers = buildHeader("SUBSCRIBE", null);
        headers += buildHeader("destination", topic);
        headers += buildHeader("id", "1"); // this is the first and only message we send

        return headers + NEW_LINE + END;
    }
}
