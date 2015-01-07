/*
 * Jitsi Videobridge, OpenSource video conferencing.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol.mock;

import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.util.*;
import org.jivesoftware.smack.packet.*;

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
    public void sendPresenceExtension(ChatRoom chatRoom,
                                      PacketExtension extension)
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
            l.onJoinJitsiMeetRequest(call, jitsiMeetRoom, null);
            handled = true;
        }
        if (!handled)
        {
            logger.warn(
                "Unhandled join Jitsi Meet request R:" + jitsiMeetRoom
                    + " C: " + call);
        }
    }
}
