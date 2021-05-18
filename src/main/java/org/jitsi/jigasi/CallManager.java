/*
 * Jigasi, the JItsi GAteway to SIP.
 *
 * Copyright @ 2018 - present 8x8, Inc.
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
package org.jitsi.jigasi;

import net.java.sip.communicator.plugin.reconnectplugin.*;
import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.protocol.media.*;
import net.java.sip.communicator.util.*;
import org.jitsi.jigasi.util.*;
import org.jitsi.service.configuration.*;

import java.util.*;
import java.util.concurrent.*;

/**
 * CallManager responsible for processing call operations like answer or hangup
 * in new thread using a manged pool of threads.
 *
 * @author Pawel Domas
 * @author Damian Minkov
 */
public class CallManager
{
    private final static Logger logger = Logger.getLogger(CallManager.class);

    private static final int POOL_MAX_SIZE = 20;

    private static final String POOL_THREADS_PREFIX = "jigasi-callManager";

    /**
     * The thread pool to serve all call operations like answer and hangup.
     * We initialize it with the 1 thread and can grow up to POOL_MAX_SIZE.
     */
    private static ExecutorService threadPool = Util.createNewThreadPool(POOL_MAX_SIZE, POOL_THREADS_PREFIX);

    private static boolean healthy = true;

    /**
     * Returns whether or not we consider this CallManager as healthy.
     * @see #submit(Runnable)
     * @return whether CallManager is healthy.
     */
    public static boolean isHealthy()
    {
        return healthy;
    }

    /**
     * Submits a task to the pool of threads. If a task throws an exception
     * (RejectedExecutionException) this means we are over the limit or
     * we have blocked tasks in the queue and we cannot schedule tasks any more
     * and we will mark CallManager as failing, to allow reporting this.
     * @param task
     * @return
     */
    private static Future<?> submit(Runnable task)
    {
        try
        {
            return threadPool.submit(task);
        }
        catch(RejectedExecutionException e)
        {
            logger.error("Failed to submit task for execution:" + task, e);

            CallManager.healthy = false;

            return null;
        }
    }

    /**
     * Answers a call in new thread or throws an exception if we fail to
     * schedule the task for that.
     * @param incomingCall The call to answer.
     * @throws OperationFailedException in case of failed to start task to do it
     */
    public synchronized static void acceptCall(Call incomingCall)
        throws OperationFailedException
    {
        Future result = submit(new AnswerCallThread(incomingCall, false));

        if (result == null)
        {
            // there was no task scheduled to answer the call, throw an error
            throw new OperationFailedException(
                "Failed to answer",
                OperationFailedException.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Invites the given list of <tt>callees</tt> to the given conference
     * <tt>call</tt>.
     *
     * @param callees the list of contacts to invite
     * @param call existing call
     */
    public synchronized static void inviteToConferenceCall(
        Map<ProtocolProviderService, List<String>> callees,
        Call call)
    {
        submit(new InviteToConferenceCallThread(callees, call));
    }

    /**
     * Invites a list of callees to a conference <tt>Call</tt>. If the specified
     * <tt>Call</tt> is <tt>null</tt>, creates a brand new telephony conference.
     */
    private static class InviteToConferenceCallThread
        implements Runnable
    {
        /**
         * The addresses of the callees to be invited into the telephony
         * conference to be organized by this instance. For further details,
         * refer to the documentation on the <tt>callees</tt> parameter of the
         * respective <tt>InviteToConferenceCallThread</tt> constructor.
         */
        private final Map<ProtocolProviderService, List<String>>
            callees;

        /**
         * The <tt>Call</tt>, if any, into the telephony conference of which
         * {@link #callees} are to be invited. If non-<tt>null</tt>, its
         * <tt>CallConference</tt> state will be shared with all <tt>Call</tt>s
         * established by this instance for the purposes of having the
         * <tt>callees</tt> into the same telephony conference.
         */
        private final Call call;

        /**
         * Initializes a new <tt>InviteToConferenceCallThread</tt> instance
         * which is to invite a list of callees to a conference <tt>Call</tt>.
         * If the specified <tt>call</tt> is <tt>null</tt>, creates a brand new
         * telephony conference.
         *
         * @param callees the addresses of the callees to be invited into a
         * telephony conference. The addresses are provided in multiple
         * <tt>List&lt;String&gt;</tt>s. Each such list of addresses is mapped
         * by the <tt>ProtocolProviderService</tt> through which they are to be
         * invited into the telephony conference. If there are multiple
         * <tt>ProtocolProviderService</tt>s in the specified <tt>Map</tt>, the
         * resulting telephony conference is known by the name
         * &quot;cross-protocol&quot;. It is also allowed to have a list of
         * addresses mapped to <tt>null</tt> which means that the new instance
         * will automatically choose a <tt>ProtocolProviderService</tt> to
         * invite the respective callees into the telephony conference.
         * @param call the <tt>Call</tt> to invite the specified
         * <tt>callees</tt> into. If <tt>null</tt>, this instance will create a
         * brand new telephony conference. Technically, a <tt>Call</tt> instance
         * is protocol/account-specific and it is possible to have
         * cross-protocol/account telephony conferences. That's why the
         * specified <tt>callees</tt> are invited into one and the same
         * <tt>CallConference</tt>: the one in which the specified <tt>call</tt>
         * is participating or a new one if <tt>call</tt> is <tt>null</tt>. Of
         * course, an attempt is made to have all callees from one and the same
         * protocol/account into one <tt>Call</tt> instance.
         */
        public InviteToConferenceCallThread(
            Map<ProtocolProviderService, List<String>> callees,
            Call call)
        {
            this.callees = callees;
            this.call = call;
        }

        /**
         * Invites {@link #callees} into a telephony conference which is
         * optionally specified by {@link #call}.
         */
        @Override
        public void run()
        {
            CallConference conference
                = (call == null) ? null : call.getConference();

            for(Map.Entry<ProtocolProviderService, List<String>> entry
                : callees.entrySet())
            {
                ProtocolProviderService pps = entry.getKey();

                /*
                 * We'd like to allow specifying callees without specifying an
                 * associated ProtocolProviderService.
                 */
                if (pps != null)
                {
                    OperationSetBasicTelephony<?> basicTelephony
                        = pps.getOperationSet(OperationSetBasicTelephony.class);

                    if(basicTelephony == null)
                        continue;
                }

                List<String> contactList = entry.getValue();
                String[] contactArray
                    = contactList.toArray(new String[contactList.size()]);

                /* Try to have a single Call per ProtocolProviderService. */
                Call ppsCall;

                if ((call != null) && call.getProtocolProvider().equals(pps))
                    ppsCall = call;
                else
                {
                    ppsCall = null;
                    if (conference != null)
                    {
                        List<Call> conferenceCalls = conference.getCalls();

                        if (pps == null)
                        {
                            /*
                             * We'd like to allow specifying callees without
                             * specifying an associated ProtocolProviderService.
                             * The simplest approach is to just choose the first
                             * ProtocolProviderService involved in the telephony
                             * conference.
                             */
                            if (call == null)
                            {
                                if (!conferenceCalls.isEmpty())
                                {
                                    ppsCall = conferenceCalls.get(0);
                                    pps = ppsCall.getProtocolProvider();
                                }
                            }
                            else
                            {
                                ppsCall = call;
                                pps = ppsCall.getProtocolProvider();
                            }
                        }
                        else
                        {
                            for (Call conferenceCall : conferenceCalls)
                            {
                                if (pps.equals(
                                    conferenceCall.getProtocolProvider()))
                                {
                                    ppsCall = conferenceCall;
                                    break;
                                }
                            }
                        }
                    }
                }

                OperationSetTelephonyConferencing telephonyConferencing
                    = pps.getOperationSet(
                            OperationSetTelephonyConferencing.class);

                try
                {
                    if (ppsCall == null)
                    {
                        ppsCall
                            = telephonyConferencing.createConfCall(
                            contactArray,
                            conference);
                        if (conference == null)
                            conference = ppsCall.getConference();
                    }
                    else
                    {
                        for (String contact : contactArray)
                        {
                            telephonyConferencing.inviteCalleeToCall(
                                contact,
                                ppsCall);
                        }
                    }
                }
                catch(Exception e)
                {
                    logger.error(
                        "Failed to invite callees: "
                            + Arrays.toString(contactArray),
                        e);
                }
            }
        }
    }

    /**
     * Answers to all <tt>CallPeer</tt>s associated with a specific
     * <tt>Call</tt> and, optionally, does that in a telephony conference with
     * an existing <tt>Call</tt>.
     */
    private static class AnswerCallThread
        implements Runnable
    {
        /**
         * The <tt>Call</tt> which is to be answered.
         */
        private final Call call;

        /**
         * The indicator which determines whether this instance is to answer
         * {@link #call} with video.
         */
        private final boolean video;

        public AnswerCallThread(Call call, boolean video)
        {
            this.call = call;
            this.video = video;
        }

        @Override
        public void run()
        {
            ProtocolProviderService pps = call.getProtocolProvider();
            Iterator<? extends CallPeer> peers = call.getCallPeers();

            while (peers.hasNext())
            {
                CallPeer peer = peers.next();

                if (video)
                {
                    OperationSetVideoTelephony telephony
                        = pps.getOperationSet(OperationSetVideoTelephony.class);

                    try
                    {
                        telephony.answerVideoCallPeer(peer);
                    }
                    catch (OperationFailedException ofe)
                    {
                        logger.error(
                            "Could not answer " + peer + " with video"
                                + " because of the following exception: "
                                + ofe);
                    }
                }
                else
                {
                    OperationSetBasicTelephony<?> telephony
                        = pps.getOperationSet(OperationSetBasicTelephony.class);

                    try
                    {
                        telephony.answerCallPeer(peer);
                    }
                    catch (OperationFailedException ofe)
                    {
                        logger.error(
                            "Could not answer " + peer
                                + " because of the following exception: ",
                            ofe);
                    }
                }
            }
        }
    }

    /**
     * Merges specific existing <tt>Call</tt>s into a specific telephony
     * conference.
     *
     * @param conference the conference
     * @param calls list of calls
     */
    public synchronized static void mergeExistingCalls(
        CallConference conference,
        Collection<Call> calls)
    {
        submit(new MergeExistingCalls(conference, calls));
    }

    /**
     * Merges specific existing <tt>Call</tt>s into a specific telephony
     * conference.
     */
    private static class MergeExistingCalls
        implements Runnable
    {
        /**
         * The telephony conference in which {@link #calls} are to be merged.
         */
        private final CallConference conference;

        /**
         * Second call.
         */
        private final Collection<Call> calls;

        /**
         * Initializes a new <tt>MergeExistingCalls</tt> instance which is to
         * merge specific existing <tt>Call</tt>s into a specific telephony
         * conference.
         *
         * @param conference the telephony conference in which the specified
         * <tt>Call</tt>s are to be merged
         * @param calls the <tt>Call</tt>s to be merged into the specified
         * telephony conference
         */
        public MergeExistingCalls(
            CallConference conference,
            Collection<Call> calls)
        {
            this.conference = conference;
            this.calls = calls;
        }

        /**
         * Puts off hold the <tt>CallPeer</tt>s of a specific <tt>Call</tt>
         * which are locally on hold.
         *
         * @param call the <tt>Call</tt> which is to have its <tt>CallPeer</tt>s
         * put off hold
         */
        private void putOffHold(Call call)
        {
            Iterator<? extends CallPeer> peers = call.getCallPeers();
            OperationSetBasicTelephony<?> telephony
                = call.getProtocolProvider().getOperationSet(
                OperationSetBasicTelephony.class);

            while (peers.hasNext())
            {
                CallPeer callPeer = peers.next();
                boolean putOffHold = true;

                if(callPeer instanceof MediaAwareCallPeer)
                {
                    putOffHold
                        = ((MediaAwareCallPeer<?,?,?>) callPeer)
                        .getMediaHandler()
                        .isLocallyOnHold();
                }
                if(putOffHold)
                {
                    try
                    {
                        telephony.putOffHold(callPeer);
                        Thread.sleep(400);
                    }
                    catch(Exception ofe)
                    {
                        logger.error("Failed to put off hold.", ofe);
                    }
                }
            }
        }

        @Override
        public void run()
        {
            // conference
            for (Call call : conference.getCalls())
                putOffHold(call);

            // calls
            if (!calls.isEmpty())
            {
                for(Call call : calls)
                {
                    if (conference.containsCall(call))
                        continue;

                    putOffHold(call);

                    /*
                     * Dispose of the CallPanel associated with the Call which
                     * is to be merged.
                     */
                    //closeCallContainerIfNotNecessary(conference, false);

                    call.setConference(conference);
                }
            }
        }
    }

    public synchronized static void hangupCall(Call call)
    {
        hangupCall(call, false);
    }

    public synchronized static void hangupCall(Call call, boolean unloadAccount)
    {
        if (logger.isDebugEnabled())
        {
            logger.debug("Hanging up :" + call, new Throwable());
        }

        submit(new HangupCallThread(call, unloadAccount));
    }

    public synchronized static void hangupCall(Call   call,
                                               int    reasonCode,
                                               String reason)
    {
        if (logger.isDebugEnabled())
        {
            logger.debug("Hanging up :" + call, new Throwable());
        }

        HangupCallThread hangupCallThread = new HangupCallThread(call, false);

        hangupCallThread.reasonCode = reasonCode;
        hangupCallThread.reason = reason;

        // if we are unhealthy, let's process the hangups with new threads
        // so we can clean failed or ongoing calls
        if (!healthy)
        {
            new Thread(hangupCallThread).start();
        }
        else
        {
            submit(hangupCallThread);
        }
    }

    /**
     *
     * Hangs up a specific <tt>Call</tt> (i.e. all <tt>CallPeer</tt>s associated
     * with a <tt>Call</tt>), <tt>CallConference</tt> (i.e. all <tt>Call</tt>s
     * participating in a <tt>CallConference</tt>), or <tt>CallPeer</tt>.
     */
    private static class HangupCallThread
        implements Runnable
    {
        /**
         * The logger.
         */
        private final static Logger logger
            = Logger.getLogger(HangupCallThread.class);

        private final Call call;

        private final CallConference conference;

        private final CallPeer peer;

        private final boolean unloadAccount;

        private int reasonCode
            = OperationSetBasicTelephony.HANGUP_REASON_NORMAL_CLEARING;

        private String reason = null;

        /**
         * Initializes a new <tt>HangupCallThread</tt> instance which is to hang
         * up a specific <tt>Call</tt> i.e. all <tt>CallPeer</tt>s associated
         * with the <tt>Call</tt>.
         *
         * @param call the <tt>Call</tt> whose associated <tt>CallPeer</tt>s are
         * to be hanged up
         * @param unloadAccount whether we need to unregister and unload account
         * after hangup.
         */
        public HangupCallThread(Call call, boolean unloadAccount)
        {
            this(call, null, null, unloadAccount);
        }

        /**
         * Initializes a new <tt>HangupCallThread</tt> instance which is to hang
         * up a specific <tt>Call</tt>, <tt>CallConference</tt>, or
         * <tt>CallPeer</tt>.
         *
         * @param call the <tt>Call</tt> whose associated <tt>CallPeer</tt>s are
         * to be hanged up
         * @param conference the <tt>CallConference</tt> whose participating
         * <tt>Call</tt>s re to be hanged up
         * @param peer the <tt>CallPeer</tt> to hang up
         * @param unloadAccount whether we need to unregister and unload account
         * after hangup.
         */
        private HangupCallThread(
            Call call,
            CallConference conference,
            CallPeer peer,
            boolean unloadAccount)
        {
            this.call = call;
            this.conference = conference;
            this.peer = peer;
            this.unloadAccount = unloadAccount;
        }

        @Override
        public void run()
        {
            /*
             * There is only an OperationSet which hangs up a CallPeer at a time
             * so prepare a list of all CallPeers to be hanged up.
             */
            Set<CallPeer> peers = new HashSet<CallPeer>();

            if (call != null)
            {
                Iterator<? extends CallPeer> peerIter = call.getCallPeers();

                while (peerIter.hasNext())
                    peers.add(peerIter.next());
            }

            if (conference != null)
                peers.addAll(conference.getCallPeers());

            if (peer != null)
                peers.add(peer);

            for (CallPeer peer : peers)
            {
                OperationSetBasicTelephony<?> basicTelephony
                    = peer.getProtocolProvider().getOperationSet(
                    OperationSetBasicTelephony.class);

                try
                {
                    basicTelephony.hangupCallPeer(peer, reasonCode, reason);
                }
                catch (Exception ofe)
                {
                    logger.error("Could not hang up: " + peer, ofe);
                }
            }

            // we are unloading the account after hangup, just to avoid some
            // exceptions on hangup, where the provider manages first to
            // close the connection before the hangup thread manages to hangup
            if (this.unloadAccount)
            {
                ProtocolProviderService pp = call.getProtocolProvider();

                // unregister with user request true to indicate we just want
                // to remove it and no reconnection is needed.
                try
                {
                    pp.unregister(true);
                }
                catch(OperationFailedException e)
                {
                    logger.error("Cannot unregister");
                }

                ProtocolProviderFactory factory = ProtocolProviderFactory.getProtocolProviderFactory(
                    JigasiBundleActivator.osgiContext, pp.getProtocolName());

                if (factory != null)
                {
                    logger.info(
                        call.getData(CallContext.class)
                            + " Removing account " + pp.getAccountID());

                    factory.unloadAccount(pp.getAccountID());

                    // Deleting not needed property
                    ConfigurationService config = JigasiBundleActivator.getConfigurationService();
                    config.removeProperty(ReconnectPluginActivator.ATLEAST_ONE_CONNECTION_PROP
                        + "." + pp.getAccountID().getAccountUniqueID());
                }
            }
        }
    }

    /**
     * Shutdowns internal thread pool used and waits for all tasks to finish
     * gracefully withing 5 seconds or <tt>TimeoutException</tt> is thrown.
     *
     * @throws InterruptedException if waiting thread is interrupted.
     * @throws TimeoutException if we fail to shutdown in 5 seconds.
     */
    public static synchronized void restartPool()
        throws InterruptedException, TimeoutException
    {
        threadPool.shutdown();

        threadPool.awaitTermination(5, TimeUnit.SECONDS);

        if (!threadPool.isTerminated())
            throw new TimeoutException();

        threadPool = Util.createNewThreadPool(POOL_MAX_SIZE, POOL_THREADS_PREFIX);
    }
}
