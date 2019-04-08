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
import net.java.sip.communicator.service.protocol.mock.*;
import net.java.sip.communicator.util.*;
import org.jitsi.jigasi.xmpp.*;
import org.jitsi.service.configuration.*;
import org.jitsi.xmpp.extensions.rayo.*;
import org.jitsi.xmpp.util.*;
import org.jivesoftware.smack.packet.*;
import org.junit.*;
import org.junit.Test;
import org.junit.runner.*;
import org.junit.runners.*;
import org.jxmpp.jid.*;
import org.jxmpp.jid.impl.*;
import org.jxmpp.stringprep.*;
import org.osgi.framework.*;

import java.lang.*;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.Assert.*;

/**
 * Test various call situations(not all :P).
 *
 * @author Pawel Domas
 * @author Nik Vaessen
 */
@RunWith(JUnit4.class)
public class CallsHandlingTest
{
    private static OSGiHandler osgi;

    private MockProtocolProvider sipProvider;

    private static int roomNameCounter = 1;

    private String roomName;

    private MockJvbConferenceFocus focus;

    /**
     * Initializes OSGi and the videobridge.
     */
    @BeforeClass
    public static void setUpClass()
        throws InterruptedException
    {
        osgi = new OSGiHandler();

        osgi.init();

        // give some time to osgi
        Object o = new Object();
        synchronized (o)
        {
            o.wait(1500);
        }
    }

    @Before
    public void setUp()
    {
        sipProvider = osgi.getSipProvider();

        this.roomName = getTestRoomName();

        this.focus = new MockJvbConferenceFocus(roomName);
    }

    @After
    public void tearDown()
        throws InterruptedException, TimeoutException
    {
        focus.tearDown();

        CallManager.restartPool();

        BundleContext ctx = JigasiBundleActivator.osgiContext;

        // Check if XMPP accounts have been unloaded
        Collection<ServiceReference<ProtocolProviderService>> refs
            = ServiceUtils.getServiceReferences(
                ctx, ProtocolProviderService.class);

        for (ServiceReference<?> ref : refs)
        {
            ProtocolProviderService protoService
                = (ProtocolProviderService) ctx.getService(ref);

            if (ProtocolNames.JABBER.equals(
                    protoService.getProtocolName()))
            {
                throw new RuntimeException(
                    protoService + " is still registered");
            }
        }
    }

    @AfterClass
    public static void tearDownClass()
    {
        osgi.shutdown();
    }

    private String getTestRoomName()
    {
        return "test" + roomNameCounter++;
    }

    /**
     * Test covers the case when SIP gateway receives incoming call with
     * JVB conference room name in INVITE extra header.
     */
    //@Test// - is called from test mutiple time
    public void testIncomingSipCall()
        throws Exception
    {
        // Once the chat room is joined the focus sends
        // session-initiate request to new participant.
        focus.setup();

        MockCall sipCall
            = sipProvider.getTelephony()
                    .mockIncomingGatewayCall("calee", roomName);

        CallStateListener callStateWatch = new CallStateListener();

        // SipGateway ought to accept incoming sip call
        // once JVB conference is joined.
        callStateWatch.waitForState(sipCall, CallState.CALL_IN_PROGRESS, 1000);

        SipGatewaySession session
            = osgi.getSipGateway().getActiveSessions().get(0);

        Call jvbCall = session.getJvbCall();
        ChatRoom jvbConfRoom = session.getJvbChatRoom();

        assertNotNull(jvbCall);
        assertNotNull(jvbCall);

        // Now tear down, SIP calee ends the call
        // then XMPP cal should be terminated and MUC room left
        CallManager.hangupCall(sipCall);

        callStateWatch.waitForState(
            session.getJvbCall(), CallState.CALL_ENDED, 1000);
        assertEquals(false, jvbConfRoom.isJoined());
    }

    //@Test //is called from test multiple time
    public void testOutgoingSipCall()
        throws InterruptedException, OperationFailedException,
               OperationNotSupportedException, XmppStringprepException
    {
        String destination = "sip-destination";

        SipGateway sipGw = osgi.getSipGateway();

        focus.setup();

        CallStateListener callStateWatch = new CallStateListener();

        OperationSetBasicTelephony sipTele
            = osgi.getSipProvider()
                    .getOperationSet(OperationSetBasicTelephony.class);

        // Make remote SIP peer accept the call, but wait for SIP call instance
        // first
        OutCallListener outCallWatch = new OutCallListener();

        outCallWatch.bind(sipTele);

        CallContext ctx = new CallContext(osgi.getSipProvider());
        ctx.setDestination(destination);
        ctx.setRoomName(roomName);
        ctx.setCustomCallResource(
            JidCreate.from("callResourceUri" + roomName + "@conference.net"));

        SipGatewaySession session = sipGw.createOutgoingCall(ctx);
        assertNotNull(session);

        Call sipCall = outCallWatch.getOutgoingCall(1000);

        assertNotNull(sipCall);

        // Remote SIP peer accepts
        CallManager.acceptCall(sipCall);

        callStateWatch.waitForState(sipCall, CallState.CALL_IN_PROGRESS, 1000);

        // Check we're in the room and all calls are up
        ChatRoom chatRoom = session.getJvbChatRoom();
        Call jvbCall = session.getJvbCall();

        assertEquals(true, chatRoom.isJoined());
        assertEquals(CallState.CALL_IN_PROGRESS, jvbCall.getCallState());

        // Now tear down, SIP calee ends the call
        // then XMPP cal should be terminated and MUC room left

        CallManager.hangupCall(sipCall);

        callStateWatch.waitForState(sipCall, CallState.CALL_ENDED, 1000);
        callStateWatch.waitForState(jvbCall, CallState.CALL_ENDED, 1000);
        assertEquals(false, chatRoom.isJoined());
    }

    /**
     * Runs in sequence {@link #testIncomingSipCall()} to check
     * reinitialization.
     *
     * @throws Exception
     */
    @Test
    public void testMultipleTime()
        throws Exception
    {
        testIncomingSipCall();
        tearDown();

        setUp();
        testOutgoingSipCall();
        tearDown();

        setUp();
        testIncomingSipCall();
        tearDown();

        setUp();
        testIncomingSipCall();
        tearDown();

        setUp();
        testOutgoingSipCall();
        tearDown();

        setUp();
        testOutgoingSipCall();
    }

    @Test
    public void testFocusLeftTheRoom()
        throws OperationFailedException,
               OperationNotSupportedException, InterruptedException,
               TimeoutException
    {
        focus.setup();

        // Focus will leave the room after inviting us to the conference
        focus.setLeaveRoomAfterInvite(true);

        CallStateListener callStateWatch = new CallStateListener();

        // Create incoming call
        MockCall sipCall
            = sipProvider.getTelephony()
                    .mockIncomingGatewayCall("calee", roomName);

        callStateWatch.waitForState(sipCall, CallState.CALL_ENDED, 2000);

        // Now we expect SIP call to be terminated
        assertEquals(CallState.CALL_ENDED, focus.getCall().getCallState());
        assertNull(focus.getChatRoom());
    }

    @Test
    public void testXmppComponent()
        throws Exception
    {
        String subdomain = "call";
        String serverName = "conference.net";
        Jid jigasiJid = JidCreate.from(subdomain + "." + serverName);

        CallControlComponent component
            = new CallControlComponent(
                    serverName, 1234, serverName, subdomain, "secret");

        component.postComponentStart();

        assertEquals(serverName, component.getDomain());

        Jid from = JidCreate.from("from@example.org");
        Jid to = JidCreate.from("sipAddress@example.com");

        focus.setup();

        OutCallListener outCallWatch = new OutCallListener();
        outCallWatch.bind(sipProvider.getTelephony());

        RayoIqProvider.DialIq dialIq
            = RayoIqProvider.DialIq.create(to.toString(), from.toString());

        dialIq.setFrom(from);
        dialIq.setTo(jigasiJid);

        dialIq.setHeader(
            CallControl.ROOM_NAME_HEADER,
            focus.getRoomName());

        org.xmpp.packet.IQ result
            = component.handleIQ(IQUtils.convert(dialIq));

        IQ iq = IQUtils.convert(result);

        assertTrue(iq instanceof RayoIqProvider.RefIq);

        RayoIqProvider.RefIq callRef = (RayoIqProvider.RefIq) iq;

        String callUri = callRef.getUri();
        assertEquals("xmpp:", callUri.substring(0, 5));
        assertTrue(callUri.contains("@" + jigasiJid));

        // Wait for call to be created
        Call sipCall = outCallWatch.getOutgoingCall(1000);

        assertNotNull(sipCall);

        // Remote SIP peer accepts
        CallManager.acceptCall(sipCall);

        CallStateListener callStateWatch = new CallStateListener();

        callStateWatch.waitForState(sipCall, CallState.CALL_IN_PROGRESS, 1000);

        Jid callResource = JidCreate.from(callUri.substring(5)); //remove xmpp:
        SipGatewaySession session = osgi.getSipGateway().getSession(callResource);
        Call xmppCall = session.getJvbCall();

        // We joined JVB conference call
        callStateWatch.waitForState(xmppCall, CallState.CALL_IN_PROGRESS, 1000);

        ChatRoom conferenceChatRoom = session.getJvbChatRoom();

        // Now tear down
        RayoIqProvider.HangUp hangUp
            = RayoIqProvider.HangUp.create(
                    from, callResource);

        // FIXME: validate result
        component.handleIQ(IQUtils.convert(hangUp));

        callStateWatch.waitForState(xmppCall, CallState.CALL_ENDED, 1000);
        callStateWatch.waitForState(sipCall, CallState.CALL_ENDED, 1000);
        assertFalse(conferenceChatRoom.isJoined());
    }

    /**
     * Tests default JVB room name configuration property.
     * @throws Exception
     */
    @Test
    public void testDefaultJVbRoomProperty()
        throws Exception
    {
        // Once the chat room is joined the focus sends
        // session-initiate request to new participant.
        focus.setup();

        CallStateListener callStateWatch = new CallStateListener();

        ConfigurationService config
            = JigasiBundleActivator.getConfigurationService();

        config.setProperty(
            SipGateway.P_NAME_DEFAULT_JVB_ROOM, roomName);

        MockCall sipCall
            = sipProvider.getTelephony()
                    .mockIncomingGatewayCall("calee", null);

        // SipGateway ought to accept incoming sip call
        // once JVB conference is joined.
        callStateWatch.waitForState(sipCall, CallState.CALL_IN_PROGRESS, 2000);

        // Now tear down, SIP calee ends the call
        // then XMPP cal should be terminated and MUC room left
        SipGatewaySession session
            = osgi.getSipGateway().getActiveSessions().get(0);
        assertNotNull(session);

        Call xmppCall = session.getJvbCall();
        assertNotNull(xmppCall);

        ChatRoom jvbRoom = session.getJvbChatRoom();
        assertNotNull(jvbRoom);

        CallManager.hangupCall(sipCall);

        callStateWatch.waitForState(xmppCall, CallState.CALL_ENDED, 1000);
        callStateWatch.waitForState(sipCall, CallState.CALL_ENDED, 1000);
        assertEquals(false, jvbRoom.isJoined());
    }

    @Test
    public void testSimultaneousCalls()
        throws Exception
    {
        // Once the chat room is joined the focus sends
        // session-initiate request to new participant.
        focus.setup();

        MockBasicTeleOpSet sipTele = sipProvider.getTelephony();

        // After incoming SIP call is received gateway will join JVB rooms and
        // start calls
        MockCall sipCall1 = sipTele.mockIncomingGatewayCall("calee1", roomName);
        MockCall sipCall2 = sipTele.mockIncomingGatewayCall("calee2", roomName);
        MockCall sipCall3 = sipTele.mockIncomingGatewayCall("calee3", roomName);

        CallStateListener callStateWatch = new CallStateListener();

        callStateWatch.waitForState(sipCall1, CallState.CALL_IN_PROGRESS, 1000);
        callStateWatch.waitForState(sipCall2, CallState.CALL_IN_PROGRESS, 1000);
        callStateWatch.waitForState(sipCall3, CallState.CALL_IN_PROGRESS, 1000);

        // Check peers are not on hold
        CallPeerStateListener peerStateWatch = new CallPeerStateListener();
        peerStateWatch.waitForState(sipCall1, 0, CallPeerState.CONNECTED, 1000);
        peerStateWatch.waitForState(sipCall2, 0, CallPeerState.CONNECTED, 1000);
        peerStateWatch.waitForState(sipCall3, 0, CallPeerState.CONNECTED, 1000);

        // We expect to have 3 active sessions
        SipGateway gateway = osgi.getSipGateway();
        List<SipGatewaySession> sessions = gateway.getActiveSessions();

        assertEquals(3, sessions.size());

        // Both are in JVB room
        ChatRoom jvbRoom1 = sessions.get(0).getJvbChatRoom();
        ChatRoom jvbRoom2 = sessions.get(1).getJvbChatRoom();
        ChatRoom jvbRoom3 = sessions.get(2).getJvbChatRoom();

        assertEquals(true, jvbRoom1.isJoined());
        assertEquals(true, jvbRoom2.isJoined());
        assertEquals(true, jvbRoom3.isJoined());

        // After hangup all calls are ended and rooms left
        Call jvbCall1 = sessions.get(0).getJvbCall();
        Call jvbCall2 = sessions.get(1).getJvbCall();
        Call jvbCall3 = sessions.get(2).getJvbCall();

        CallManager.hangupCall(sipCall1);
        CallManager.hangupCall(sipCall2);
        CallManager.hangupCall(sipCall3);

        callStateWatch.waitForState(jvbCall1, CallState.CALL_ENDED, 1000);
        callStateWatch.waitForState(jvbCall2, CallState.CALL_ENDED, 1000);
        callStateWatch.waitForState(jvbCall3, CallState.CALL_ENDED, 1000);

        assertEquals(CallState.CALL_ENDED, sipCall1.getCallState());
        assertEquals(CallState.CALL_ENDED, sipCall2.getCallState());
        assertEquals(CallState.CALL_ENDED, sipCall3.getCallState());

        assertEquals(false, jvbRoom1.isJoined());
        assertEquals(false, jvbRoom2.isJoined());
        assertEquals(false, jvbRoom3.isJoined());
    }

    @Test
    public void testNoFocusInTheRoom()
        throws Exception
    {
        // Set wait for JVB invite timeout
        long jvbInviteTimeout = 200;
        AbstractGateway.setJvbInviteTimeout(jvbInviteTimeout);

        // No setup - SIP peer will join and should leave after timeout
        // of waiting for the invitation
        //focus.setup();

        SipGateway gateway = osgi.getSipGateway();
        GatewaySessions gatewaySessions = new GatewaySessions(gateway);

        // After incoming SIP call is received gateway will join JVB rooms and
        // start calls
        MockCall sipCall1
            = sipProvider.getTelephony()
                    .mockIncomingGatewayCall("calee1", roomName);

        CallStateListener callStateWatch = new CallStateListener();

        // Assert incoming call state
        callStateWatch.waitForState(
            sipCall1, CallState.CALL_INITIALIZATION, 1000);

        // We expect to have 1 active sessions
        List<SipGatewaySession> sessions = gatewaySessions.getSessions(1000);
        assertEquals(1, sessions.size());

        SipGatewaySession session1 = sessions.get(0);

        GatewaySessionAsserts sessionWatch = new GatewaySessionAsserts();
        sessionWatch.assertJvbRoomJoined(session1, 1000);

        // We entered the room where there is no focus
        ChatRoom jvbRoom1 = session1.getJvbChatRoom();
        assertEquals(true, jvbRoom1.isJoined());
        assertEquals(1, jvbRoom1.getMembersCount());

        // There is no call cause the focus did not invited
        Call jvbCall1 = sessions.get(0).getJvbCall();
        assertNull(jvbCall1);

        callStateWatch.waitForState(
            sipCall1, CallState.CALL_ENDED, jvbInviteTimeout + 200);

        assertEquals(false, jvbRoom1.isJoined());

        AbstractGateway.setJvbInviteTimeout(
            AbstractGateway.DEFAULT_JVB_INVITE_TIMEOUT);
    }

}
