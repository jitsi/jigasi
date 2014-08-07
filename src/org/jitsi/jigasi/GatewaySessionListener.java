/*
 * Jitsi Videobridge, OpenSource video conferencing.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.jigasi;


/**
 * Class used to listen for various {@link GatewaySession} state changes.
 *
 * @author Pawel Domas
 */
public interface GatewaySessionListener
{
    /**
     * Called when SIP gateway session has joined the MUC and is now waiting for
     * invite from the focus.
     * @param source the {@link GatewaySession} on which the event takes place.
     */
    void onJvbRoomJoined(GatewaySession source);
}
