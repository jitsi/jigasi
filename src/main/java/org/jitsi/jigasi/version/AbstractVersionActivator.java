/*
 * Copyright @ 2015 - present, 8x8 Inc
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
package org.jitsi.jigasi.version;

import org.jitsi.utils.version.*;
import org.jitsi.utils.version.Version;

import org.jitsi.utils.logging.*;
import org.osgi.framework.*;

import java.util.regex.*;

/**
 * <p>
 * The entry point to the {@code VersionService} implementation. We register the
 * {@code VersionServiceImpl} instance on the OSGi bus.
 * </p>
 * <p>
 * This abstract <tt>BundleActivator</tt> will provide implementation of
 * the <tt>VersionService</tt> once {@link #getCurrentVersion()} method is
 * provided.
 * </p>
 *
 * @author George Politis
 * @author Pawel Domas
 */
public abstract class AbstractVersionActivator
    implements BundleActivator
{
    /**
     * The <tt>Logger</tt> used by this <tt>VersionActivator</tt> instance for
     * logging output.
     */
    private final Logger logger
        = Logger.getLogger(AbstractVersionActivator.class);

    /**
     * The pattern that will parse strings to version object.
     */
    private static final Pattern PARSE_VERSION_STRING_PATTERN
        = Pattern.compile("(\\d+)\\.(\\d+)\\.([\\d\\.]+)");

    /**
     * Implementing class must return a valid {@link Version} object.
     *
     * @return {@link Version} instance which provides the details about
     * current version of the application.
     */
    abstract protected Version getCurrentVersion();

    /**
     * Called when this bundle is started so the Framework can perform the
     * bundle-specific activities necessary to start this bundle.
     *
     * @param context The execution context of the bundle being started.
     * @throws Exception If this method throws an exception, this bundle is
     * marked as stopped and the Framework will remove this bundle's listeners,
     * unregister all services registered by this bundle, and release all
     * services used by this bundle.
     */
    public void start(BundleContext context) throws Exception
    {
        if (logger.isDebugEnabled())
        {
            logger.debug("Started.");
        }

        Version currentVersion = getCurrentVersion();

        VersionServiceImpl versionServiceImpl
            = new VersionServiceImpl(currentVersion);

        context.registerService(
            VersionService.class.getName(),
            versionServiceImpl,
            null);

        logger.info("VersionService registered: "
            + currentVersion.getApplicationName() + " " + currentVersion);
    }

    /**
     * Called when this bundle is stopped so the Framework can perform the
     * bundle-specific activities necessary to stop the bundle.
     *
     * @param context The execution context of the bundle being stopped.
     * @throws Exception If this method throws an exception, the bundle is still
     * marked as stopped, and the Framework will remove the bundle's listeners,
     * unregister all services registered by the bundle, and release all
     * services used by the bundle.
     */
    public void stop(BundleContext context) throws Exception
    {
    }

    /**
     * Implementation of the {@link VersionService}.
     */
    static class VersionServiceImpl
        implements VersionService
    {
        private final Version version;

        private VersionServiceImpl(Version version)
        {
            this.version = version;
        }

        /**
         * Returns a Version instance corresponding to the <tt>version</tt>
         * string.
         *
         * @param versionString a version String that we have obtained by calling a
         * <tt>Version.toString()</tt> method.
         *
         * @return the <tt>Version</tt> object corresponding to the
         * <tt>version</tt> string. Or null if we cannot parse the string.
         */
        @Override
        public Version parseVersionString(String versionString)
        {
            Matcher matcher
                = PARSE_VERSION_STRING_PATTERN.matcher(versionString);

            if (matcher.matches() && matcher.groupCount() == 3)
            {
                return new VersionImpl(
                    version.getApplicationName(),
                    Integer.parseInt(matcher.group(1)),
                    Integer.parseInt(matcher.group(2)),
                    matcher.group(3),
                    version.getPreReleaseID());
            }

            return null;
        }

        /**
         * Returns a <tt>Version</tt> object containing version details of the
         * the application version that we're currently running.
         *
         * @return a <tt>Version</tt> object containing version details of the
         * application version that we're currently running.
         */
        @Override
        public Version getCurrentVersion()
        {
            return version;
        }
    }
}
