package org.jitsi.jigasi.osgi;

import java.util.*;
import net.java.sip.communicator.service.hid.*;
import org.osgi.framework.*;

public class EmptyHidServiceActivator
    implements BundleActivator
{
    @Override
    public void start(BundleContext bundleContext) throws Exception
    {
        bundleContext.registerService(HIDService.class, new HIDService()
        {
            @Override
            public void keyPress(int keycode)
            {
            }

            @Override
            public void keyRelease(int keycode)
            {
            }

            @Override
            public void keyPress(char key)
            {
            }

            @Override
            public void keyRelease(char key)
            {
            }

            @Override
            public void mousePress(int btns)
            {
            }

            @Override
            public void mouseRelease(int btns)
            {
            }

            @Override
            public void mouseMove(int x, int y)
            {
            }

            @Override
            public void mouseWheel(int rotation)
            {
            }
        }, new Hashtable<>());
    }

    @Override
    public void stop(BundleContext bundleContext) throws Exception
    {
    }
}