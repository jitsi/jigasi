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
