/*
 * Jitsi Videobridge, OpenSource video conferencing.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.jigasi;

import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.protocol.event.*;

import static org.junit.Assert.assertEquals;

/**
 * Class used to encapsulate the process of waiting for some <tt>CallState</tt>
 * set on given <tt>Call</tt> instance.
 *
 * @author Pawel domas
 */
public class CallStateListener
    extends CallChangeAdapter
{
    private CallState targetState;

    @Override
    public void callStateChanged(CallChangeEvent evt)
    {
        Call call = evt.getSourceCall();
        if (targetState.equals(call.getCallState()))
        {
            synchronized (this)
            {
                this.notifyAll();
            }
        }
    }

    public void waitForState(Call      watchedCall,
                             CallState targetState,
                             long      timeout)
        throws InterruptedException
    {
        this.targetState = targetState;

        // FIXME: we can miss call state anyway ?(but timeout will release)
        if (!targetState.equals(watchedCall.getCallState()))
        {
            synchronized (this)
            {
                watchedCall.addCallChangeListener(this);

                this.wait(timeout);
            }
        }

        watchedCall.removeCallChangeListener(this);

        assertEquals(targetState, watchedCall.getCallState());
    }
}