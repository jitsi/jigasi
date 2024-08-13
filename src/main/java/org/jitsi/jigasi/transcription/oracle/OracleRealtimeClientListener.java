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

package org.jitsi.jigasi.transcription.oracle;
import com.oracle.bmc.aispeech.model.*;

public interface OracleRealtimeClientListener
{
    void onClose(int statusCode, String statusMessage);

    void onAckMessage(RealtimeMessageAckAudio ackMessage);

    void onResult(RealtimeMessageResult result);

    void onError(Throwable error);

    void onConnect();

    void onConnectMessage(RealtimeMessageConnect connectMessage);
}
