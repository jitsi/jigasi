/*
 * Copyright @ 2019 - present, 8x8 Inc
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

/**
 * Keeps constants for the application version.
 *
 * Note that the constants are modified at build time, so changes to this file
 * must be synchronized with the build system.
 *
 * @author Boris Grozev
 */
public class CurrentVersionImpl
{
    /**
     * The major version.
     */
    public static final int VERSION_MAJOR = 1;

    /**
     * The minor version.
     */
    public static final int VERSION_MINOR = 0;

    /**
     * The version prerelease ID of the current application version.
     */
    public static final String PRE_RELEASE_ID = null;

    /**
     * The nightly build ID. This file is auto-updated by build.xml.
     */
    public static final String NIGHTLY_BUILD_ID = "build.SVN";

    static final Version VERSION
            = new VersionImpl(
            "Jigasi",
            VERSION_MAJOR,
            VERSION_MINOR,
            NIGHTLY_BUILD_ID,
            PRE_RELEASE_ID);
}
