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
package org.jitsi.jigasi.transcription.action;

/**
 * Action handler. Initialized with a name, phrase to detect and url address to
 * use for sending detected result when a command is detected.
 *
 * @author Damian Minkov
 */
public class ActionHandler
{
    /**
     * The name of the handler, used only internally (logging).
     */
    private String name;

    /**
     * The phrase to detect.
     */
    private String phrase;

    /**
     * The url address to use for sending commands.
     */
    private String url;

    /**
     * Constructs new ActionHandler.
     * @param name the name.
     * @param phrase the phrase to expect.
     * @param url the address to post the result.
     */
    public ActionHandler(String name, String phrase, String url)
    {
        this.name = name;
        this.phrase = phrase;
        this.url = url;
    }

    /**
     * The phrase to detect.
     * @return the phrase to detect.
     */
    public String getPhrase()
    {
        return this.phrase;
    }

    /**
     * The url address to use for sending commands.
     * @return the url address to use for sending commands.
     */
    public String getUrl()
    {
        return url;
    }

    /**
     * The name of the handler, used only internally (logging).
     * @return the name of the handler.
     */
    public String getName()
    {
        return name;
    }

    @Override
    public String toString()
    {
        return "ActionHandler{" +
            "name='" + name + '\'' +
            ", phrase='" + phrase + '\'' +
            ", url='" + url + '\'' +
            '}';
    }
}
