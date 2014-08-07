/*
 * Jitsi Videobridge, OpenSource video conferencing.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.jigasi;

/**
 * Call control interface that manages call session identifiers.
 *
 * @author Pawel Domas
 */
public interface CallsControl
{
    /**
     * Allocates new call resource/URI for given {@link SipGateway}.
     * @param gateway the <tt>SipGateway</tt> that will handle new call.
     * @return call resource/URI for the new call handled by given
     *         {@link SipGateway}.
     */
    public String allocateNewSession(SipGateway gateway);

    /**
     * Method must be called by call handler to notify this
     * <tt>CallsControl</tt> that the call identified by <tt>callResource</tt>
     * has ended.
     * @param gateway the <tt>SipGateway</tt> that handles the call.
     * @param callResource the resource of the call that has ended.
     */
    public void callEnded(SipGateway gateway, String callResource);

    /**
     * Call resource currently has the form of e23gr547@callcontro.server.net.
     * This methods extract random call id part before '@' sign. In the example
     * above it is 'e23gr547'.
     * @param callResource the call resource string from which we want to
     *                     extract the call id.
     * @return extracted random call ID part from full call resource string.
     */
    public String extractCallIdFromResource(String callResource);
}
