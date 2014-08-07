/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.osgi.framework;

import net.java.sip.communicator.util.*;

import java.util.*;
import java.util.concurrent.*;

/**
 *
 * @author Lyubomir Marinov
 */
public class AsyncExecutor<T extends Runnable>
{
    private long keepAliveTime;

    private final List<CommandFuture<T>> queue
        = new LinkedList<CommandFuture<T>>();

    private boolean shutdown;

    private boolean shutdownNow;

    private Thread thread;

    public AsyncExecutor()
    {
        this(0, TimeUnit.MILLISECONDS);
    }

    public AsyncExecutor(long keepAliveTime, TimeUnit unit)
    {
        if (keepAliveTime < 0)
            throw new IllegalArgumentException("keepAliveTime");

        this.keepAliveTime = unit.toMillis(keepAliveTime);
    }

    private synchronized boolean contains(T command)
    {
        for (CommandFuture<T> commandFuture : queue)
            if (commandFuture.command == command)
                return true;
        return false;
    }

    public void execute(T command)
    {
        submit(command);
    }

    private void runInThread()
    {
        long idleTime = -1;

        while (true)
        {
            CommandFuture<T> commandFuture;

            synchronized (this)
            {
                if (shutdownNow)
                    return;
                else if (queue.isEmpty())
                {
                    /*
                     * Technically, we may keep this Thread alive much longer
                     * than keepAliveTime since idleTime because we always try
                     * to wait for at least keepAliveTime in a single wait. But
                     * we are OK with it as long as this AsyncExecutor does not
                     * keep its Thread forever in the presence of an actual
                     * non-infinite keepAliveTime.
                     */
                    if (idleTime == -1)
                        idleTime = System.currentTimeMillis();
                    else if ((System.currentTimeMillis() - idleTime)
                            > keepAliveTime)
                        return;

                    boolean interrupted = false;

                    try
                    {
                        wait(keepAliveTime);
                    }
                    catch (InterruptedException ie)
                    {
                        interrupted = true;
                    }
                    if (interrupted)
                        Thread.currentThread().interrupt();

                    continue;
                }
                else
                {
                    idleTime = -1;
                    commandFuture = queue.remove(0);
                }
            }

            T command = commandFuture.command;
            Throwable exception = null;

            try
            {
                command.run();
            }
            catch (Throwable t)
            {
                exception = t;

                if (t instanceof ThreadDeath)
                    throw (ThreadDeath) t;
                else
                    uncaughtException(command, t);
            }
            finally
            {
                commandFuture.setDone(
                        (exception == null) ? Boolean.TRUE : exception);
            }
        }
    }

    public void setKeepAliveTime(long keepAliveTime, TimeUnit unit)
    {
        if (keepAliveTime < 0)
            throw new IllegalArgumentException("keepAliveTime");

        synchronized (this)
        {
            this.keepAliveTime = unit.toMillis(keepAliveTime);
            notifyAll();
        }
    }

    public synchronized void shutdown()
    {
        shutdown = true;
        notifyAll();
    }

    public List<T> shutdownNow()
    {
        List<CommandFuture<T>> awaiting;

        synchronized (this)
        {
            shutdown = true;
            shutdownNow = true;
            notifyAll();

            boolean interrupted = false;

            while (thread != null)
            {
                try
                {
                    wait(keepAliveTime);
                }
                catch (InterruptedException ie)
                {
                    interrupted = true;
                }
            }
            if (interrupted)
                Thread.currentThread().interrupt();

            awaiting = new ArrayList<CommandFuture<T>>(queue.size());
            awaiting.addAll(queue);
        }

        List<T> awaitingCommands = new ArrayList<T>(awaiting.size());

        for (CommandFuture<T> commandFuture : awaiting)
        {
            awaitingCommands.add(commandFuture.command);
            commandFuture.setDone(Boolean.FALSE);
        }

        return awaitingCommands;
    }

    public synchronized Future<?> submit(T command)
    {
        if (command == null)
            throw new NullPointerException("command");
        if (shutdown)
            throw new RejectedExecutionException("shutdown");
        if (contains(command))
            throw new RejectedExecutionException("contains");

        CommandFuture<T> future = new CommandFuture<T>(command);

        queue.add(future);
        startThreadOrNotifyAll();

        return future;
    }

    private synchronized void startThreadOrNotifyAll()
    {
        if ((thread == null)
                && (!shutdown && !shutdownNow)
                && !queue.isEmpty())
        {
            thread
                = new Thread(getClass().getName())
                {
                    @Override
                    public void run()
                    {
                        try
                        {
                            runInThread();
                        }
                        finally
                        {
                            synchronized (AsyncExecutor.this)
                            {
                                if (Thread.currentThread().equals(thread))
                                {
                                    thread = null;
                                    startThreadOrNotifyAll();
                                }
                            }
                        }
                    }
                };
            thread.setDaemon(true);
            thread.start();
        }
        else
            notifyAll();
    }

    protected void uncaughtException(T command, Throwable exception)
    {
        Logger.getLogger(AsyncExecutor.class)
                .error("Error executing command "+command, exception);
    }

    private static class CommandFuture<T extends Runnable>
        implements Future<Object>
    {
        public final T command;

        private Boolean done;

        private Throwable exception;

        public CommandFuture(T command)
        {
            this.command = command;
        }

        public boolean cancel(boolean mayInterruptIfRunning)
        {
            // TODO Auto-generated method stub
            return false;
        }

        public Object get()
            throws ExecutionException,
                   InterruptedException
        {
            try
            {
                return get(0, TimeUnit.MILLISECONDS);
            }
            catch (TimeoutException te)
            {
                /*
                 * Since the timeout is infinite, a TimeoutException is
                 * not expected.
                 */
                throw new RuntimeException(te);
            }
        }

        public synchronized Object get(long timeout, TimeUnit unit)
            throws ExecutionException,
                   InterruptedException,
                   TimeoutException
        {
            timeout = unit.toMillis(timeout);

            boolean timeoutException = false;

            while (true)
            {
                if (done != null)
                {
                    if (done)
                        break;
                    else
                        throw new CancellationException();
                }
                else if (exception != null)
                    throw new ExecutionException(exception);
                else if (timeoutException)
                    throw new TimeoutException();
                else
                {
                    wait(timeout);
                    timeoutException = (timeout != 0);
                }
            }
            return null;
        }

        public synchronized boolean isCancelled()
        {
            return ((done != null) && !done.booleanValue());
        }

        public synchronized boolean isDone()
        {
            return ((done != null) || (exception != null));
        }

        synchronized void setDone(Object done)
        {
            if (done instanceof Boolean)
                this.done = (Boolean) done;
            else if (done instanceof Throwable)
                exception = (Throwable) done;
            else
                throw new IllegalArgumentException("done");

            notifyAll();
        }
    }
}
