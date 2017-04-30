package org.skywalking.apm.agent.core.plugin.loader;

import org.junit.Assert;
import org.junit.Test;
import org.skywalking.apm.agent.core.plugin.interceptor.loader.InterceptorInstanceLoader;

import java.lang.reflect.InvocationTargetException;

/**
 * @author wusheng
 */
public class InterceptorInstanceLoaderTest {
    @Test
    public void load() throws ClassNotFoundException, InstantiationException, IllegalAccessException, InvocationTargetException {
        ClassLoader mockClassLoader = new ClassLoader() {
            @Override
            public Class<?> loadClass(String name) throws ClassNotFoundException {
                return super.loadClass(name);
            }
        };
        Object obj = InterceptorInstanceLoader.load("org.skywalking.apm.agent.core.plugin.loader.NeverUsedTestClass", mockClassLoader);
        Assert.assertTrue(obj != null);

        Object obj2 = InterceptorInstanceLoader.load("org.skywalking.apm.agent.core.plugin.loader.NeverUsedTestClass", mockClassLoader);
        Assert.assertTrue(obj != null);
        Assert.assertEquals(obj, obj2);

        Object obj3 = InterceptorInstanceLoader.load("org.skywalking.apm.agent.core.plugin.loader.NeverUsedTestClass", InterceptorInstanceLoaderTest.class.getClassLoader());
        Assert.assertTrue(obj3 != null);

        Object obj4 = InterceptorInstanceLoader.load("org.skywalking.apm.agent.core.plugin.loader.NeverUsedTestClass", InterceptorInstanceLoaderTest.class.getClassLoader());
        Assert.assertTrue(obj4 != null);
        Assert.assertEquals(obj3, obj4);
    }
}
