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

/**
 * A listener object which will be notified when a TranscriptEvent is created.
 *
 * @author Damian Minkov
 */
public interface TranscriptionEventListener
{
    /**
     * Notifies for newly create <tt>TranscriptEvent</tt>.
     * @param transcriber the transcriber creating the event.
     * @param event the newly created event.
     */
    public void notify(Transcriber transcriber, TranscriptEvent event);
}
