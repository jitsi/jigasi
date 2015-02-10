/*
 * Jitsi Videobridge, OpenSource video conferencing.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.jigasi;

import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.util.*;

/**
 * Thread does the job of registering given <tt>ProtocolProviderService</tt>.
 *
 * @author Pawel Domas
 * @author George Politis
 */
public class RegisterThread
    extends Thread
{
    /**
     * The logger.
     */
    private final static Logger logger
        = Logger.getLogger(RegisterThread.class);

    private final ProtocolProviderService pps;

    private final String password;

    RegisterThread(ProtocolProviderService pps)
    {
        this(pps, null);
    }

    RegisterThread(ProtocolProviderService pps, String password)
    {
        this.pps = pps;
        this.password = password;
    }

    @Override
    public void run()
    {
        try
        {
            pps.register(new ServerSecurityAuthority(password));
        }
        catch (OperationFailedException e)
        {
            logger.error(e, e);
        }
    }
}
