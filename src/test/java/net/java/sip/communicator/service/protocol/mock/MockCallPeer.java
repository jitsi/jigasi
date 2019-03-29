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
import net.java.sip.communicator.util.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.utils.*;

/**
 * @author Pawel Domas
 */
public class MockCallPeer
    extends MediaAwareCallPeer<MockCall, MockPeerMediaHandler,
                               MockProtocolProvider>
{
    /**
     * The logger.
     */
    private final static Logger logger = Logger.getLogger(MockCallPeer.class);

    private final String address;

    private final MockCall call;

    public MockCallPeer(String address, MockCall call)
    {
        super(call);

        this.address = address;
        this.call = call;

        setMediaHandler(new MockPeerMediaHandler(this, null));
    }

    @Override
    public void addConferenceMembersSoundLevelListener(
        ConferenceMembersSoundLevelListener listener)
    {

    }

    @Override
    public void addStreamSoundLevelListener(SoundLevelListener listener)
    {

    }

    @Override
    public String getAddress()
    {
        return address;
    }

    @Override
    public MockCall getCall()
    {
        return call;
    }

    @Override
    public Contact getContact()
    {
        return null;
    }

    @Override
    public String getDisplayName()
    {
        return null;
    }

    @Override
    public byte[] getImage()
    {
        return new byte[0];
    }

    @Override
    public String getPeerID()
    {
        return null;
    }

    @Override
    public String getURI()
    {
        return null;
    }

    @Override
    public void removeConferenceMembersSoundLevelListener(
        ConferenceMembersSoundLevelListener listener)
    {

    }

    @Override
    public void removeStreamSoundLevelListener(SoundLevelListener listener)
    {

    }

    private CallPeerState lastState;

    @Override
    public void setState(CallPeerState newState, String reason, int reasonCode)
    {
        this.lastState = getState();

        super.setState(newState, reason, reasonCode);
    }

    @Override
    public String getEntity()
    {
        throw new UnsupportedOperationException("getEntity");
    }

    @Override
    public MediaDirection getDirection(MediaType mediaType)
    {
        throw new UnsupportedOperationException("getDirection");
    }

    public void putOnHold()
    {
        try
        {
            getMediaHandler().setLocallyOnHold(true);
            setState(CallPeerState.ON_HOLD_LOCALLY);
            logger.info(this + " is now on hold, last state: " + lastState);
        }
        catch (OperationFailedException e)
        {
            throw new RuntimeException(e);
        }
    }

    public void putOffHold()
    {
        try
        {
            getMediaHandler().setLocallyOnHold(false);
            setState(lastState);
            logger.info(this + " is now off hold, switch to: " + lastState);
        }
        catch (OperationFailedException e)
        {
            throw new RuntimeException(e);
        }
    }

}
