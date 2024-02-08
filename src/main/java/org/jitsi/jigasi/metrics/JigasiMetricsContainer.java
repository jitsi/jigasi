/*
 * Jigasi, the JItsi GAteway to SIP.
 *
 * Copyright @ 2024 - present 8x8, Inc
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
package org.jitsi.jigasi.metrics;

import io.prometheus.client.*;
import org.jitsi.metrics.*;

public class JigasiMetricsContainer extends MetricsContainer
{
    public final static JigasiMetricsContainer INSTANCE = new JigasiMetricsContainer();

    private JigasiMetricsContainer()
    {
        super(CollectorRegistry.defaultRegistry, "jitsi_jigasi");
    }
}
