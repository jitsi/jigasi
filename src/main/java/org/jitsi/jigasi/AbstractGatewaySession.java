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
import net.java.sip.communicator.service.protocol.event.*;
import net.java.sip.communicator.service.protocol.media.*;
import net.java.sip.communicator.util.Logger;
import org.jitsi.jigasi.util.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.util.*;

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
     * The logger.
     */
    private final static Logger logger = Logger.getLogger(
        AbstractGatewaySession.class);

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
        return jvbConference.isInTheRoom();
    }

    // FIXME: 17/07/17 undocumented before refactor
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
    void notifyMemberJoined(ChatRoomMember member)
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
    // FIXME: 17/07/17 JvbConference does not yet call this method
    void notifyMemberLeft(ChatRoomMember member)
    {
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
     * Returns the gateway used for this session.
     * @return the gateway used for this session.
     */
    public AbstractGateway getGateway()
    {
        return gateway;
    }
}
