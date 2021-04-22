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
package org.jitsi.jigasi.sounds;

import java.time.*;
import java.util.concurrent.atomic.*;

/**
 * Implements RateLimiter for sound notifications.
 */
class SoundRateLimiter
{
    /**
     * Initial time point.
     */
    private final AtomicReference<Instant> startTimePoint = new AtomicReference<>(null);

    /**
     * Timeout in milliseconds.
     */
    private long limiterTimeout;

    /**
     * SoundRateLimiter constructor.
     *
     * @param maxTimeout Timeout in milliseconds to block notification.
     */
    SoundRateLimiter(long maxTimeout)
    {
        this.limiterTimeout = maxTimeout;
    }

    /**
     * Checks if timeout since last notification has passed.
     * When it returns false, sound notification can be played.
     *
     * @return true of enabled, false otherwise.
     */
    public boolean on()
    {
        if (this.startTimePoint.compareAndSet(null, Instant.now()))
        {
            return false;
        }

        Instant prevTimePoint = this.startTimePoint.get();

        if (prevTimePoint == null)
        {
            return false;
        }

        long elapsedMs =
            Instant.now().toEpochMilli()
                - prevTimePoint.toEpochMilli();

        if (elapsedMs >= this.limiterTimeout)
        {
            this.startTimePoint.set(Instant.now());
            return false;
        }

        return true;
    }

    /**
     * {@inheritDoc}
     */
    public void reset()
    {
        this.startTimePoint.set(null);
    }
}
