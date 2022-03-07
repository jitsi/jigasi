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
package org.jitsi.jigasi.xmpp;

import net.java.sip.communicator.util.*;
import org.jitsi.jigasi.*;
import org.jitsi.xmpp.extensions.rayo.*;
import org.jitsi.xmpp.extensions.rayo.RayoIqProvider.*;
import org.jitsi.service.configuration.*;
import org.jivesoftware.smack.packet.*;
import org.jxmpp.jid.*;
import org.jxmpp.jid.impl.*;
import org.jxmpp.stringprep.*;

/**
 *  Implementation of call control that is capable of utilizing Rayo
 *  XMPP protocol for the purpose of SIP sipGateway calls management.
 *
 * @author Damian Minkov
 * @author Nik Vaessen
 */
public class CallControl
{
    /**
     * The logger.
     */
    private final static Logger logger = Logger.getLogger(CallControl.class);

    /**
     * Name of 'header' attribute that hold JVB room name.
     */
    public static final String ROOM_NAME_HEADER = "JvbRoomName";

    /**
     * Optional header for specifying password required to enter MUC room.
     */
    public static final String ROOM_PASSWORD_HEADER = "JvbRoomPassword";

    /**
     * JID allowed to make outgoing SIP calls.
     */
    public static final String ALLOWED_JID_P_NAME
        = "org.jitsi.jigasi.ALLOWED_JID";

    /**
     * If a dial IQ has this destination we should invite a
     * TranscriptionGatewaySession
     */
    public static final String TRANSCRIPTION_DIAL_IQ_DESTINATION
        = "jitsi_meet_transcribe";

    /**
     * The {@link SipGateway} service which manages SipGateway sessions.
     */
    private SipGateway sipGateway;

    /**
     * The {@link TranscriptionGateway} service which manges
     * TranscriptionGatewaySession's
     */
    private TranscriptionGateway transcriptionGateway;

    /**
     * The only JID that will be allowed to create outgoing SIP calls. If not
     * set then anybody is allowed to do so.
     */
    private Jid allowedJid;

    /**
     * Constructs new call control instance with a SipGateway
     *
     * @param sipGateway the SipGateway instance
     * @param config the config service instance
     */
    public CallControl(SipGateway sipGateway, ConfigurationService config)
    {
        this(config);
        this.sipGateway = sipGateway;
    }

    /**
     * Constructs new call control instance with a SipGateway
     *
     * @param transcriptionGateway the TranscriptionGateway instance
     * @param config the config service instance
     */
    public CallControl(TranscriptionGateway transcriptionGateway,
                       ConfigurationService config)
    {
        this(config);
        this.transcriptionGateway = transcriptionGateway;
    }

    /**
     * Constructs new call control instance.
     * @param config the config service instance.
     */
    public CallControl(ConfigurationService config)
    {
        Boolean always_trust_mode = config.getBoolean(
            "net.java.sip.communicator.service.gui.ALWAYS_TRUST_MODE_ENABLED",
            false);
        if (always_trust_mode)
        {
            // Always trust mode - prevent failures because there's no GUI
            // to ask the user, but do we always want to trust so, in this
            // mode, the service is vulnerable to Man-In-The-Middle attacks.
            logger.warn(
                "Always trust in remote TLS certificates mode is enabled");
        }

        String allowedJidString = config.getString(ALLOWED_JID_P_NAME, null);
        if (allowedJidString != null)
        {
            try
            {
                this.allowedJid = JidCreate.from(allowedJidString);
            }
            catch (XmppStringprepException e)
            {
                logger.error("Invalid call control JID", e);
            }
        }

        if (allowedJid != null)
        {
            logger.info("JID allowed to make outgoing calls: " + allowedJid);
        }
    }

    /**
     * Handles an {@link IQ} stanza of type {@link IQ.Type#set} which
     * represents a request.
     *
     * @param iq the {@link IQ} of type {@link IQ.Type#set} which represents
     * the request to handle
     * @return a {@link RefIq} which represents the response to the request.
     */
    public RefIq handleDialIq(DialIq iq, CallContext ctx,
        AbstractGatewaySession[] createdSession)
        throws CallControlAuthorizationException
    {
        checkAuthorized(iq);

        String from = iq.getSource();
        String to = iq.getDestination();

        ctx.setDestination(to);

        String roomName = null;

        // extract the headers and pass them to context
        for (ExtensionElement ext: iq.getExtensions())
        {
            if (ext instanceof HeaderExtension)
            {
                HeaderExtension header = (HeaderExtension) ext;
                String name = header.getName();
                String value = header.getValue();

                if (ROOM_NAME_HEADER.equals(name))
                {
                    roomName = value;
                    try
                    {
                        ctx.setRoomName(roomName);
                    }
                    catch(XmppStringprepException e)
                    {
                        throw new RuntimeException("Malformed JvbRoomName header found " + roomName, e);
                    }
                }
                else if (ROOM_PASSWORD_HEADER.equals(name))
                {
                    ctx.setRoomPassword(value);
                }
                else
                {
                    ctx.addExtraHeader(name, value);
                }
            }
        }

        if (roomName == null)
            throw new RuntimeException("No JvbRoomName header found");

        logger.info(ctx +
            " Got dial request " + from + " -> " + to + " room: " + roomName);

        AbstractGatewaySession session = null;
        if (TRANSCRIPTION_DIAL_IQ_DESTINATION.equals(to))
        {
            if (transcriptionGateway == null)
            {
                logger.error(ctx
                    + " Cannot accept dial request " + to + " because"
                    + " the TranscriptionGateway is disabled");
                return RefIq.createResult(iq,
                    XMPPError.Condition.not_acceptable.toString());
            }

            session = transcriptionGateway.createOutgoingCall(ctx);
        }
        else
        {
            if (sipGateway == null)
            {
                logger.error(ctx
                    + " Cannot accept dial request " + to + " because"
                    + " the SipGateway is disabled");
                return RefIq.createResult(iq,
                    XMPPError.Condition.not_acceptable.toString());
            }

            session = sipGateway.createOutgoingCall(ctx);
        }

        if (createdSession != null && createdSession.length == 1)
        {
            createdSession[0] = session;
        }

        return RefIq.createResult(iq, "xmpp:" + ctx.getCallResource());
    }

    /**
     * Handles an {@link IQ} stanza of type {@link IQ.Type#set} which
     * represents a request.
     *
     * @param iq the {@link IQ} of type {@link IQ.Type#set} which represents
     * the request to handle
     * @return an {@link IQ} stanza which represents the response to the
     * specified request.
     */
    public IQ handleHangUp(HangUp iq)
        throws CallControlAuthorizationException
    {
        checkAuthorized(iq);

        // FIXME: 23/07/17 Transcription sessions should also be able
        // to be hangup ?

        Jid callResource = iq.getTo();
        SipGatewaySession session = sipGateway.getSession(callResource);
        if (session == null)
            throw new RuntimeException(
                "No sipGateway for call: " + callResource);

        session.hangUp();
        return IQ.createResultIQ(iq);
    }

    private void checkAuthorized(IQ iq)
        throws CallControlAuthorizationException
    {
        Jid fromBareJid = iq.getFrom().asBareJid();
        if (allowedJid != null && !allowedJid.equals(fromBareJid))
        {
            throw new CallControlAuthorizationException(iq);
        }
        else if (allowedJid == null)
        {
            logger.warn("Requests are not secured by JID filter!");
        }
    }

    /**
     * Get the SipGateway this CallControl uses to create SipGatewaySession's
     *
     * @return the SipGateway
     */
    public SipGateway getSipGateway()
    {
        return sipGateway;
    }

    /**
     * Set the SipGateway this CallControl uses to create SipGatewaySession's
     *
     * @param sipGateway the SipGateway to set
     */
    public void setSipGateway(SipGateway sipGateway)
    {
        this.sipGateway = sipGateway;
    }

    /**
     * Get the TranscriptionGateway this CallControl uses to create
     * TranscriptionGatewaySession's
     *
     * @return the TranscriptionGateway
     */
    public TranscriptionGateway getTranscriptionGateway()
    {
        return transcriptionGateway;
    }

    /**
     * Set the TranscriptionGateway this CallControl uses to create
     * TranscriptionGatewaySession's
     *
     * @param transcriptionGw the TranscriptionGateway
     */
    public void setTranscriptionGateway(TranscriptionGateway transcriptionGw)
    {
        this.transcriptionGateway = transcriptionGw;
    }

    /**
     * Finds {@link AbstractGatewaySession} for given <tt>callResource</tt> if
     * one is currently active.
     * Searches for the sessions in SipGateway and TranscriptionGateway.
     *
     * @param callResource the call resource/URI of the
     *                     <tt>AbstractGatewaySession</tt> to be found.
     *
     * @return {@link AbstractGatewaySession} for given <tt>callResource</tt> if
     * there is one currently active or <tt>null</tt> otherwise.
     */
    public AbstractGatewaySession getSession(Jid callResource)
    {
        AbstractGatewaySession result
            = this.sipGateway.getSession(callResource);
        if (result == null)
        {
            result
                = this.transcriptionGateway.getSession(callResource);
        }

        return result;
    }

    /**
     * Adds a listener that will be notified of changes in any gateway status.
     *
     * @param listener a gateway status listener.
     */
    public void addGatewayListener(GatewayListener listener)
    {
        this.sipGateway.addGatewayListener(listener);
        this.transcriptionGateway.addGatewayListener(listener);
    }

    /**
     * Removes a listener that was being notified of changes in the status of
     * any of the AbstractGateway.
     *
     * @param listener a gateway status listener.
     */
    public void removeGatewayListener(GatewayListener listener)
    {
        this.sipGateway.removeGatewayListener(listener);
        this.transcriptionGateway.removeGatewayListener(listener);
    }


}
