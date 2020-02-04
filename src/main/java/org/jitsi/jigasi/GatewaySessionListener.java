/*
 * Jigasi, the JItsi GAteway to SIP.
 *
 * Copyright @ 2018 - present 8x8, Inc.
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
 * The normal flow of events is:
 * - onJvbRoomJoined
 * - when there are more than one participants we receive onConferenceCallInvited
 *      notifyChatRoomMemberJoined/notifyChatRoomMemberUpdated/notifyChatRoomMemberLeft
 * - onGatewayCallInvited
 *      - in case of outgoing call (sip) we will receive this event after
 *      onConferenceCallInvited, as we initiate the outgoing call after we had
 *      been invited in the conference (after joining the room).
 *      - in case of incoming call (sip) we will receive this event just before
 *      create JvbConference (before onJvbRoomJoined and onConferenceCallInvited)
 * - onConferenceMemberJoined/onConferenceMemberLeft - when participants
 *   join/leave the conference (used to match ssrc coming from peers to
 *   Participants)
 * - onConferenceCallEnded - when the conference call ends, because we receive
 *   session-terminate xmpp message or the call itself is Ended. If we have not
 *   received event that the JvbConference is stopped the gateway call (sip) can
 *   wait and we can later resume the ConferenceCall.
 *   There are cases where we would not receive this event but will just
 *   receive onJvbConferenceStopped directly and no ConferenceCall to be resumed
 *   and the we need to hangup gateway(sip) call.
 *
 * @author Pawel Domas
 * @author Nik Vaessen
 * @author Damian Minkov
 */
public interface GatewaySessionListener<T extends AbstractGatewaySession>
{
    /**
     * Called when a <tt>AbstractGatewaySession</tt> has joined the MUC/room
     *
     * @param source the {@link AbstractGatewaySession} on which the event
     *               takes place.
     */
    void onJvbRoomJoined(T source);

    /**
     * Called to notify session that a member has joined the room
     *
     * Notifies this {@link AbstractGatewaySession} that member just joined
     * the conference room(MUC) and increments the participant counter
     *
     * @param member the member who joined the room
     */
    void onChatRoomMemberJoined(ChatRoomMember member);

    /**
     * Method called to notify session that a member has been update.
     *
     * @param member the member who was updated in the room
     */
    void onChatRoomMemberUpdated(ChatRoomMember member);

    /**
     * Method called to notify session that a member has left the room.
     *
     * @param member the member who left the room
     */
    void onChatRoomMemberLeft(ChatRoomMember member);

    /**
     * A gateway call was invites.
     * @param gwCall the call that was invites/created.
     */
    void onGatewayCallInvited(Call gwCall);

    /**
     * Method called to notify that xmpp conference call has been received and
     * is about to be answered.
     *
     * @param xmppCall the xmpp call instance.
     */
    void onConferenceCallInvited(Call xmppCall);

    /**
     * The xmpp Conference call has been terminated (session-terminate).
     * @param xmppCall the xmpp call that ended.
     */
    void onConferenceCallEnded(Call xmppCall);

    /**
     * Notify that a conference member has joined the conference.
     * This is the media part of the conference (different from the room
     * participants events and instances).
     *
     * @param conferenceMember the conference member who just joined
     */
    void onConferenceMemberJoined(ConferenceMember conferenceMember);

    /**
     * Notify that a conference member has left the conference
     * This is the media part of the conference (different from the room
     * participants events and instances).
     *
     * @param conferenceMember the conference member who just left
     */
    void onConferenceMemberLeft(ConferenceMember conferenceMember);

    /**
     * Method called to notify that JvbConference will end.
     *
     * @param jvbConference <tt>JvbConference</tt> instance.
     * @param reasonCode the reason code, timeout or nothing if normal hangup
     * @param reason the reason text, timeout or nothing if normal hangup
     */
    void onJvbConferenceWillStop(JvbConference jvbConference,
                                 int reasonCode, String reason);

    /**
     * Method called to notify that JvbConference has ended.
     *
     * @param jvbConference <tt>JvbConference</tt> instance.
     * @param reasonCode the reason code, timeout or nothing if normal hangup
     * @param reason the reason text, timeout or nothing if normal hangup
     */
    void onJvbConferenceStopped(JvbConference jvbConference,
                                int reasonCode, String reason);

    /**
     * After ConferenceCallEnded in a <tt>JvbConference</tt> the
     * Conference call was reestablished and the conference continues normally.
     *
     * @param jvbConference <tt>JvbConference</tt> instance.
     */
    void onJvbConferenceResumed(JvbConference jvbConference);


    /**
     *  Method called to notify that server has reached the maximum number of
     *  occupants and gives a chance to the session to handle it.
     */
    void onMaxOccupantsLimitReached();

    /**
     * Delivers event that a GatewaySession had failed establishing.
     * Typically this happens when room joining failed.
     */
    void onGatewaySessionFailed();
}
