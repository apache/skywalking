package org.slf4j;

import java.util.Map;
import org.slf4j.helpers.NOPMDCAdapter;
import org.slf4j.impl.StaticMDCBinder;
import org.slf4j.spi.MDCAdapter;

public class MDC
{
    static MDCAdapter mdcAdapter;

    public static MDCAdapter getInstance()
    {
        if (mdcAdapter == null) {
            synchronized (MDCAdapter.class) {
                if (mdcAdapter == null) {
                    mdcAdapter = StaticMDCBinder.SINGLETON.getMDCA();
                }
            }
        }
        return mdcAdapter;
    }

    public static void put(String key, String val)
            throws IllegalArgumentException
    {
        if (key == null) {
            throw new IllegalArgumentException("key parameter cannot be null");
        }
        if (getInstance() == null) {
            throw new IllegalStateException("MDCAdapter cannot be null.");
        }

        getInstance().put(key, val);
    }

    public static String get(String key)
            throws IllegalArgumentException
    {
        if (key == null) {
            throw new IllegalArgumentException("key parameter cannot be null");
        }
        if (getInstance() == null) {
            throw new IllegalStateException("MDCAdapter cannot be null.");
        }

        return getInstance().get(key);
    }

    public static void remove(String key)
            throws IllegalArgumentException
    {
        if (key == null) {
            throw new IllegalArgumentException("key parameter cannot be null");
        }

        if (getInstance() == null) {
            throw new IllegalStateException("MDCAdapter cannot be null.");
        }

        getInstance().remove(key);
    }

    public static void clear()
    {
        if (getInstance() == null) {
            throw new IllegalStateException("MDCAdapter cannot be null.");
        }

        getInstance().clear();
    }

    public static Map getCopyOfContextMap()
    {
        if (mdcAdapter == null) {
            throw new IllegalStateException("MDCAdapter cannot be null.");
        }

        return mdcAdapter.getCopyOfContextMap();
    }

    public static void setContextMap(Map contextMap)
    {
        if (mdcAdapter == null) {
            throw new IllegalStateException("MDCAdapter cannot be null.");
        }

        mdcAdapter.setContextMap(contextMap);
    }

    public static MDCAdapter getMDCAdapter()
    {
        return mdcAdapter;
    }

    static
    {
        try
        {
            mdcAdapter = StaticMDCBinder.SINGLETON.getMDCA();
        } catch (NoClassDefFoundError ncde) {
            mdcAdapter = new NOPMDCAdapter();
            String str = ncde.getMessage();
        }
        catch (Exception localException)
        {
        }
    }
}