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
import net.java.sip.communicator.service.protocol.event.*;

/**
 * The purpose of this class is to encapsulate the process of waiting for
 * outgoing call get created on specified <tt>OperationSetBasicTelephony</tt>.
 *
 * @author Pawel Domas
 */
public class OutCallListener
    implements CallListener
{
    private Call outgoingCall;

    private OperationSetBasicTelephony telephony;

    public void bind(OperationSetBasicTelephony telephony)
    {
        this.telephony = telephony;

        telephony.addCallListener(this);
    }

    public Call getOutgoingCall(long timeout)
        throws InterruptedException
    {
        synchronized (this)
        {
            if (outgoingCall == null)
            {
                this.wait(timeout);
            }
            return outgoingCall;
        }
    }

    @Override
    public void incomingCallReceived(CallEvent event){ }

    @Override
    public void outgoingCallCreated(CallEvent event)
    {
        telephony.removeCallListener(this);

        synchronized (this)
        {
            outgoingCall = event.getSourceCall();

            this.notifyAll();
        }
    }

    @Override
    public void callEnded(CallEvent event){ }
}
