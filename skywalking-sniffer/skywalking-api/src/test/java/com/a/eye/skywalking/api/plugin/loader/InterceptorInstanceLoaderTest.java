package com.a.eye.skywalking.api.plugin.loader;

import com.a.eye.skywalking.api.plugin.interceptor.loader.InterceptorInstanceLoader;
import java.lang.reflect.InvocationTargetException;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author wusheng
 */
public class InterceptorInstanceLoaderTest {
    @Test
    public void load() throws ClassNotFoundException, InstantiationException, IllegalAccessException, InvocationTargetException {
        ClassLoader mockClassLoader = new ClassLoader() {
            @Override public Class<?> loadClass(String name) throws ClassNotFoundException {
                return super.loadClass(name);
            }
        };
        Object obj = InterceptorInstanceLoader.load("com.a.eye.skywalking.api.plugin.loader.NeverUsedTestClass", mockClassLoader);
        Assert.assertTrue(obj != null);

        Object obj2 = InterceptorInstanceLoader.load("com.a.eye.skywalking.api.plugin.loader.NeverUsedTestClass", mockClassLoader);
        Assert.assertTrue(obj != null);
        Assert.assertEquals(obj, obj2);

        Object obj3 = InterceptorInstanceLoader.load("com.a.eye.skywalking.api.plugin.loader.NeverUsedTestClass", InterceptorInstanceLoaderTest.class.getClassLoader());
        Assert.assertTrue(obj3 != null);

        Object obj4 = InterceptorInstanceLoader.load("com.a.eye.skywalking.api.plugin.loader.NeverUsedTestClass", InterceptorInstanceLoaderTest.class.getClassLoader());
        Assert.assertTrue(obj4 != null);
        Assert.assertEquals(obj3, obj4);
    }
}
