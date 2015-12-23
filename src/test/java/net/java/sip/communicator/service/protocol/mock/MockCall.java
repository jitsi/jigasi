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
import net.java.sip.communicator.service.protocol.event.*;
import net.java.sip.communicator.service.protocol.media.*;

/**
 * @author Pawel Domas
 */
public class MockCall
    extends MediaAwareCall<MockCallPeer,
                           MockBasicTeleOpSet,
                           MockProtocolProvider>
{
    /**
     * Creates a new Call instance.
     *
     * @param telephony
     */
    protected MockCall(MockBasicTeleOpSet telephony)
    {
        super(telephony);
    }

    @Override
    public boolean isConferenceFocus()
    {
        return false;
    }

    @Override
    public void addLocalUserSoundLevelListener(SoundLevelListener l)
    {

    }

    @Override
    public void removeLocalUserSoundLevelListener(SoundLevelListener l)
    {

    }

    public void addCallPeer(MockCallPeer peer)
    {
        doAddCallPeer(peer);
    }

    public synchronized void hangup()
    {
        setCallState(CallState.CALL_ENDED);

        for (MockCallPeer peer : getCallPeerList())
        {
            peer.setState(CallPeerState.DISCONNECTED);
        }
    }

    @Override
    public String toString()
    {
        return super.toString() + " " + getProtocolProvider().getProtocolName();
    }

    public void setCallState(CallState newState)
    {
        super.setCallState(newState);
    }
}
