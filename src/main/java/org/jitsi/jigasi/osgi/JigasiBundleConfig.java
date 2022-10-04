/*
 * Jigasi, the JItsi GAteway to SIP.
 *
 * Copyright @ 2018 - present 8x8, Inc.
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
package org.jitsi.jigasi.osgi;

import java.util.*;
import net.java.sip.communicator.impl.protocol.jabber.*;
import org.jitsi.impl.neomedia.*;
import org.jitsi.impl.neomedia.device.*;
import org.jitsi.impl.neomedia.transform.csrc.*;
import org.jitsi.service.configuration.*;

/**
 * Jigasi OSGi bundle config
 *
 * @author Pawel Domas
 */
public class JigasiBundleConfig
{
    private JigasiBundleConfig()
    {
        // prevent instances
    }

    /**
     * Returns a {@code Map} which contains default system properties common to
     * all server components. Currently, we have the following values there:
     * <li>{@link ConfigurationService#PNAME_CONFIGURATION_FILE_IS_READ_ONLY}
     * = true</li>
     * <li>{@link MediaServiceImpl#DISABLE_AUDIO_SUPPORT_PNAME} = true</li>
     * <li>{@link MediaServiceImpl#DISABLE_VIDEO_SUPPORT_PNAME} = true</li>
     *
     * @return a {@code Map} which contains default system properties common to
     * all server components
     */
    private static Map<String, String> getSystemPropertyDefaults()
    {
        // XXX A default System property value specified bellow will eventually
        // be set only if the System property in question does not have a value
        // set yet.

        Map<String, String> defaults = new HashMap<>();
        String true_ = Boolean.toString(true);
        String false_ = Boolean.toString(false);

        // Disable Video
        defaults.put(
            "net.java.sip.communicator.service.media.DISABLE_VIDEO_SUPPORT",
            true_);

        // Audio system should not be disabled
        defaults.put(
            MediaServiceImpl.DISABLE_AUDIO_SUPPORT_PNAME,
            false_);

        defaults.put(
            DeviceConfiguration.PROP_AUDIO_SYSTEM,
            AudioSystem.LOCATOR_PROTOCOL_AUDIOSILENCE);
        defaults.put(
            "org.jitsi.impl.neomedia.device.PortAudioSystem.disabled",
            true_);
        defaults.put(
            "org.jitsi.impl.neomedia.device.PulseAudioSystem.disabled",
            true_);

        // Disables COIN notifications
        defaults.put(
            OperationSetTelephonyConferencingJabberImpl.DISABLE_COIN_PROP_NAME,
            true_);

        // FIXME not sure about this one
        // It makes no sense for Jitsi Videobridge to pace its RTP output.
        defaults.put(
            DeviceConfiguration.PROP_VIDEO_RTP_PACING_THRESHOLD,
            Integer.toString(Integer.MAX_VALUE));

        /*
         * Drops silent audio packets that has the
         * rtp extension(rfc6464) with sound level information.
         */
        defaults.put(
            SsrcTransformEngine
                .DROP_MUTED_AUDIO_SOURCE_IN_REVERSE_TRANSFORM,
            true_);

        // override defaults with passed to the Main
        defaults.put(
            ConfigurationService.PNAME_CONFIGURATION_FILE_IS_READ_ONLY,
            System.getProperty(
                ConfigurationService.PNAME_CONFIGURATION_FILE_IS_READ_ONLY,
                true_));

        return defaults;
    }

    /**
     * Sets default system properties required to run Jitsi libraries inside of
     * a server component. The purpose of that is to disable audio/video input
     * devices etc.
     */
    public static void setSystemPropertyDefaults()
    {
        Map<String, String> defaults = getSystemPropertyDefaults();

        for (Map.Entry<String, String> e : defaults.entrySet())
        {
            String key = e.getKey();

            if (System.getProperty(key) == null)
                System.setProperty(key, e.getValue());
        }
    }
}
