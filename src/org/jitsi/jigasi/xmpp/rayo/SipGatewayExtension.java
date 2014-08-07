package org.jitsi.jigasi.xmpp.rayo;

import net.java.sip.communicator.impl.protocol.jabber.extensions.*;

/**
 * Extension added to MUC presence packets to notify about call state updates.
 *
 * @author Pawel Domas
 */
public class SipGatewayExtension
    extends AbstractPacketExtension
{
    /**
     * SIP gateway extension namespace.
     */
    public static final String NAMESPACE = "http://jitsi.org/sipgateway";

    /**
     * SIP gateway XML element name.
     */
    public static final String ELEMENT_NAME = "sipgateway";

    /**
     * The name of the attribute that holds call resource.
     */
    public static final String CALL_RESOURCE_ATTR = "resource";

    /**
     * The name of the attribute that holds call state.
     */
    public static final String CALL_STATE_ATTR = "state";

    /**
     * Connecting to JVB conference room state constant.
     */
    public static final String STATE_CONNECTING_JVB = "Initializing Call";

    /**
     * Ringing state constant.
     */
    public static final String STATE_RINGING = "Ringing...";

    /**
     * Call in progress state constant.
     */
    public static final String STATE_IN_PROGRESS = "In progress";

    /**
     * Error state constant. FIXME: to be removed
     */
    public static final String STATE_ERROR = "error";

    /**
     * Creates new instance of <tt>SipGatewayExtension</tt>.
     */
    public SipGatewayExtension()
    {
        super(NAMESPACE, ELEMENT_NAME);
    }


    public void setResource(String resource)
    {
        setAttribute(CALL_RESOURCE_ATTR, resource);
    }

    public String getResource()
    {
        return getAttributeAsString(CALL_RESOURCE_ATTR);
    }

    public void setState(String state)
    {
        setAttribute(CALL_STATE_ATTR, state);
    }

    public String getState()
    {
        return getAttributeAsString(CALL_STATE_ATTR);
    }

    public void setError(String error)
    {
        // FIXME: remove existting extension
        //getChildExtensionsOfType(ErrorPacketExtension.class)

        ErrorPacketExtension errorExt
            = new ErrorPacketExtension();

        errorExt.setText(error);

        addChildExtension(errorExt);
    }

    //FIXME: errors will end up in jingle anyway
    class ErrorPacketExtension
        extends AbstractPacketExtension
    {

        protected ErrorPacketExtension()
        {
            super(null, "error");
        }
    }
}
