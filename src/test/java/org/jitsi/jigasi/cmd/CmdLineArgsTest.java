/*
 * Copyright @ 2015 - present, 8x8 Inc
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
package org.jitsi.jigasi.cmd;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.*;

public class CmdLineArgsTest
{
    /**
     * A basic test for {@link CmdLine} class.
     */
    @Test
    public void testJvbArgs()
        throws ParseException
    {
        String[] args = {
            "--apis=xmpp,rest",
            "-blablaarg=",
            "--domain=example.com",
            "-max-port=21000",
            "secret=secretpass",
            "--port=5275",
            "somegarbagearg",
            "-=dsf="
        };

        // create the parser
        CmdLine parser = new CmdLine();

        // parse the command line arguments
        parser.parse(args);

        assertEquals("example.com", parser.getOptionValue("domain"));
        assertEquals(21000, parser.getIntOptionValue("max-port", 1));
        assertEquals("secretpass", parser.getOptionValue("secret"));
        assertEquals(5275, parser.getIntOptionValue("port", 1));
        assertEquals("xmpp,rest", parser.getOptionValue("apis"));

        // Default value
        assertEquals(
            "localhost", parser.getOptionValue("host", "localhost"));

        // Parsed default value
        assertEquals(10000, parser.getIntOptionValue("min-port", 10000));
    }

    @Test
    public void testRequiredArg()
    {
        String[] args = { "--min-port=23423" };

        CmdLine parser = new CmdLine();

        parser.addRequiredArgument("-max-port");
        parser.addRequiredArgument("min-port");

        try
        {
            parser.parse(args);

            fail("Missed required argument");
        }
        catch (ParseException e)
        {
            assertEquals(
                "Some of required arguments were not specified: [max-port]",
                e.getMessage());
        }
    }
}
