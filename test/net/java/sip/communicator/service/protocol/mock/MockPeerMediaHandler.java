/*
 * Jitsi Videobridge, OpenSource video conferencing.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol.mock;

import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.protocol.media.*;
import org.jitsi.service.neomedia.event.*;

/**
 * @author Pawel Domas
 */
public class MockPeerMediaHandler
    extends CallPeerMediaHandler<MockCallPeer>
{
    /**
     * Creates a new handler that will be managing media streams for
     * <tt>peer</tt>.
     *
     * @param peer         the <tt>CallPeer</tt> instance that we will be managing
     *                     media for.
     * @param srtpListener the object that receives SRTP security events.
     */
    public MockPeerMediaHandler(
        MockCallPeer peer,
        SrtpListener srtpListener)
    {
        super(peer, srtpListener);
    }

    @Override
    protected TransportManager<MockCallPeer> getTransportManager()
    {
        return null;
    }

    @Override
    protected TransportManager<MockCallPeer> queryTransportManager()
    {
        return null;
    }

    @Override
    protected void throwOperationFailedException(String message, int errorCode,
                                                 Throwable cause)
        throws OperationFailedException
    {
        throw new OperationFailedException(message, errorCode, cause);
    }
}
