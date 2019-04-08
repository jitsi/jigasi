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
import org.jitsi.utils.logging.*;
import org.jitsi.utils.*;

/**
 * No UI just returns default credentials.
 *
 * @author Pawel Domas
 * @author George Politis
 */
public class ServerSecurityAuthority
    implements SecurityAuthority
{
    /**
     * The logger.
     */
    private final static Logger logger
        = Logger.getLogger(ServerSecurityAuthority.class);

    /**
     * The password to return.
     */
    private final String password;

    private final ProtocolProviderService provider;

    public ServerSecurityAuthority(
        ProtocolProviderService provider, String password)
    {
        this.password = password;
        this.provider = provider;
    }

    @Override
    public UserCredentials obtainCredentials(String realm,
                                             UserCredentials defaultValues,
                                             int reasonCode)
    {
        if (reasonCode == WRONG_PASSWORD
            || reasonCode == WRONG_USERNAME)
        {
            logger.error(
                "Wrong username or password for provider:" + this.provider);
            return null;
        }

        if (!StringUtils.isNullOrEmpty(password))
        {
            defaultValues.setPassword(password.toCharArray());
        }

        return defaultValues;
    }

    @Override
    public UserCredentials obtainCredentials(String realm,
                                             UserCredentials defaultValues)
    {
        return defaultValues;
    }

    @Override
    public void setUserNameEditable(boolean isUserNameEditable)
    {

    }

    @Override
    public boolean isUserNameEditable()
    {
        return false;
    }
}
