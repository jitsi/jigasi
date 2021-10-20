/*
 * Jigasi, the JItsi GAteway to SIP.
 *
 * Copyright @ 2017 Atlassian Pty Ltd
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
package org.jitsi.jigasi.xmpp;

import org.jivesoftware.smack.packet.*;

import java.util.*;

import static org.jivesoftware.smack.packet.StanzaError.Condition.forbidden;

/**
 * This exception is thrown when a request is not from a permitted entity.
 */
public class CallControlAuthorizationException
    extends Exception
{
    private IQ iq;

    /**
     * Creates a new instance of this class.
     *
     * @param iq The IQ which failed the authorization check.
     */
    public CallControlAuthorizationException(IQ iq)
    {
        Objects.requireNonNull(iq);
        this.iq = iq;
    }

    /**
     * Gets the IQ that failed the authorization check.
     *
     * @return the IQ that failed the authorization check.
     */
    public IQ getIq()
    {
        return iq;
    }

    /**
     * Gets an error IQ based on the IQ that failed the validation with
     * a condition of
     * {@link org.jivesoftware.smack.packet.StanzaError.Condition#forbidden}.
     *
     * @return an error stanza.
     */
    public IQ getErrorIq()
    {
        return IQ.createErrorResponse(iq, forbidden);
    }
}
