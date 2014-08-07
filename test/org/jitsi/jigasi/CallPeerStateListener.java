/*
 * Jitsi Videobridge, OpenSource video conferencing.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.jigasi;

import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.protocol.event.*;

import java.util.*;

import static org.junit.Assert.assertEquals;

/**
 * Class used to wait for and assert specified {@link CallPeerState} on
 * selected call peer.
 *
 * @author Pawel Domas
 */
public class CallPeerStateListener
    extends CallPeerAdapter
{
    private CallPeerState targetState;

    @Override
    public void peerStateChanged(CallPeerChangeEvent evt)
    {
        super.peerStateChanged(evt);

        CallPeer peer = evt.getSourceCallPeer();
        if (targetState.equals(peer.getState()))
        {
            synchronized (this)
            {
                this.notifyAll();
            }
        }
    }

    public void waitForState(Call          call,
                             int           peerIdx,
                             CallPeerState targetState,
                             long          timeout)
        throws InterruptedException
    {
        this.targetState = targetState;

        CallPeer watchedPeer = getCallPeer(call, peerIdx);

        // FIXME: we can miss call state anyway ?(but timeout will release)
        if (!targetState.equals(watchedPeer.getState()))
        {
            synchronized (this)
            {
                watchedPeer.addCallPeerListener(this);

                this.wait(timeout);
            }
        }

        watchedPeer.removeCallPeerListener(this);

        assertEquals(targetState, watchedPeer.getState());
    }

    private CallPeer getCallPeer(Call call, int peerIdx)
    {
        if (peerIdx >= call.getCallPeerCount())
            throw new IllegalArgumentException(
                "Peers idx: " + peerIdx
                    + " per count: " + call.getCallPeerCount());

        Iterator<? extends CallPeer> peers = call.getCallPeers();
        CallPeer watchedPeer = peers.next();
        for (int i=0; i < peerIdx; i++)
        {
            watchedPeer = peers.next();
        }

        return watchedPeer;
    }
}
