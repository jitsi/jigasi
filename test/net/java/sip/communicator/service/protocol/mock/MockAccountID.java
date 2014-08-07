/*
 * Jitsi Videobridge, OpenSource video conferencing.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
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
