/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.osgi.framework;

import org.jitsi.impl.osgi.framework.launch.*;
import org.osgi.framework.*;

import java.io.*;
import java.util.*;

/**
 *
 * @author Lyubomir Marinov
 */
public class BundleContextImpl
    implements BundleContext
{
    private final BundleImpl bundle;

    private final FrameworkImpl framework;

    public BundleContextImpl(FrameworkImpl framework, BundleImpl bundle)
    {
        this.framework = framework;
        this.bundle = bundle;
    }

    public void addBundleListener(BundleListener listener)
    {
        framework.addBundleListener(getBundle(), listener);
    }

    public void addFrameworkListener(FrameworkListener listener)
    {
        // TODO Auto-generated method stub
    }

    public void addServiceListener(ServiceListener listener)
    {
        try
        {
            addServiceListener(listener, null);
        }
        catch (InvalidSyntaxException ise)
        {
            // Since filter is null, there should be no InvalidSyntaxException.
        }
    }

    public void addServiceListener(ServiceListener listener, String filter)
        throws InvalidSyntaxException
    {
        framework.addServiceListener(
                getBundle(),
                listener,
                (filter == null) ? null : createFilter(filter));
    }

    public Filter createFilter(String filter)
        throws InvalidSyntaxException
    {
        return FrameworkUtil.createFilter(filter);
    }

    public ServiceReference<?>[] getAllServiceReferences(
            String className,
            String filter)
        throws InvalidSyntaxException
    {
        return getServiceReferences(className, filter, false);
    }

    public BundleImpl getBundle()
    {
        return bundle;
    }

    public Bundle getBundle(long id)
    {
        return framework.getBundle(id);
    }

    public Bundle getBundle(String location)
    {
        // TODO Auto-generated method stub
        return null;
    }

    public Bundle[] getBundles()
    {
        // TODO Auto-generated method stub
        return null;
    }

    public File getDataFile(String filename)
    {
        // TODO Auto-generated method stub
        return null;
    }

    public String getProperty(String key)
    {
        // TODO Auto-generated method stub
        return null;
    }

    public Object getService(ServiceReference reference)
    {
        return
            ((ServiceRegistrationImpl.ServiceReferenceImpl) reference)
                .getService();
    }

    public ServiceReference getServiceReference(Class clazz)
    {
        return getServiceReference(clazz, clazz.getName());
    }

    private ServiceReference getServiceReference(
            Class clazz,
            String className)
    {
        ServiceReference[] serviceReferences;

        try
        {
            serviceReferences = getServiceReferences(className, null);
        }
        catch (InvalidSyntaxException ise)
        {
            // No InvlidSyntaxException is expected because the filter is null.
            serviceReferences = null;
        }

        return
            ((serviceReferences == null) || (serviceReferences.length == 0))
                ? null
                : serviceReferences[0];
    }

    public ServiceReference getServiceReference(String className)
    {
        return getServiceReference(Object.class, className);
    }

    public Collection<ServiceReference> getServiceReferences(
            Class clazz,
            String filter)
        throws InvalidSyntaxException
    {
        return getServiceReferences(clazz, clazz.getName(), filter, true);
    }

    private Collection<ServiceReference> getServiceReferences(
            Class clazz,
            String className,
            String filter,
            boolean checkAssignable)
        throws InvalidSyntaxException
    {
        return
            framework.getServiceReferences(
                    getBundle(),
                    clazz,
                    className,
                    (filter == null) ? null : createFilter(filter),
                    checkAssignable);
    }

    public ServiceReference[] getServiceReferences(
            String className,
            String filter)
        throws InvalidSyntaxException
    {
        return getServiceReferences(className, filter, true);
    }

    private ServiceReference[] getServiceReferences(
            String className,
            String filter,
            boolean checkAssignable)
        throws InvalidSyntaxException
    {
        Collection<ServiceReference> serviceReferences
            = getServiceReferences(
                    Object.class,
                    className,
                    filter,
                    checkAssignable);

        return
            serviceReferences.toArray(
                    new ServiceReference[serviceReferences.size()]);
    }

    public Bundle installBundle(String location)
        throws BundleException
    {
        return installBundle(location, null);
    }

    public Bundle installBundle(String location, InputStream input)
        throws BundleException
    {
        return framework.installBundle(getBundle(), location, input);
    }

    public <S> ServiceRegistration<S> registerService(
            Class<S> clazz,
            S service,
            Dictionary<String, ?> properties)
    {
        return
            registerService(
                    clazz,
                    new String[] { clazz.getName() }, service, properties);
    }

    private <S> ServiceRegistration<S> registerService(
            Class<S> clazz,
            String[] classNames,
            S service,
            Dictionary<String, ?> properties)
    {
        return
            framework.registerService(
                    getBundle(),
                    clazz,
                    classNames, service, properties);
    }

    public ServiceRegistration<?> registerService(
            String className,
            Object service,
            Dictionary<String, ?> properties)
    {
        return registerService(new String[] { className }, service, properties);
    }

    public ServiceRegistration<?> registerService(
            String[] classNames,
            Object service,
            Dictionary<String, ?> properties)
    {
        return registerService(Object.class, classNames, service, properties);
    }

    public void removeBundleListener(BundleListener listener)
    {
        framework.removeBundleListener(getBundle(), listener);
    }

    public void removeFrameworkListener(FrameworkListener listener)
    {
        // TODO Auto-generated method stub
    }

    public void removeServiceListener(ServiceListener listener)
    {
        framework.removeServiceListener(getBundle(), listener);
    }

    public boolean ungetService(ServiceReference<?> reference)
    {
        // TODO Auto-generated method stub
        return false;
    }
}
