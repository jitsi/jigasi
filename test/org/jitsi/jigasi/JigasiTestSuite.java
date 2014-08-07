/*
 * Jitsi Videobridge, OpenSource video conferencing.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.jigasi;

import org.jitsi.jigasi.xmpp.rayo.*;
import org.junit.runner.*;
import org.junit.runners.*;

/**
 * @author Pawel Domas
 */
@RunWith(Suite.class)
@Suite.SuiteClasses(
    {
        CallsHandlingTest.class,
        DialIqProviderTest.class,
        RefIqProviderTest.class
    })
public class JigasiTestSuite
{
}
