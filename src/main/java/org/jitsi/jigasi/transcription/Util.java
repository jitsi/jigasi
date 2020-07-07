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

import org.jitsi.utils.logging.*;
import org.json.*;

import java.io.*;
import java.net.*;

/**
 * Utility functions used in the transcription package.
 *
 * @author Damian Minkov
 */
public class Util
{
    /**
     * The logger for this class
     */
    private final static Logger logger = Logger.getLogger(Util.class);

    /**
     * Posts json object to an address of a service to handle it and further
     * process it.
     * @param address the address where to send the post request.
     * @param json the json object to send.
     */
    public static void postJSON(String address, JSONObject json)
    {
        try
        {
            URL url = new URL(address);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setDoOutput(true);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");

            OutputStream os = conn.getOutputStream();
            os.write(json.toString().getBytes());
            os.flush();

            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK)
            {
                logger.error("Error for action post received: "
                    + conn.getResponseCode()
                    + "(" + conn.getResponseMessage() + ")");
            }

            conn.disconnect();
        }
        catch (IOException e)
        {
            logger.error("Error posting transcription", e);
        }
    }
}
