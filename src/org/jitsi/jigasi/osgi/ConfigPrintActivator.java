/*
 * Jitsi Videobridge, OpenSource video conferencing.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.jigasi.osgi;

import net.java.sip.communicator.util.*;
import org.jitsi.service.configuration.*;
import org.jitsi.util.Logger;
import org.osgi.framework.*;

import java.util.regex.*;

/**
 * FIXME: just add logging option to {@link ConfigurationService} impl directly.
 *
 * Bundle that prints configuration properties on startup.
 *
 * @author Pawel Domas
 */
public class ConfigPrintActivator
    implements BundleActivator
{
    /**
     * The <tt>Logger</tt> used by the <tt>OSGiBundleActivator</tt> class and
     * its instances to print debug information.
     */
    private static final Logger logger
        = Logger.getLogger(ConfigPrintActivator.class);

    /**
     * Logs the properties of the <tt>ConfigurationService</tt> for the purposes
     * of debugging.
     *
     * @param bundleContext
     */
    private void logConfigurationServiceProperties(BundleContext bundleContext)
    {
        if (!logger.isInfoEnabled())
            return;

        boolean interrupted = false;

        try
        {
            if (bundleContext != null)
            {
                ConfigurationService cfg
                    = ServiceUtils.getService(
                        bundleContext,
                        ConfigurationService.class);

                if (cfg != null)
                {
                    /*
                     * Do not print the values of properties with names which
                     * mention the word password.
                     */
                    Pattern exclusion
                        = Pattern.compile(
                        "passw(or)?d",
                        Pattern.CASE_INSENSITIVE);

                    for (String p : cfg.getAllPropertyNames())
                    {
                        Object v = cfg.getProperty(p);

                        if (v != null)
                        {
                            if (exclusion.matcher(p).find())
                                v = "**********";
                            logger.info(p + "=" + v);
                        }
                    }
                }
            }
        }
        catch (Throwable t)
        {
            logger.error(t, t);

            if (t instanceof InterruptedException)
                interrupted = true;
            else if (t instanceof ThreadDeath)
                throw (ThreadDeath) t;
        }
        finally
        {
            if (interrupted)
                Thread.currentThread().interrupt();
        }
    }

    @Override
    public void start(BundleContext bundleContext)
        throws Exception
    {
        logConfigurationServiceProperties(bundleContext);
    }

    @Override
    public void stop(BundleContext bundleContext)
        throws Exception
    {

    }
}
