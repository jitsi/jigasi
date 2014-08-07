/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.osgi.framework.startlevel;

import org.jitsi.impl.osgi.framework.*;
import org.osgi.framework.startlevel.*;

/**
 *
 * @author Lyubomir Marinov
 */
public class BundleStartLevelImpl
    implements BundleStartLevel
{
    private final BundleImpl bundle;

    private int startLevel = 0;

    public BundleStartLevelImpl(BundleImpl bundle)
    {
        this.bundle = bundle;
    }

    public BundleImpl getBundle()
    {
        return bundle;
    }

    public int getStartLevel()
    {
        int startLevel = this.startLevel;

        if (startLevel == 0)
        {
            FrameworkStartLevel frameworkStartLevel
                = getBundle().getFramework().adapt(FrameworkStartLevel.class);

            if (frameworkStartLevel == null)
                startLevel = 1;
            else
                startLevel = frameworkStartLevel.getInitialBundleStartLevel();
        }
        return startLevel;
    }

    public boolean isActivationPolicyUsed()
    {
        // TODO Auto-generated method stub
        return false;
    }

    public boolean isPersistentlyStarted()
    {
        // TODO Auto-generated method stub
        return false;
    }

    public void setStartLevel(int startLevel)
    {
        if ((startLevel <= 0) || (getBundle().getBundleId() == 0))
            throw new IllegalArgumentException("startLevel");

        this.startLevel = startLevel;
    }
}
