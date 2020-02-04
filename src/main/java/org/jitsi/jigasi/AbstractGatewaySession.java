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
     * Configuration property to change the resource used by focus.
     */
    private static final String FOCUSE_RESOURCE_PROP
        = "org.jitsi.jigasi.FOCUS_RESOURCE";

    /**
     * Address of the focus member that has invited us to the conference.
     * Used to identify the focus user and dispose the session when it leaves
     * the room.
     */
    protected final String focusResourceAddr;

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
     * Returns a copy the current list of listeners.
     * @return a copy the current list of listeners.
     */
    private Iterable<GatewaySessionListener> getGatewaySessionListeners()
    {
        synchronized (listeners)
        {
            return new ArrayList<>(listeners);
        }
    }

    /**
     * Method called by <tt>JvbConference</tt> to notify that JVB call has
     * ended.
     *
     * @param jvbConference <tt>JvbConference</tt> instance.
     * @param reasonCode the reason code, timeout or nothing if normal hangup
     * @param reason the reason text, timeout or nothing if normal hangup
     */
    void notifyJvbConferenceStopped(
        JvbConference jvbConference,int reasonCode, String reason)
    {
        for (GatewaySessionListener listener : getGatewaySessionListeners())
        {
            listener.onJvbConferenceStopped(jvbConference, reasonCode, reason);
        }
    }

    /**
     * Method called by <tt>JvbConference</tt> to notify that JvbConference
     * will end.
     *
     * @param jvbConference <tt>JvbConference</tt> instance.
     * @param reasonCode the reason code, timeout or nothing if normal hangup
     * @param reason the reason text, timeout or nothing if normal hangup
     */
    void notifyJvbConferenceWillStop(
        JvbConference jvbConference, int reasonCode, String reason)
    {
        for (GatewaySessionListener listener : getGatewaySessionListeners())
        {
            listener.onJvbConferenceWillStop(jvbConference, reasonCode, reason);
        }
    }

    /**
     * Method called to notify that <tt>JvbConference</tt> was resumed after
     * the conference call was ended and then reestablished again.
     *
     * @param jvbConference <tt>JvbConference</tt> instance.
     */
    void notifyJvbConferenceResumed(JvbConference jvbConference)
    {
        for (GatewaySessionListener listener : getGatewaySessionListeners())
        {
            listener.onJvbConferenceResumed(jvbConference);
        }
    }

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

        this.gateway.onJvbRoomJoined(this);

        for (GatewaySessionListener listener : getGatewaySessionListeners())
        {
            listener.onJvbRoomJoined(this);
        }
    }

    /**
     * Delivers event that a GatewaySession had failed establishing.
     * Typically this happens when room joining failed.
     */
    void notifyGatewaySessionFailed()
    {
        this.gateway.fireGatewaySessionFailed(this);

        for (GatewaySessionListener listener : getGatewaySessionListeners())
        {
            listener.onGatewaySessionFailed();
        }
    }

    /**
     *  Method called by {@link JvbConference} to notify that it has reached
     *  the maximum number of occupants and gives a chance to the session to
     *  handle it.
     */
    void notifyMaxOccupantsLimitReached()
    {
        for (GatewaySessionListener listener : getGatewaySessionListeners())
        {
            listener.onMaxOccupantsLimitReached();
        }
    }

    /**
     * Method called by {@link JvbConference} to notify session that a member
     * has joined the room
     *
     * Notifies this {@link AbstractGatewaySession} that member just joined
     * the conference room(MUC) and increments the participant counter
     *
     * @param member the member who joined the room
     */
    void notifyChatRoomMemberJoined(ChatRoomMember member)
    {
        participantsCount++;

        for (GatewaySessionListener listener : getGatewaySessionListeners())
        {
            listener.onChatRoomMemberJoined(member);
        }
    }

    /**
     * Method called by {@link JvbConference} to notify session that a member
     * has left the room.
     *
     * @param member the member who left the room
     */
    void notifyChatRoomMemberLeft(ChatRoomMember member)
    {
        /*
         * Note that we do NOT update {@link this#participantsCount} because
         * it is the cumulative count of the participants over the whole session
         */

        for (GatewaySessionListener listener : getGatewaySessionListeners())
        {
            listener.onChatRoomMemberLeft(member);
        }
    }

    /**
     * Method called by {@link JvbConference} to notify session that a member
     * has sent an updated presence packet and we received that update.
     *
     * @param member the member who got updated in the room
     */
    void notifyChatRoomMemberUpdated(ChatRoomMember member)
    {
        for (GatewaySessionListener listener : getGatewaySessionListeners())
        {
            listener.onChatRoomMemberUpdated(member);
        }
    }

    /**
     * Method called by {@link JvbConference} to notify that
     * a gateway call (sip) has been created/invited.
     *
     * @param sipCall the sip call created.
     */
    void notifyGatewayCallInvited(Call sipCall)
    {
        for (GatewaySessionListener listener : getGatewaySessionListeners())
        {
            listener.onGatewayCallInvited(sipCall);
        }
    }

    /**
     * Method called by {@link JvbConference} to notify that
     * an invite (session-initiate) was received for the xmpp call.
     * This means there are more than 1 participants in the room.
     *
     * @param xmppCall the xmpp call.
     */
    void notifyConferenceCallInvited(Call xmppCall)
    {
        for (GatewaySessionListener listener : getGatewaySessionListeners())
        {
            listener.onConferenceCallInvited(xmppCall);
        }
    }

    /**
     * Method called by {@link JvbConference} to notify that
     * the call has been terminated (session-terminate).
     *
     * @param xmppCall the xmpp call.
     */
    void notifyConferenceCallEnded(Call xmppCall)
    {
        for (GatewaySessionListener listener : getGatewaySessionListeners())
        {
            listener.onConferenceCallEnded(xmppCall);
        }
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
        for (GatewaySessionListener listener : getGatewaySessionListeners())
        {
            listener.onConferenceMemberJoined(conferenceMember);
        }
    }

    /**
     * Notify this {@link AbstractGatewaySession} that a conference member has
     * left the conference
     *
     * @param conferenceMember the conference member who just left
     */
    void notifyConferenceMemberLeft(ConferenceMember conferenceMember)
    {
        for (GatewaySessionListener listener : getGatewaySessionListeners())
        {
            listener.onConferenceMemberLeft(conferenceMember);
        }
    }
}
