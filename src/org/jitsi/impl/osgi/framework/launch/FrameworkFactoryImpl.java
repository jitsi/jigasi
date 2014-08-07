/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.osgi.framework.launch;

import org.osgi.framework.launch.*;

import java.util.*;

/**
 *
 * @author Lyubomir Marinov
 */
public class FrameworkFactoryImpl
    implements FrameworkFactory
{
    public Framework newFramework(Map<String, String> configuration)
    {
        return new FrameworkImpl(configuration);
    }
}
