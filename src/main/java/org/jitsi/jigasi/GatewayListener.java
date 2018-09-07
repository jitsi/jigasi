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
 * Class used to listen for various {@link SipGateway} state changes.
 *
 * @author Damian Minkov
 * @author Nik Vaessen
 */
public interface GatewayListener
{
    /**
     * Called when new session is added to the list of active sessions.
     * @param session the session that was added.
     */
    default void onSessionAdded(AbstractGatewaySession session)
    {}

    /**
     * Called when a session is removed from the list of active sessions.
     * @param session the session that was removed.
     */
    default void onSessionRemoved(AbstractGatewaySession session)
    {}

    /**
     * Called when a session failed to establish.
     * @param session the session that failed.
     */
    default void onSessionFailed(AbstractGatewaySession session)
    {}

    /**
     * Called when the gateway is ready to create new sessions.
     */
    default void onReady()
    {}
}
