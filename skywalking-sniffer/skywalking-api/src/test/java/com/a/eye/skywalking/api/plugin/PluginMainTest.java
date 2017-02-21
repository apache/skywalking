package com.a.eye.skywalking.api.plugin;

import org.junit.Test;

import java.lang.reflect.InvocationTargetException;

public class PluginMainTest {
    @Test
    public void testMain() throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException, PluginException {
        TracingBootstrap.main(new String[] {"PluginMainTest"});
    }

    public static void main(String[] args)
            throws InstantiationException, IllegalAccessException, ClassNotFoundException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException,
            SecurityException {
        long start = System.currentTimeMillis();

        BeInterceptedClass inst = (BeInterceptedClass) Class.forName("BeInterceptedClass").newInstance();
        inst.printabc();
        long end = System.currentTimeMillis();
        System.out.println(end - start + "ms");

        BeInterceptedClass.call();
    }
}
