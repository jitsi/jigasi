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
import net.java.sip.communicator.service.protocol.event.*;
import org.jivesoftware.smack.packet.*;

import java.util.*;

/**
 * Class represents gateway session which manages single SIP call instance
 * (outgoing or incoming).
 *
 * @author Pawel Domas
 * @author Nik Vaessen
 */
public abstract class AbstractGatewaySession
    implements OperationSetJitsiMeetTools.JitsiMeetRequestListener,
               DTMFListener
{
    /**
     * The <tt>AbstractGateway</tt> that manages this session.
     */
    protected AbstractGateway gateway;

    /**
     * The call context assigned for the current call.
     */
    protected CallContext callContext;

    /**
     * The <tt>JvbConference</tt> that handles current JVB conference.
     */
    protected JvbConference jvbConference;

    /**
     * Gateway session listeners.
     */
    private final ArrayList<GatewaySessionListener> listeners
            = new ArrayList<>();

    /**
     * Global participant count during this session including the focus.
     */
    private int participantsCount = 0;

    /**
     * Whether media had stopped being received from the gateway side.
     */
    protected boolean gatewayMediaDropped = false;

    /**
     * Configuration property to change the resource used by focus.
     */
    private static final String FOCUSE_RESOURCE_PROP
        = "org.jitsi.jigasi.FOCUS_RESOURCE";

    /**
     * Address of the focus member that has invited us to the conference.
     * Used to identify the focus user and dispose the session when it leaves
     * the room.
     */
    private final String focusResourceAddr;

    /**
     * Creates new <tt>AbstractGatewaySession</tt> that can be used to
     * join a conference by using the {@link #createOutgoingCall()} method.
     *
     * @param gateway the {@link AbstractGateway} the <tt>AbstractGateway</tt>
     *                instance that will control this session.
     * @param callContext the call context that identifies this session.
     */
    public AbstractGatewaySession(AbstractGateway gateway,
                                  CallContext callContext)
    {
        this.gateway = gateway;
        this.callContext = callContext;
        this.focusResourceAddr = JigasiBundleActivator.getConfigurationService()
            .getString(FOCUSE_RESOURCE_PROP, "focus");

    }

    /**
     * Starts new outgoing session by joining JVB conference held in given
     * MUC room.
     */
    public void createOutgoingCall()
    {
        if (jvbConference != null)
        {
            throw new IllegalStateException("Conference in progress");
        }

        jvbConference = new JvbConference(this, callContext);
        jvbConference.start();
    }

    /**
     * Returns the call context for the current session.

     * @return the call context for the current session.
     */
    public CallContext getCallContext()
    {
        return callContext;
    }

    /**
     * Returns the name of the chat room that holds current JVB conference or
     * <tt>null</tt> we're not in any room.
     *
     * @return the name of the chat room that holds current JVB conference or
     *         <tt>null</tt> we're not in any room.
     */
    public String getJvbRoomName()
    {
        return jvbConference != null ? jvbConference.getRoomName() : null;
    }

    /**
     * Returns the url of the conference or null if we're not in a meeting
     *
     * @return the url or null
     */
    public String getMeetingUrl()
    {
        return jvbConference != null ? jvbConference.getMeetingUrl() : null;
    }

    /**
     * Returns <tt>ChatRoom</tt> that hosts JVB conference of this session
     * if we're already/still in this room or <tt>null</tt> otherwise.
     */
    public ChatRoom getJvbChatRoom()
    {
        return jvbConference != null ? jvbConference.getJvbRoom() : null;
    }

    /**
     * Returns <tt>true</tt> if we are currently in JVB conference room.
     * @return <tt>true</tt> if we are currently in JVB conference room.
     */
    public boolean isInTheRoom()
    {
        return jvbConference != null && jvbConference.isInTheRoom();
    }

    /**
     * Method called by <tt>JvbConference</tt> to notify that JVB conference
     * call has been received and is about to be answered.
     *
     * @param incomingCall JVB call instance.
     */
    abstract void onConferenceCallInvited(Call incomingCall);

    /**
     * Method called by <tt>JvbConference</tt> to notify that JVB conference
     * call has started.
     *
     * @param jvbConferenceCall JVB call instance.
     * @return any <tt>Exception</tt> that might occurred during handling of the
     *         event. FIXME: is this still needed ?
     */
    abstract Exception onConferenceCallStarted(Call jvbConferenceCall);

    /**
     * Method called by <tt>JvbConference</tt> to notify that JVB call has
     * ended.
     *
     * @param jvbConference <tt>JvbConference</tt> instance.
     * @param reasonCode the reason code, timeout or nothing if normal hangup
     * @param reason the reason text, timeout or nothing if normal hangup
     */
    abstract void onJvbConferenceStopped(JvbConference jvbConference,
                                         int reasonCode, String reason);

    /**
     * Method called by <tt>JvbConference</tt> to notify that JVB call will end.
     *
     * @param jvbConference <tt>JvbConference</tt> instance.
     * @param reasonCode the reason code, timeout or nothing if normal hangup
     * @param reason the reason text, timeout or nothing if normal hangup
     */
    abstract void onJvbConferenceWillStop(JvbConference jvbConference,
        int reasonCode, String reason);

    /**
     * Method called by <tt>JvbConference</tt> to notify JVB call ended
     * but conference may still be online.
     */
    public void onJvbCallEnded() {}

    /**
     * Method called by <tt>JvbConference</tt> to notify JVB call has been
     * established.
     */
    public void onJvbCallEstablished() {}

    /**
     * Cancels current session by leaving the muc room
     */
    public void hangUp()
    {
        if (jvbConference != null)
        {
            jvbConference.stop();
        }
    }

    /**
     * Adds new {@link GatewaySessionListener} on this instance.
     * @param listener adds new {@link GatewaySessionListener} that will receive
     *                 updates from this instance.
     */
    public void addListener(GatewaySessionListener listener)
    {
        synchronized(listeners)
        {
            if (!listeners.contains(listener))
                listeners.add(listener);
        }
    }

    /**
     * Removes {@link GatewaySessionListener} from this instance.
     * @param listener removes {@link GatewaySessionListener} that will  stop
     *                 receiving updates from this instance.
     */
    public void removeListener(GatewaySessionListener listener)
    {
        synchronized(listeners)
        {
            listeners.remove(listener);
        }
    }

    /**
     * Method called by {@link JvbConference} to notify session that it has
     * joined the room.
     *
     * This method Notifies {@link GatewaySessionListener}(if any) that we have
     * just joined the conference room(call is not started yet - just the MUC).
     */
    void notifyJvbRoomJoined()
    {
        // set initial participant count
        participantsCount += getJvbChatRoom().getMembersCount();

        Iterable<GatewaySessionListener> gwListeners;
        synchronized (listeners)
        {
            gwListeners = new ArrayList<>(listeners);
        }

        for (GatewaySessionListener listener : gwListeners)
        {
            listener.onJvbRoomJoined(this);
        }
    }

    /**
     * Method called by {@link Lobby} to notify session that it has
     * joined the lobby room.
     *
     * This method Notifies {@link GatewaySessionListener}(if any) that we have
     * just joined the lobby room(call is not started yet - just the MUC and we are waiting for a modrator to accept it)
     */
    public void notifyOnLobbyWaitReview(ChatRoom lobbyRoom)
    {
        Iterable<GatewaySessionListener> gwListeners;
        synchronized (listeners)
        {
            gwListeners = new ArrayList<>(listeners);
        }

        for (GatewaySessionListener listener : gwListeners)
        {
            listener.onLobbyWaitReview(lobbyRoom);
        }
    }

    /**
     *  Method called by {@link JvbConference} that it has reached
     *  the maximum number of occupants and gives a chance to the session to
     *  handle it.
     */
    void handleMaxOccupantsLimitReached()
    {}

    /**
     * Method called by {@link JvbConference} to notify session that a member
     * has joined the room
     *
     * Notifies {@link GatewaySessionListener} that member just joined
     * the conference room(MUC) and increments the participant counter
     *
     * @param member the member who joined the JVB conference
     */
    // FIXME: 17/07/17 original documentation is wrong. the listener does not
    // even contain such a method
    void notifyChatRoomMemberJoined(ChatRoomMember member)
    {
        participantsCount++;
    }

    /**
     * Method called by {@link JvbConference} to notify session that a member
     * has left the room.
     *
     * Nothing needs to done in abstract class, but
     * implementation might not actually care; thus not abstract.
     *
     * @param member the member who left the JVB conference
     */
    void notifyChatRoomMemberLeft(ChatRoomMember member)
    {
        /*
         * Note that we do NOT update {@link this#participantsCount} because
         * it is the cumulative count of the participants over the whole session
         */
    }

    /**
     * Method called by {@link JvbConference} to notify session that a member
     * has sent an updated presence packet.
     *
     * @param member the member who left the JVB conference
     * @param presence the updated presence of the member
     */
    void notifyChatRoomMemberUpdated(ChatRoomMember member, Presence presence)
    {
        // We don't need to do anything here.
    }

    /**
     * Returns the cumulative number of participants that were active during
     * this session including the focus.
     *
     * @return the participants count.
     */
    public int getParticipantsCount()
    {
        return participantsCount;
    }

    /**
     * Get the name this instance should have in the conference and with which
     * is can be identified
     *
     * @return the name
     */
    public abstract String getMucDisplayName();

    /**
     * Get whether this GatewaySession will work when xmpp uses translator in
     * conference
     *
     * @return true when this GatewaySession will work with translator, false
     * otherwise
     */
    public abstract boolean isTranslatorSupported();

    /**
     * Get the default status of our participant before we get any state from
     * the <tt>CallPeer</tt>.
     *
     * @return the default status, or null when none desired
     */
    public abstract String getDefaultInitStatus();

    /**
     * Returns the gateway used for this session.
     * @return the gateway used for this session.
     */
    public AbstractGateway getGateway()
    {
        return gateway;
    }

    /**
     * Notify this {@link AbstractGatewaySession} that a conference member has
     * joined the conference
     *
     * @param conferenceMember the conference member who just joined
     */
    void notifyConferenceMemberJoined(ConferenceMember conferenceMember)
    {
        // we don't have anything to do here
    }

    /**
     * Notify this {@link AbstractGatewaySession} that a conference member has
     * left the conference
     *
     * @param conferenceMember the conference member who just left
     */
    void notifyConferenceMemberLeft(ConferenceMember conferenceMember)
    {
        // we don't have anything to do here
    }

    /**
     * Returns whether current gateway is up and running and media is being
     * received or it was dropped.
     * @return current gateway media status.
     */
    public boolean isGatewayMediaDropped()
    {
        return gatewayMediaDropped;
    }

    /**
     * Used to identify the focus user and dispose the session when it leaves
     * the room.
     * @return Address of the focus member that has invited us
     * to the conference.
     */
    public String getFocusResourceAddr()
    {
        return focusResourceAddr;
    }

    /**
     * If muting is supported will mute the participant.
     */
    public abstract void mute();

    /**
     * Whether the gateway implementation supports call resuming. Where we can
     * keep the gateway session while the xmpp call is been disconnected or
     * reconnected and the gateway can wait.
     *
     * @return whether gateway supports call resume.
     */
    public abstract boolean hasCallResumeSupport();
}
