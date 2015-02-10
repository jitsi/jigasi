/*
 * Jitsi Videobridge, OpenSource video conferencing.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.jigasi;

import net.java.sip.communicator.service.protocol.*;
import org.jitsi.util.*;

/**
 * No UI just returns default credentials.
 *
 * @author Pawel Domas
 * @author George Politis
 */
public class ServerSecurityAuthority
    implements SecurityAuthority
{
    private final String password;

    public ServerSecurityAuthority(String password)
    {
        this.password = password;
    }

    @Override
    public UserCredentials obtainCredentials(String realm,
                                             UserCredentials defaultValues,
                                             int reasonCode)
    {
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
