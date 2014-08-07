/*
 * Jitsi Videobridge, OpenSource video conferencing.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
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
