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
