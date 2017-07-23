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
 * A transcription of a conference. An instance of this class will hold the
 * complete transcription once a conference is over
 *
 * @author Nik Vaessen
 */
public class Transcription
    implements TranscriptionListener
{
    //todo design this class

    @Override
    public void notify(TranscriptionResult result)
    {

    }

    @Override
    public void completed()
    {

    }
}
