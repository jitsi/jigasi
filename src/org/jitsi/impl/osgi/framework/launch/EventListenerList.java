/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.osgi.framework.launch;

import org.osgi.framework.*;

import java.lang.reflect.*;
import java.util.*;

/**
 *
 * @author Lyubomir Marinov
 */
public class EventListenerList
{
    private final List<Element<?>> elements = new LinkedList<Element<?>>();

    public synchronized <T extends EventListener> boolean add(
            Bundle bundle,
            Class<T> clazz,
            T listener)
    {
        if (bundle == null)
            throw new NullPointerException("bundle");
        if (clazz == null)
            throw new NullPointerException("clazz");
        if (listener == null)
            throw new NullPointerException("listener");

        int index = indexOf(bundle, clazz, listener);

        if (index == -1)
            return elements.add(new Element<T>(bundle, clazz, listener));
        else
            return false;
    }

    public synchronized <T extends EventListener> T[] getListeners(
            Class<T> clazz)
    {
        EventListener[] eventListeners = new EventListener[elements.size()];
        int count = 0;

        for (Element<?> element : elements)
                if (element.clazz == clazz)
                    eventListeners[count++] = element.listener;

        @SuppressWarnings("unchecked")
        T[] listeners = (T[]) Array.newInstance(clazz, count);

        System.arraycopy(eventListeners, 0, listeners, 0, count);
        return listeners;
    }

    private synchronized <T extends EventListener> int indexOf(
            Bundle bundle,
            Class<T> clazz,
            T listener)
    {
        for (int index = 0, count = elements.size(); index < count; index++)
        {
            Element<?> element = elements.get(index);

            if (element.bundle.equals(bundle)
                    && (element.clazz == clazz)
                    && (element.listener == listener))
                return index;
        }
        return -1;
    }

    public synchronized <T extends EventListener> boolean remove(
            Bundle bundle,
            Class<T> clazz,
            T listener)
    {
        int index = indexOf(bundle, clazz, listener);

        if (index == -1)
            return false;
        else
        {
            elements.remove(index);
            return true;
        }
    }

    public synchronized boolean removeAll(Bundle bundle)
    {
        boolean changed = false;

        for (int index = 0, count = elements.size(); index < count;)
        {
            if (elements.get(index).bundle.equals(bundle)
                    && (elements.remove(index) != null))
                changed = true;
            else
                index++;
        }

        return changed;
    }

    private static class Element<T extends EventListener>
    {
        public final Bundle bundle;

        public final Class<T> clazz;

        public final T listener;

        public Element(Bundle bundle, Class<T> clazz, T listener)
        {
            this.bundle = bundle;
            this.clazz = clazz;
            this.listener = listener;
        }
    }
}
