/*
 * Jigasi, the JItsi GAteway to SIP.
 *
 * Copyright @ 2015 Atlassian Pty Ltd
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
package net.java.sip.communicator.service.protocol.mock;

import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.util.*;
import org.jivesoftware.smack.packet.*;
import org.json.simple.*;

import java.util.*;
import java.util.concurrent.*;

/**
 * @author Pawel Domas
 */
public class MockJitsiMeetTools
    implements OperationSetJitsiMeetTools
{
    /**
     * The logger used by this class.
     */
    private final static Logger logger
        = Logger.getLogger(MockJitsiMeetTools.class);

    private final MockProtocolProvider protocolProvider;

    /**
     * The list of {@link JitsiMeetRequestListener}.
     */
    private final List<JitsiMeetRequestListener> requestHandlers
        = new CopyOnWriteArrayList<JitsiMeetRequestListener>();

    public MockJitsiMeetTools(MockProtocolProvider protocolProvider)
    {
        this.protocolProvider = protocolProvider;
    }

    public MockCall mockIncomingGatewayCall(String uri, String roomName)
    {
        return protocolProvider.getTelephony()
                .mockIncomingGatewayCall(uri, roomName);
    }

    @Override
    public void addSupportedFeature(String featureName)
    {
        //FIXME: to be implemented and used in tests
    }

    @Override
    public void removeSupportedFeature(String featureName)
    {

    }

    @Override
    public void sendPresenceExtension(ChatRoom chatRoom,
                                      ExtensionElement extension)
    {
        //FIXME: to be tested
    }

    @Override
    public void removePresenceExtension(
        ChatRoom chatRoom, ExtensionElement packetExtension)
    {
        //FIXME: to be tested
    }

    @Override
    public void setPresenceStatus(ChatRoom chatRoom, String statusName)
    {
        //FIXME: to be tested
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addRequestListener(JitsiMeetRequestListener requestHandler)
    {
        this.requestHandlers.add(requestHandler);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void removeRequestListener(JitsiMeetRequestListener requestHandler)
    {
        this.requestHandlers.remove(requestHandler);
    }

    /**
     * Notifies all registered {@link JitsiMeetRequestListener} about incoming
     * call that contains name of the MUC room which is hosting Jitsi Meet
     * conference.
     * @param call the incoming {@link Call} instance.
     * @param jitsiMeetRoom the name of the chat room of Jitsi Meet conference
     *                      to be joined.
     */
    public void notifyJoinJitsiMeetRoom(Call call, String jitsiMeetRoom)
    {
        boolean handled = false;
        for (JitsiMeetRequestListener l : requestHandlers)
        {
            l.onJoinJitsiMeetRequest(
                call, jitsiMeetRoom, new HashMap<String, String>());
            handled = true;
        }
        if (!handled)
        {
            logger.warn(
                "Unhandled join Jitsi Meet request R:" + jitsiMeetRoom
                    + " C: " + call);
        }
    }

    @Override
    public void sendJSON(CallPeer callPeer, JSONObject jsonObject, Map<String, Object> map)
        throws OperationFailedException
    {
    }
}
