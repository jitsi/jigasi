/*
 * Jitsi Videobridge, OpenSource video conferencing.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.jigasi;

import net.java.sip.communicator.service.protocol.event.*;

/**
 * The stub of {@link CallListener}, so that we don't have to implement all
 * methods whenever we want to listen for only one event.
 *
 * @author Pawel Domas
 */
public class CallListenerAdapter
    implements CallListener
{
    @Override
    public void incomingCallReceived(CallEvent event)
    {

    }

    @Override
    public void outgoingCallCreated(CallEvent event)
    {

    }

    @Override
    public void callEnded(CallEvent event)
    {

    }
}
