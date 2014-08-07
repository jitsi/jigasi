/*
 * Jitsi Videobridge, OpenSource video conferencing.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol.mock;

import net.java.sip.communicator.service.protocol.*;
import org.jivesoftware.smack.packet.*;

/**
 * @author Pawel Domas
 */
public class MockJitsiMeetTools
    implements OperationSetJitsiMeetTools
{
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
}
