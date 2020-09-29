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
package org.jitsi.jigasi;

import net.java.sip.communicator.service.protocol.*;

import static org.junit.Assert.assertTrue;

/**
 * Class encapsulates some assertions about {@link SipGatewaySession}.
 *
 * @author Pawel Domas
 * @author Nik Vaessen
 */
public class GatewaySessionAsserts
    implements GatewaySessionListener
{
    @Override
    public void onJvbRoomJoined(AbstractGatewaySession source)
    {
        synchronized (this)
        {
            this.notifyAll();
        }
    }

    @Override
    public void onLobbyWaitReview(ChatRoom lobbyRoom)
    {}

    public void assertJvbRoomJoined(AbstractGatewaySession session, long timeout)
        throws InterruptedException
    {
        synchronized (this)
        {
            session.addListener(this);

            ChatRoom room = session.getJvbChatRoom();

            if (room == null || !room.isJoined())
            {
                this.wait(timeout);
            }

            session.removeListener(this);

            assertTrue(session.getJvbChatRoom().isJoined());
        }
    }
}
