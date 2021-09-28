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
import org.jitsi.jigasi.util.*;
import org.jitsi.xmpp.extensions.jitsimeet.*;
import org.jivesoftware.smack.*;
import org.jivesoftware.smack.iqrequest.*;
import org.jivesoftware.smack.packet.*;
import org.jxmpp.jid.*;

/**
 * Handles all the mute/unmute/startMuted logic.
 */
public class AudioModeration
{
    /**
     * The name of XMPP feature which states this Jigasi SIP Gateway can be
     * muted.
     */
    public static final String MUTED_FEATURE_NAME = "http://jitsi.org/protocol/audio-mute";

    /**
     * The mute IQ handler if enabled.
     */
    private MuteIqHandler muteIqHandler = null;

    /**
     * {@link AbstractGatewaySession} that is used in the <tt>JvbConference</tt> instance.
     */
    private final AbstractGatewaySession gatewaySession;

    /**
     * The <tt>JvbConference</tt> that handles current JVB conference.
     */
    private final JvbConference jvbConference;

    public AudioModeration(JvbConference jvbConference, AbstractGatewaySession gatewaySession)
    {
        this.gatewaySession = gatewaySession;
        this.jvbConference = jvbConference;
    }

    /**
     * Adds the features supported by jigasi for audio mute if enabled
     * @param meetTools the <tt>OperationSetJitsiMeetTools</tt> instance.
     * @return Returns the features extension element that can be added to presence.
     */
    static ExtensionElement addSupportedFeatures(OperationSetJitsiMeetTools meetTools)
    {
        if (JigasiBundleActivator.isSipStartMutedEnabled())
        {
            meetTools.addSupportedFeature(MUTED_FEATURE_NAME);
            return Util.createFeature(MUTED_FEATURE_NAME);
        }

        return null;
    }

    /**
     * Cleans listeners/handlers used by audio moderation.
     */
    public void clean()
    {
        XMPPConnection connection = jvbConference.getConnection();

        if (muteIqHandler != null)
        {
            // we need to remove it from the connection, or we break some Smack
            // weak references map where the key is connection and the value
            // holds a connection and we leak connection/conferences.
            if (connection != null)
            {
                connection.unregisterIQRequestHandler(muteIqHandler);
            }
        }

    }

    /**
     * Adds the iq handler that will handle muting us from the xmpp side.
     * We add it before inviting jicofo and before joining the room to not miss any message.
     */
    void notifyWillJoinJvbRoom()
    {
        if (JigasiBundleActivator.isSipStartMutedEnabled())
        {
            if (muteIqHandler == null)
            {
                muteIqHandler = new MuteIqHandler(this.gatewaySession);
            }

            jvbConference.getConnection().registerIQRequestHandler(muteIqHandler);
        }
    }

    /**
     * Handles mute requests received by jicofo if enabled.
     */
    private class MuteIqHandler
        extends AbstractIqRequestHandler
    {
        /**
         * {@link AbstractGatewaySession} that is used in the <tt>JvbConference</tt> instance.
         */
        private final AbstractGatewaySession gatewaySession;

        public MuteIqHandler(AbstractGatewaySession gatewaySession)
        {
            super(
                MuteIq.ELEMENT_NAME,
                MuteIq.NAMESPACE,
                IQ.Type.set,
                Mode.sync);

            this.gatewaySession = gatewaySession;
        }

        @Override
        public IQ handleIQRequest(IQ iqRequest)
        {
            return handleMuteIq((MuteIq) iqRequest);
        }

        /**
         * Handles the incoming mute request only if it is from the focus.
         * @param muteIq the incoming iq.
         * @return the result iq.
         */
        private IQ handleMuteIq(MuteIq muteIq)
        {
            Boolean doMute = muteIq.getMute();
            Jid from = muteIq.getFrom();

            if (doMute == null || !from.getResourceOrEmpty().equals(gatewaySession.getFocusResourceAddr()))
            {
                return IQ.createErrorResponse(muteIq, XMPPError.getBuilder(
                    XMPPError.Condition.item_not_found));
            }

            if (doMute)
            {
                gatewaySession.mute();
            }

            return IQ.createResultIQ(muteIq);
        }
    }
}
