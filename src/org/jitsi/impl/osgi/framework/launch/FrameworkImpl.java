/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.osgi.framework.launch;

import net.java.sip.communicator.util.*;
import org.jitsi.impl.osgi.framework.*;
import org.jitsi.impl.osgi.framework.startlevel.*;
import org.osgi.framework.*;
import org.osgi.framework.launch.*;
import org.osgi.framework.startlevel.*;

import java.io.*;
import java.util.*;

/**
 *
 * @author Lyubomir Marinov
 */
public class FrameworkImpl
    extends BundleImpl
    implements Framework
{
    /**
     * The logger
     */
    private final Logger logger = Logger.getLogger(FrameworkImpl.class);

    private final List<BundleImpl> bundles = new LinkedList<BundleImpl>();

    private final Map<String, String> configuration;

    private EventDispatcher eventDispatcher;

    private FrameworkStartLevelImpl frameworkStartLevel;

    private long nextBundleId = 1;

    private long nextServiceId = 1;

    private final List<ServiceRegistrationImpl> serviceRegistrations
        = new LinkedList<ServiceRegistrationImpl>();

    public FrameworkImpl(Map<String, String> configuration)
    {
        super(null, 0, null);

        this.configuration = configuration;

        bundles.add(this);
    }

    @Override
    public <A> A adapt(Class<A> type)
    {
        Object adapt;

        if (FrameworkStartLevel.class.equals(type))
        {
            synchronized (this)
            {
                if (frameworkStartLevel == null)
                    frameworkStartLevel = new FrameworkStartLevelImpl(this);

                adapt = frameworkStartLevel;
            }
        }
        else
            adapt = null;

        @SuppressWarnings("unchecked")
        A a = (A) adapt;

        return (a != null) ? a : super.adapt(type);
    }

    public void addBundleListener(BundleImpl origin, BundleListener listener)
    {
        if (eventDispatcher != null)
            eventDispatcher.addListener(origin, BundleListener.class, listener);
    }

    public void addServiceListener(
            BundleImpl origin,
            ServiceListener listener,
            Filter filter)
    {
        if (eventDispatcher != null)
            eventDispatcher.addListener(origin, ServiceListener.class, listener);
    }

    public void fireBundleEvent(int type, Bundle bundle)
    {
        fireBundleEvent(type, bundle, bundle);
    }

    private void fireBundleEvent(int type, Bundle bundle, Bundle origin)
    {
        if (eventDispatcher != null)
            eventDispatcher.fireBundleEvent(
                    new BundleEvent(type, bundle, origin));
    }

    private void fireFrameworkEvent(int type, FrameworkListener... listeners)
    {
        if ((listeners != null) && (listeners.length != 0))
        {
            FrameworkEvent event = new FrameworkEvent(type, this, null);

            for (FrameworkListener listener : listeners)
                try
                {
                    listener.frameworkEvent(event);
                }
                catch (Throwable t)
                {
                    if (type != FrameworkEvent.ERROR)
                    {
                        // TODO Auto-generated method stub
                    }
                    logger.error("Error firing framework event", t);
                }
        }
    }

    private void fireServiceEvent(int type, ServiceReference<?> reference)
    {
        if (eventDispatcher != null)
            eventDispatcher.fireServiceEvent(new ServiceEvent(type, reference));
    }

    public BundleImpl getBundle(long id)
    {
    	if (id == 0)
    		return this;
    	else
    	{
	    	synchronized (this.bundles)
	    	{
	    		for (BundleImpl bundle : this.bundles)
	    			if (bundle.getBundleId() == id)
	    				return bundle;
	    	}
	    	return null;
    	}
    }

    private List<BundleImpl> getBundlesByStartLevel(int startLevel)
    {
        List<BundleImpl> bundles = new LinkedList<BundleImpl>();

        synchronized (this.bundles)
        {
            for (BundleImpl bundle : this.bundles)
            {
                BundleStartLevel bundleStartLevel
                    = bundle.adapt(BundleStartLevel.class);

                if ((bundleStartLevel != null)
                        && (bundleStartLevel.getStartLevel() == startLevel))
                    bundles.add(bundle);
            }
        }
        return bundles;
    }

    public Collection<ServiceReference> getServiceReferences(
            BundleImpl origin,
            Class<?> clazz,
            String className,
            Filter filter,
            boolean checkAssignable)
        throws InvalidSyntaxException
    {
        Filter classNameFilter
            = FrameworkUtil.createFilter(
            '('
                + Constants.OBJECTCLASS
                + '='
                + ((className == null) ? '*' : className)
                + ')');
        List<ServiceReference> serviceReferences
            = new LinkedList<ServiceReference>();

        synchronized (serviceRegistrations)
        {
            for (ServiceRegistrationImpl serviceRegistration
                    : serviceRegistrations)
            {
                ServiceReference<?> serviceReference
                    = serviceRegistration.getReference();

                if (classNameFilter.match(serviceReference)
                        && ((filter == null)
                                || (filter.match(serviceReference))))
                {
                    ServiceReference serviceReferenceS
                        = serviceRegistration.getReference(clazz);

                    if (serviceReferenceS != null)
                        serviceReferences.add(serviceReferenceS);
                }
            }
        }

        return serviceReferences;
    }

    @Override
    public FrameworkImpl getFramework()
    {
        return this;
    }

    private long getNextBundleId()
    {
        return nextBundleId++;
    }

    public void init()
        throws BundleException
    {
        setState(STARTING);
    }

    public Bundle installBundle(
            BundleImpl origin,
            String location, InputStream input)
        throws BundleException
    {
        if (location == null)
            throw new BundleException("location");

        BundleImpl bundle = null;
        boolean fireBundleEvent = false;

        synchronized (bundles)
        {
            for (BundleImpl existing : bundles)
                if (existing.getLocation().equals(location))
                {
                    bundle = existing;
                    break;
                }
            if (bundle == null)
            {
                bundle
                    = new BundleImpl(
                            getFramework(),
                            getNextBundleId(),
                            location);
                bundles.add(bundle);
                fireBundleEvent = true;
            }
        }

        if (fireBundleEvent)
            fireBundleEvent(BundleEvent.INSTALLED, bundle, origin);

        return bundle;
    }

    public ServiceRegistration registerService(
            BundleImpl origin,
            Class clazz,
            String[] classNames,
            Object service,
            Dictionary<String, ?> properties)
    {
        if ((classNames == null) || (classNames.length == 0))
            throw new IllegalArgumentException("classNames");
        else  if (service == null)
            throw new IllegalArgumentException("service");
        else
        {
            Class<?> serviceClass = service.getClass();

            if (!ServiceFactory.class.isAssignableFrom(serviceClass))
            {
                ClassLoader classLoader = serviceClass.getClassLoader();

                for (String className : classNames)
                {
                    boolean illegalArgumentException = true;
                    Throwable cause = null;

                    try
                    {
                        if (Class.forName(className, false, classLoader)
                                .isAssignableFrom(serviceClass))
                        {
                            illegalArgumentException = false;
                        }
                    }
                    catch (ClassNotFoundException cnfe)
                    {
                        cause = cnfe;
                    }
                    catch (ExceptionInInitializerError eiie)
                    {
                        cause = eiie;
                    }
                    catch (LinkageError le)
                    {
                        cause = le;
                    }
                    if (illegalArgumentException)
                    {
                        IllegalArgumentException iae
                            = new IllegalArgumentException(className);

                        if (cause != null)
                            iae.initCause(cause);
                        throw iae;
                    }
                }
            }
        }

        long serviceId;

        synchronized (serviceRegistrations)
        {
            serviceId = nextServiceId++;
        }

        ServiceRegistrationImpl serviceRegistration
            = new ServiceRegistrationImpl(
                    origin,
                    serviceId,
                    classNames, service, properties);

        synchronized (serviceRegistrations)
        {
            serviceRegistrations.add(serviceRegistration);
        }
        fireServiceEvent(
                ServiceEvent.REGISTERED,
                serviceRegistration.getReference());
        return serviceRegistration;
    }

    public void removeBundleListener(BundleImpl origin, BundleListener listener)
    {
        if (eventDispatcher != null)
            eventDispatcher.removeListener(
                    origin,
                    BundleListener.class,
                    listener);
    }

    public void removeServiceListener(
            BundleImpl origin,
            ServiceListener listener)
    {
        if (eventDispatcher != null)
            eventDispatcher.removeListener(
                    origin,
                    ServiceListener.class,
                    listener);
    }

    @Override
    public void start(int options)
        throws BundleException
    {
        int state = getState();

        if ((state == INSTALLED) || (state == RESOLVED))
        {
            init();
            state = getState();
        }

        if (state == STARTING)
        {
            int startLevel = 1;

            if (configuration != null)
            {
                String s
                    = configuration.get(
                            Constants.FRAMEWORK_BEGINNING_STARTLEVEL);

                if (s != null)
                    try
                    {
                        startLevel = Integer.parseInt(s);
                    }
                    catch (NumberFormatException nfe)
                    {
                    }
            }

            FrameworkStartLevel frameworkStartLevel
                = adapt(FrameworkStartLevel.class);
            FrameworkListener listener
                = new FrameworkListener()
                {
                    public void frameworkEvent(FrameworkEvent event)
                    {
                        synchronized (this)
                        {
                            notifyAll();
                        }
                    }
                };

            frameworkStartLevel.setStartLevel(
                    startLevel,
                    new FrameworkListener[] { listener });
            synchronized (listener)
            {
                boolean interrupted = false;

                while (frameworkStartLevel.getStartLevel() < startLevel)
                    try
                    {
                        listener.wait();
                    }
                    catch (InterruptedException ie)
                    {
                        interrupted = true;
                    }
                if (interrupted)
                    Thread.currentThread().interrupt();
            }

            setState(ACTIVE);
        }
    }

    public void startLevelChanged(
            int oldStartLevel, int newStartLevel,
            FrameworkListener... listeners)
    {
        if (oldStartLevel < newStartLevel)
        {
            for (BundleImpl bundle : getBundlesByStartLevel(newStartLevel))
            {
                try
                {
                    BundleStartLevel bundleStartLevel
                        = bundle.adapt(BundleStartLevel.class);
                    int options = START_TRANSIENT;

                    if (bundleStartLevel.isActivationPolicyUsed())
                        options |= START_ACTIVATION_POLICY;
                    bundle.start(options);
                }
                catch (Throwable t)
                {
                    if (t instanceof ThreadDeath)
                        throw (ThreadDeath) t;
                    // TODO Auto-generated method stub

                    logger.error("Error changing start level", t);
                }
            }
        }

        fireFrameworkEvent(FrameworkEvent.STARTLEVEL_CHANGED, listeners);
    }

    public void startLevelChanging(
            int oldStartLevel, int newStartLevel,
            FrameworkListener... listeners)
    {
        if (oldStartLevel > newStartLevel)
        {
            for (BundleImpl bundle : getBundlesByStartLevel(oldStartLevel))
            {
                try
                {
                    bundle.stop(STOP_TRANSIENT);
                }
                catch (Throwable t)
                {
                    if (t instanceof ThreadDeath)
                        throw (ThreadDeath) t;
                    // TODO Auto-generated method stub

                    logger.error("Error changing start level", t);
                }
            }
        }
    }

    @Override
    protected void stateChanged(int oldState, int newState)
    {
        switch (newState)
        {
        case RESOLVED:
            if (eventDispatcher != null)
            {
                eventDispatcher.stop();
                eventDispatcher = null;
            }
            synchronized (this)
            {
                if (frameworkStartLevel != null)
                {
                    frameworkStartLevel.stop();
                    frameworkStartLevel = null;
                }
            }
            break;
        case STARTING:
            eventDispatcher = new EventDispatcher();
            break;
        }

        super.stateChanged(oldState, newState);
    }

    @Override
    public void stop(int options)
        throws BundleException
    {
        final FrameworkStartLevelImpl frameworkStartLevel
            = (FrameworkStartLevelImpl) adapt(FrameworkStartLevel.class);

        new Thread(getClass().getName() + ".stop")
        {
            @Override
            public void run()
            {
                FrameworkImpl framework = FrameworkImpl.this;

                framework.setState(STOPPING);

                FrameworkListener listener
                    = new FrameworkListener()
                    {
                        public void frameworkEvent(FrameworkEvent event)
                        {
                            synchronized (this)
                            {
                                notifyAll();
                            }
                        }
                    };

                frameworkStartLevel.internalSetStartLevel(0, listener);
                synchronized (listener)
                {
                    boolean interrupted = false;

                    while (frameworkStartLevel.getStartLevel() != 0)
                        try
                        {
                            listener.wait();
                        }
                        catch (InterruptedException ie)
                        {
                            interrupted = true;
                        }
                    if (interrupted)
                        Thread.currentThread().interrupt();
                }

                framework.setState(RESOLVED);
                // Kills the process to clear static fields before next restart
                System.exit(0);
            }
        }
            .start();
    }

    public void unregisterService(
            BundleImpl origin,
            ServiceRegistration<?> serviceRegistration)
    {
        boolean removed;

        synchronized (serviceRegistrations)
        {
            removed = serviceRegistrations.remove(serviceRegistration);
        }

        if (removed)
        {
            fireServiceEvent(
                    ServiceEvent.UNREGISTERING,
                    serviceRegistration.getReference());
        }
        else
            throw new IllegalStateException("serviceRegistrations");
    }

    public ServiceReference<?>[] getRegisteredServices()
    {
        ServiceReference<?>[] references
                = new ServiceReference[serviceRegistrations.size()];

        for(int i=0; i<serviceRegistrations.size(); i++)
        {
            references[i] = serviceRegistrations.get(i).getReference();
        }

        return references;
    }

    public FrameworkEvent waitForStop(long timeout)
        throws InterruptedException
    {
        // TODO Auto-generated method stub
        return null;
    }
}
