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
