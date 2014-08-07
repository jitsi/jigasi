package org.jitsi.jigasi;

/**
 *
 */
public class MockCallsControl
    implements CallsControl
{
    @Override
    public String allocateNewSession(SipGateway gateway)
    {
        return Long.toHexString(System.currentTimeMillis());
    }

    @Override
    public void callEnded(SipGateway gateway, String callResource)
    {

    }

    @Override
    public String extractCallIdFromResource(String callResource)
    {
        return callResource;
    }
}
