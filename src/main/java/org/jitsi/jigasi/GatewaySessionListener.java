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

/**
 * Class used to listen for various {@link AbstractGatewaySession} state
 * changes.
 *
 * @author Pawel Domas
 * @author Nik Vaessen
 */
public interface GatewaySessionListener<T extends AbstractGatewaySession>
{
    /**
     * Called when a <tt>AbstractGatewaySession</tt> has joined the MUC
     *
     * @param source the {@link AbstractGatewaySession} on which the event
     *               takes place.
     */
    void onJvbRoomJoined(T source);

    /**
     * Called when a <tt>AbstractGatewaySession</tt> has joined the lobby MUC
     *
     * @param lobbyRoom the {@link ChatRoom} representing the lobby room.
     */
    void onLobbyWaitReview(ChatRoom lobbyRoom);
}
