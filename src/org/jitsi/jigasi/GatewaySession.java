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
import net.java.sip.communicator.util.Logger;
import org.jitsi.util.*;
import org.jivesoftware.smack.packet.*;

import java.text.*;

/**
 * Class represents gateway session which manages single SIP call instance
 * (outgoing or incoming).
 *
 * @author Pawel Domas
 */
public class GatewaySession
    implements OperationSetJitsiMeetTools.JitsiMeetRequestListener,
               DTMFListener
{
    /**
     * The logger.
     */
    private final static Logger logger = Logger.getLogger(GatewaySession.class);

    /**
     * The <tt>SipGateway</tt> that manages this session.
     */
    private SipGateway sipGateway;

    /**
     * The {@link OperationSetJitsiMeetTools} for SIP leg.
     */
    private final OperationSetJitsiMeetTools jitsiMeetTools;

    /**
     * The <tt>JvbConference</tt> that handles current JVB conference.
     */
    private JvbConference jvbConference;

    /**
     * The SIP call instance if any SIP call is active.
     */
    private Call call;

    /**
     * Stores JVB call instance that will be merged into single conference with
     * SIP call.
     */
    private Call jvbConferenceCall;

    /**
     * Object listens for SIP call state changes.
     */
    private final SipCallStateListener callStateListener
        = new SipCallStateListener();

    /**
     * Peers state listener that publishes peer state in MUC presence status.
     */
    private CallPeerListener peerStateListener;

    /**
     * IF we work in outgoing connection mode then this field contains the SIP
     * number to dial.
     */
    private String destination;

    /**
     * The call resource assigned by {@link CallsControl} for the current call.
     */
    private String callResource;

    /**
     * SIP protocol provider instance.
     */
    private ProtocolProviderService sipProvider;

    /**
     * FIXME: to be removed ?
     */
    private final Object waitLock = new Object();

    /**
     * FIXME: JVB room name property is not available at the moment when call
     *        is created, because header is not parsed yet
     */
    private WaitForJvbRoomNameThread waitThread;

    /**
     * Gateway session listener.
     */
    private GatewaySessionListener listener;

    /**
     * Creates new <tt>GatewaySession</tt> for given <tt>callResource</tt>
     * and <tt>sipCall</tt>. We already have SIP call instance, so this session
     * can be considered "incoming" SIP session(was created after incoming call
     * had been received).
     *
     * @param gateway the <tt>SipGateway</tt> instance that will control this
     *                session.
     * @param callResource the call resource/URI that identifies this session.
     * @param sipCall the incoming SIP call instance which will be handled by
     *                this session.
     */
    public GatewaySession(SipGateway gateway,
                          String     callResource,
                          Call       sipCall)
    {
        this(gateway);
        this.callResource = callResource;
        this.call = sipCall;
    }

    /**
     * Creates new <tt>GatewaySession</tt> that can be used to initiate outgoing
     * SIP gateway session by using
     * {@link #createOutgoingCall(String, String, String, String)} method.
     *
     * @param gateway the {@link SipGateway} the <tt>SipGateway</tt> instance
     *                that will control this session.
     */
    public GatewaySession(SipGateway gateway)
    {
        this.sipGateway = gateway;
        this.sipProvider = gateway.getSipProvider();
        this.jitsiMeetTools
            = sipProvider.getOperationSet(
                    OperationSetJitsiMeetTools.class);
    }

    private void allCallsEnded()
    {
        String resource = callResource;

        destination = null;

        callResource = null;

        sipGateway.notifyCallEnded(resource);
    }

    private void cancelWaitThread()
    {
        if (waitThread != null)
        {
            waitThread.cancel();
        }
    }

    /**
     * Starts new outgoing session by dialing given SIP number and joining JVB
     * conference held in given MUC room.
     * @param destination the destination SIP number that will be called.
     * @param jvbRoomName the name of MUC that holds JVB conference that will be
     *                    joined.
     * @param roomPass optional password required to enter MUC room.
     * @param callResource the call resource that will identify new call.
     */
    public void createOutgoingCall(
        String destination, String jvbRoomName,
        String roomPass,    String callResource)
    {
        if (jvbConference != null)
        {
            throw new IllegalStateException("Conference in progress");
        }

        if (call != null)
        {
            throw new IllegalStateException("SIP call in progress");
        }

        this.destination = destination;
        this.callResource = callResource;

        jvbConference = new JvbConference(this, jvbRoomName, roomPass);

        jvbConference.start();
    }

    /**
     * Returns the call resource/URI for currently active call.
     * @return the call resource/URI for currently active call.
     */
    public String getCallResource()
    {
        return callResource;
    }

    /**
     * Returns the <tt>CallsControl</tt> that manages this instance.
     * @return the <tt>CallsControl</tt> that manages this instance.
     */
    public CallsControl getCallsControl()
    {
        return sipGateway.getCallsControl();
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
     * Returns SIP destination address for outgoing SIP call.
     * @return SIP destination address for outgoing SIP call.
     */
    public String getDestination()
    {
        return destination;
    }

    /**
     * Returns the instance of SIP call if any is currently in progress.
     * @return the instance of SIP call if any is currently in progress.
     */
    public Call getSipCall()
    {
        return call;
    }

    /**
     * Returns name of the XMPP server that hosts JVB conference room.
     */
    public String getXmppServerName()
    {
        return sipGateway.getXmppServerName();
    }

    public void hangUp()
    {
        hangUp(-1, null);
    }

    /**
     * Cancels current session.
     */
    public void hangUp(int reasonCode, String reason)
    {
        cancelWaitThread();

        if (jvbConference != null)
        {
            jvbConference.stop();
        }
        else if (call != null)
        {
            if (reasonCode != -1)
                CallManager.hangupCall(call, reasonCode, reason);
            else
                CallManager.hangupCall(call);
        }
    }

    private void joinJvbConference(String conferenceRoomName, String password)
    {
        cancelWaitThread();

        jvbConference
            = new JvbConference(this, conferenceRoomName, password);

        jvbConference.start();
    }

    /*private void joinSipWithJvbCalls()
    {
        List<Call> calls = new ArrayList<Call>();
        calls.add(call);
        calls.add(jvbConferenceCall);

        CallManager.mergeExistingCalls(
            jvbConferenceCall.getConference(), calls);

        sendPresenceExtension(
            createPresenceExtension(
                SipGatewayExtension.STATE_IN_PROGRESS, null));

        jvbConference.setPresenceStatus(
            SipGatewayExtension.STATE_IN_PROGRESS);
    }*/

    void onConferenceCallInvited(Call incomingCall)
    {
        // Incoming SIP connection mode sets common conference here
        if (destination == null)
        {
            call.setConference(incomingCall.getConference());
        }
    }

    /**
     * Method called by <tt>JvbConference</tt> to notify that JVB conference
     * call has started.
     * @param jvbConferenceCall JVB call instance.
     * @return any <tt>Exception</tt> that might occurred during handling of the
     *         event. FIXME: is this still needed ?
     */
    Exception onConferenceCallStarted(Call jvbConferenceCall)
    {
        this.jvbConferenceCall = jvbConferenceCall;

        if (destination == null)
        {
            CallManager.acceptCall(call);
        }
        else
        {
            //sendPresenceExtension(
              //  createPresenceExtension(
                //    SipGatewayExtension.STATE_RINGING, null));

            //if (jvbConference != null)
            //{
              //  jvbConference.setPresenceStatus(
                //    SipGatewayExtension.STATE_RINGING);
            //}

            // Make an outgoing call
            final OperationSetBasicTelephony tele
                = sipProvider.getOperationSet(
                        OperationSetBasicTelephony.class);
            // add listener to detect call creation, and add extra headers
            // before inviting, and remove the listener when job is done
            tele.addCallListener(new CallListener()
            {
                @Override
                public void incomingCallReceived(CallEvent callEvent)
                {}

                @Override
                public void outgoingCallCreated(CallEvent callEvent)
                {
                    String roomName = getJvbRoomName();
                    if(roomName != null)
                    {
                        Call call = callEvent.getSourceCall();
                        call.setData("EXTRA_HEADER_NAME.1",
                            "Jitsi-Conference-Room");
                        call.setData("EXTRA_HEADER_VALUE.1", roomName);
                    }

                    tele.removeCallListener(this);
                }

                @Override
                public void callEnded(CallEvent callEvent)
                {
                    tele.removeCallListener(this);
                }
            });
            try
            {
                this.call = tele.createCall(destination);

                peerStateListener = new CallPeerListener(this.call);

                // Outgoing SIP connection mode sets common conference object
                // just after the call has been created
                call.setConference(jvbConferenceCall.getConference());

                logger.info(
                    "Created outgoing call to " + destination + " " + call);

                this.call.addCallChangeListener(callStateListener);

                //FIXME: It might be already in progress or ended ?!
                if (!CallState.CALL_INITIALIZATION.equals(call.getCallState()))
                {
                    callStateListener.handleCallState(call, null);
                }
            }
            catch (OperationFailedException e)
            {
                return e;
            }
            catch (ParseException e)
            {
                return e;
            }
        }

        return null;
    }

    /**
     * Caled by <tt>JvbConference</tt> to notify that JVB call has ended.
     * @param jvbConference <tt>JvbConference</tt> instance.
     */
    void onJvbConferenceStopped(JvbConference jvbConference,
                                int reasonCode, String reason)
    {
        this.jvbConference = null;

        if (call != null)
        {
            hangUp(reasonCode, reason);
        }
        else
        {
            allCallsEnded();
        }
    }

    private void sendPresenceExtension(PacketExtension extension)
    {
        if (jvbConference != null)
        {
            jvbConference.sendPresenceExtension(extension);
        }
        else
        {
            logger.error(
                "JVB conference unavailable. Failed to send: "
                    + extension.toXML());
        }
    }

    private void sipCallEnded()
    {
        if (call == null)
            return;

        logger.info("Sip call ended: " + call.toString());

        call.removeCallChangeListener(callStateListener);

        call = null;

        if (jvbConference != null)
        {
            jvbConference.stop();
        }
        else
        {
            allCallsEnded();
        }
    }

    @Override
    public void onJoinJitsiMeetRequest(Call call, String room, String pass)
    {
        if (jvbConference == null && this.call == call)
        {
            if (room != null)
            {
                joinJvbConference(room, pass);
            }
        }
    }

    /**
     * Initializes this instance for incoming call which was passed to the
     * constructor {@link #GatewaySession(SipGateway, String, Call)}.
     */
    void initIncomingCall()
    {
        call.addCallChangeListener(callStateListener);

        peerStateListener = new CallPeerListener(call);

        if (jvbConference != null)
        {
            // Reject incoming call
            CallManager.hangupCall(call);
        }
        else
        {
            waitForRoomName();
        }
    }

    private void waitForRoomName()
    {
        if (waitThread != null)
        {
            throw new IllegalStateException("Wait thread exists");
        }

        waitThread = new WaitForJvbRoomNameThread();

        jitsiMeetTools.addRequestListener(this);

        waitThread.start();
    }

    /**
     * Returns {@link Call} instance for JVB leg of the conference.
     */
    public Call getJvbCall()
    {
        return jvbConferenceCall;
    }

    /**
     * Returns {@link GatewaySessionListener} currently bound to this instance.
     */
    public GatewaySessionListener getListener()
    {
        return listener;
    }

    /**
     * Sets new {@link GatewaySessionListener} on this instance.
     * @param listener sets new {@link GatewaySessionListener} that will receive
     *                 updates from this instance.
     */
    public void setListener(GatewaySessionListener listener)
    {
        this.listener = listener;
    }

    /**
     * Notifies {@link GatewaySessionListener}(if any) that we have just joined
     * the conference room(call is not started yet - just the MUC).
     */
    void notifyJvbRoomJoined()
    {
        if (listener != null)
        {
            listener.onJvbRoomJoined(this);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void toneReceived(DTMFReceivedEvent dtmfReceivedEvent)
    {
        if (dtmfReceivedEvent != null
                && dtmfReceivedEvent.getSource() == jvbConferenceCall)
        {
            OperationSetDTMF opSet
                    = sipProvider.getOperationSet(OperationSetDTMF.class);
            if (opSet != null && dtmfReceivedEvent.getStart() != null)
            {
                if (dtmfReceivedEvent.getStart())
                {
                    try
                    {
                        opSet.startSendingDTMF(
                                peerStateListener.thePeer,
                                dtmfReceivedEvent.getValue());
                    }
                    catch (OperationFailedException ofe)
                    {
                        logger.info("Failed to forward a DTMF tone: " + ofe);
                    }
                }
                else
                {
                    opSet.stopSendingDTMF(peerStateListener.thePeer);
                }
            }
        }
    }

    class SipCallStateListener
        implements CallChangeListener
    {

        @Override
        public void callPeerAdded(CallPeerEvent evt) { }

        @Override
        public void callPeerRemoved(CallPeerEvent evt)
        {
            //if (evt.getSourceCall().getCallPeerCount() == 0)
            //  sipCallEnded();
        }

        @Override
        public void callStateChanged(CallChangeEvent evt)
        {
            //logger.info("SIP call " + evt);

            handleCallState(evt.getSourceCall(), evt.getCause());
        }

        public void handleCallState(Call call, CallPeerChangeEvent cause)
        {
            // Once call is started notify SIP gateway
            if (call.getCallState() == CallState.CALL_IN_PROGRESS)
            {
                logger.info("Sip call IN_PROGRESS: " + call);
                //sendPresenceExtension(
                  //  createPresenceExtension(
                    //    SipGatewayExtension.STATE_IN_PROGRESS, null));

                //jvbConference.setPresenceStatus(
                  //  SipGatewayExtension.STATE_IN_PROGRESS);

                logger.info("SIP call format used: "
                                + Util.getFirstPeerMediaFormat(call));
            }
            else if(call.getCallState() == CallState.CALL_ENDED)
            {
                // If we have something to show and we're still in the MUC
                // then we display error reason string and leave the room with
                // 5 sec delay.
                if (cause != null
                    && jvbConference != null && jvbConference.isInTheRoom())
                {
                    // Show reason instead of disconnected
                    if (!StringUtils.isNullOrEmpty(cause.getReasonString()))
                    {
                        peerStateListener.unregister();

                        jvbConference.setPresenceStatus(
                            cause.getReasonString());
                    }

                    // Delay 5 seconds
                    new Thread(new Runnable()
                    {
                        @Override
                        public void run()
                        {
                            try
                            {
                                Thread.sleep(5000);

                                sipCallEnded();
                            }
                            catch (InterruptedException e)
                            {
                                Thread.currentThread().interrupt();
                            }
                        }
                    }).start();
                }
                else
                {
                    sipCallEnded();
                }
            }
        }
    }

    class CallPeerListener
        extends CallPeerAdapter
    {
        CallPeer thePeer;

        CallPeerListener(Call call)
        {
            thePeer = call.getCallPeers().next();
            thePeer.addCallPeerListener(this);
        }

        @Override
        public void peerStateChanged(final CallPeerChangeEvent evt)
        {
            CallPeerState callPeerState = (CallPeerState)evt.getNewValue();
            String stateString = callPeerState.getStateString();

            logger.info(callResource + " SIP peer state: " + stateString);

            if (jvbConference != null)
                jvbConference.setPresenceStatus(stateString);

            if (CallPeerState.BUSY.equals(callPeerState))
            {
                // Hangup the call with 5 sec delay, so that we can see BUSY
                // status in jitsi-meet
                new Thread(new Runnable()
                {
                    @Override
                    public void run()
                    {
                        try
                        {
                            Thread.sleep(5000);
                        }
                        catch (InterruptedException e)
                        {
                            throw new RuntimeException(e);
                        }
                        CallManager.hangupCall(
                                evt.getSourceCallPeer().getCall());
                    }
                }).start();
            }
        }

        public void unregister()
        {
            thePeer.removeCallPeerListener(this);
        }
    }

    /**
     * FIXME: to be removed
     */
    class WaitForJvbRoomNameThread
        extends Thread
    {
        private boolean cancel = false;

        @Override
        public void run()
        {
            synchronized (waitLock)
            {
                try
                {
                    waitLock.wait(1000);

                    if (cancel)
                    {
                        logger.info("Wait thread cancelled");
                        return;
                    }

                    if (getJvbRoomName() == null
                        && !CallState.CALL_ENDED.equals(call.getCallState()))
                    {
                        String defaultRoom
                            = JigasiBundleActivator
                            .getConfigurationService()
                            .getString(
                                SipGateway.P_NAME_DEFAULT_JVB_ROOM);

                        if (defaultRoom != null)
                        {
                            logger.info(
                                "Using default JVB room name property "
                                    + defaultRoom);

                            joinJvbConference(defaultRoom, null);
                        }
                        else
                        {
                            logger.info(
                                "No JVB room name provided in INVITE header");

                            hangUp(
                            OperationSetBasicTelephony.HANGUP_REASON_BUSY_HERE,
                            "No JVB room name provided");
                        }
                    }
                }
                catch (InterruptedException e)
                {
                    Thread.currentThread().interrupt();
                }
                finally
                {
                    jitsiMeetTools.removeRequestListener(GatewaySession.this);
                }
            }
        }

        public void cancel()
        {
            if (Thread.currentThread() == waitThread)
            {
                waitThread = null;
                return;
            }

            synchronized (waitLock)
            {
                cancel = true;
                waitLock.notifyAll();
            }
            try
            {
                waitThread.join();
                waitThread = null;
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
            }
        }
    }
}
