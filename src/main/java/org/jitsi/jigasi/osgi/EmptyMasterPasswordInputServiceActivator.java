package org.jitsi.jigasi.osgi;

import net.java.sip.communicator.service.credentialsstorage.*;
import org.osgi.framework.*;

public class EmptyMasterPasswordInputServiceActivator
    implements BundleActivator
{
    @Override
    public void start(BundleContext context) throws Exception
    {
        context.registerService(MasterPasswordInputService.class,
            prevSuccess -> null, null);
    }

    @Override
    public void stop(BundleContext context) throws Exception
    {

    }
}
