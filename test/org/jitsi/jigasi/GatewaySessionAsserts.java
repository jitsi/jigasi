/*
 * Jitsi Videobridge, OpenSource video conferencing.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.jigasi;

import net.java.sip.communicator.service.protocol.*;

import static org.junit.Assert.assertTrue;

/**
 * Class encapsulates some assertions about {@link GatewaySession}.
 *
 * @author Pawel Domas
 */
public class GatewaySessionAsserts
    implements GatewaySessionListener
{
    @Override
    public void onJvbRoomJoined(GatewaySession source)
    {
        synchronized (this)
        {
            this.notifyAll();
        }
    }

    public void assertJvbRoomJoined(GatewaySession session, long timeout)
        throws InterruptedException
    {
        synchronized (this)
        {
            session.setListener(this);

            ChatRoom room = session.getJvbChatRoom();

            if (room == null || !room.isJoined())
            {
                this.wait(timeout);
            }

            session.setListener(null);

            assertTrue(session.getJvbChatRoom().isJoined());
        }
    }
}
