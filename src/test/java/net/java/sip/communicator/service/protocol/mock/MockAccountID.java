/*
 * Jigasi, the JItsi GAteway to SIP.
 *
 * Copyright @ 2015 Atlassian Pty Ltd
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
package net.java.sip.communicator.service.protocol.mock;

import net.java.sip.communicator.service.protocol.*;

import java.util.*;

/**
 * Mock <tt>AccountID</tt> for testing purposes.
 *
 * @author Pawel Domas
 */
public class MockAccountID
    extends AccountID
{
    /**
     * Creates an account id for the specified provider userid and
     * accountProperties.
     * If account uid exists in account properties, we are loading the account
     * and so load its value from there, prevent changing account uid
     * when server changed (serviceName has changed).
     *
     * @param userID            a String that uniquely identifies the user.
     * @param accountProperties a Map containing any other protocol and
     *                          implementation specific account initialization properties
     * @param protocolName      the name of the protocol implemented by the provider
     *                          that this id is meant for.
     */
    public MockAccountID(String userID,
                            Map<String, String> accountProperties,
                            String protocolName)
    {
        super(userID, accountProperties, protocolName, "mock");
    }
}
