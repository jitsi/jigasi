/*
 * Jigasi, the JItsi GAteway to SIP.
 *
 * Copyright @ 2017 Atlassian Pty Ltd
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
package org.jitsi.jigasi.transcription;

import org.jitsi.jigasi.*;

/**
 * This interface is used to send a message to the chatRoom of a jitsi-meet
 * conference
 *
 * @author Nik Vaessen
 */
public interface TranscriptionResultPublisher
{
    /**
     * Publish the given TranscriptionResult to the given ChatRoom
     *
     * @param jvbConference the meeting room
     * @param result the result
     */
    void publish(JvbConference jvbConference, TranscriptionResult result);

    /**
     * Publish the given TranslationResult to the given ChatRoom
     *
     * @param jvbConference the meeting room
     * @param result the result
     */
    void publish(JvbConference jvbConference, TranslationResult result);
}
