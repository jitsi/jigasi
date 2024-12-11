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


import org.jitsi.utils.logging.*;

import java.util.*;

/**
 * Utility class for parsing command line arguments that take some value.
 * Arguments can have one of the following formats:
 * <ul>
 * <li>"arg=value"</li>
 * <li>"-arg=value"</li>
 * <li>"--arg=value"</li>
 * </ul>
 * It's also possible to specify required arguments. If any of required
 * arguments is not found {@link ParseException} will be thrown by
 * {@link #parse(String[])}.
 *
 * @author Pawel Domas
 */
public class CmdLine
{
    /**
     * The logger
     */
    private final static Logger logger = Logger.getLogger(CmdLine.class);

    /**
     * Map of argument values.
     */
    private Map<String, String> argMap = new HashMap<String, String>();

    /**
     * The list of required arguments.
     */
    private List<String> requiredArgs = new ArrayList<String>();

    /**
     * Adds argument name to the list of required arguments.
     * @param reqArg "arg", "-arg" or "--arg" argument name to be added.
     */
    public void addRequiredArgument(String reqArg)
    {
        reqArg = cleanHyphens(reqArg);

        if (!requiredArgs.contains(reqArg))
            requiredArgs.add(reqArg);
    }

    /**
     * Removes given argument name from the list of required arguments.
     * @param reqArg "arg", "-arg" or "--arg" argument name.
     */
    public void removeRequiredArgument(String reqArg)
    {
        reqArg = cleanHyphens(reqArg);

        requiredArgs.remove(reqArg);
    }

    /**
     * Returns the list of required arguments. Names are stripped from hyphens.
     */
    public List<String> getRequiredArguments()
    {
        return Collections.unmodifiableList(requiredArgs);
    }

    private String cleanHyphens(String arg)
    {
        if (arg.startsWith("--"))
            return arg.substring(2);
        else if (arg.startsWith("-"))
            return arg.substring(1);
        else
            return arg;
    }

    /**
     * Parses the array of command line arguments.
     *
     * @param args String array which should come from the "main" method.
     *
     * @throws ParseException if any of required arguments has not been found
     *         in <tt>args</tt>.
     */
    public void parse(String[] args) throws ParseException
    {
        for (String arg : args)
        {
            arg = cleanHyphens(arg);

            int eqIdx = arg.indexOf("=");
            if (eqIdx <= 0)
            {
                logger.warn("Skipped invalid cmd line argument: " + arg);
                continue;
            }
            else if (eqIdx == arg.length() - 1)
            {
                logger.warn("Skipped empty cmd line argument: " + arg);
                continue;
            }

            String key = arg.substring(0, eqIdx);
            String val = arg.substring(eqIdx+1);
            argMap.put(key, val);
        }

        List<String> leftReqArgs = new ArrayList<String>(requiredArgs);
        leftReqArgs.removeAll(argMap.keySet());
        if (!leftReqArgs.isEmpty())
        {
            throw new ParseException(
                "Some of required arguments were not specified: "
                        + leftReqArgs.toString());
        }
    }

    /**
     * Returns the value of cmd line argument for given name. <tt>null</tt>
     * if there was no value or it was empty.
     * @param opt the name of command line argument which value we want to get.
     */
    public String getOptionValue(String opt)
    {
        return argMap.get(cleanHyphens(opt));
    }

    /**
     * Returns the value of cmd line argument for given name.
     * <tt>defaultValue</tt> if there was no value or it was empty.
     * @param opt the name of command line argument which value we want to get.
     * @param defaultValue the default value which should be returned if the
     *                     argument value is missing.
     */
    public String getOptionValue(String opt, String defaultValue)
    {
        String val = getOptionValue(opt);
        return val != null ? val : defaultValue;
    }

    /**
     * Returns <tt>int</tt> value of cmd line argument for given name.
     * <tt>defaultValue</tt> if there was no valid value for that argument.
     * @param opt the name of command line argument which value we want to get.
     * @param defaultValue the default value which should be returned if the
     *                     argument value is missing.
     */
    public int getIntOptionValue(String opt, int defaultValue)
    {
        String val = getOptionValue(opt);
        if (val == null)
            return defaultValue;
        try
        {
            return Integer.parseInt(val);
        }
        catch (NumberFormatException fmt)
        {
            return defaultValue;
        }
    }
}
