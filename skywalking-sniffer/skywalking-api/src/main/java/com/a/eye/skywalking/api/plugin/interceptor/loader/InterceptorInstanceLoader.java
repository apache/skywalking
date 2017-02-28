package com.a.eye.skywalking.api.plugin.interceptor.loader;

import com.a.eye.skywalking.api.logging.ILog;
import com.a.eye.skywalking.api.logging.LogManager;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.ProtectionDomain;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * The Classloader controller.
 * This is a very important class in sky-walking's auto-instrumentation mechanism.
 * If you want to fully understand why need this, and how it works, you need have knowledge about Classloader appointment mechanism.
 * <p>
 * The loader will load a class, and focus the target class loader (be intercepted class's classloader) loads it.
 * <p>
 * Created by wusheng on 16/8/2.
 */
public class InterceptorInstanceLoader {
    private static ILog logger = LogManager.getLogger(InterceptorInstanceLoader.class);

    private static ConcurrentHashMap<String, Object> INSTANCE_CACHE = new ConcurrentHashMap<>();

    private static ReentrantLock instanceLoadLock = new ReentrantLock();

    public static <T> T load(String className, ClassLoader targetClassLoader)
            throws InvocationTargetException, IllegalAccessException, InstantiationException, ClassNotFoundException {
        String instanceKey = className + "_OF_" + targetClassLoader.getClass().getName() + "@" + Integer.toHexString(targetClassLoader.hashCode());
        Object inst = INSTANCE_CACHE.get(instanceKey);
        if (inst != null) {
            return (T) inst;
        }

        if (InterceptorInstanceLoader.class.getClassLoader().equals(targetClassLoader)) {
            return (T) targetClassLoader.loadClass(className).newInstance();
        }

        instanceLoadLock.lock();
        try {
            try {
                inst = findLoadedClass(className, targetClassLoader);
                if (inst == null) {
                    inst = loadBinary(className, targetClassLoader);
                }
                if (inst == null) {
                    throw new ClassNotFoundException(targetClassLoader.toString() + " load interceptor class:" + className + " failure.");
                }
                INSTANCE_CACHE.put(instanceKey, inst);
                return (T) inst;
            } catch (Exception e) {
                throw new ClassNotFoundException(targetClassLoader.toString() + " load interceptor class:" + className + " failure.", e);
            }
        } finally {
            instanceLoadLock.unlock();
        }

    }

    /**
     * load class from class binary files.
     * Most likely all the interceptor implementations should be loaded by this.
     *
     * @param className         interceptor class name.
     * @param targetClassLoader the classloader, which should load the interceptor.
     * @param <T>
     * @return interceptor instance.
     * @throws InvocationTargetException
     * @throws IllegalAccessException
     * @throws InstantiationException
     */
    private static <T> T loadBinary(String className, ClassLoader targetClassLoader) throws InvocationTargetException, IllegalAccessException, InstantiationException {
        String path = "/" + className.replace('.', '/').concat(".class");
        byte[] data = null;
        BufferedInputStream is = null;
        ByteArrayOutputStream baos = null;
        try {
            logger.debug("Read binary code of {} using classload {}", className, InterceptorInstanceLoader.class.getClassLoader());
            is = new BufferedInputStream(InterceptorInstanceLoader.class.getResourceAsStream(path));
            baos = new ByteArrayOutputStream();
            int ch = 0;
            while ((ch = is.read()) != -1) {
                baos.write(ch);
            }
            data = baos.toByteArray();
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        } finally {
            if (is != null)
                try {
                    is.close();
                } catch (IOException ignored) {
                }
            if (baos != null)
                try {
                    baos.close();
                } catch (IOException ignored) {
                }
        }

        Method defineClassMethod = null;
        Class<?> targetClassLoaderType = targetClassLoader.getClass();
        while (defineClassMethod == null && targetClassLoaderType != null) {
            try {
                defineClassMethod = targetClassLoaderType.getDeclaredMethod("defineClass", String.class, byte[].class, int.class, int.class, ProtectionDomain.class);
            } catch (NoSuchMethodException e) {
                targetClassLoaderType = targetClassLoaderType.getSuperclass();
            }
        }
        defineClassMethod.setAccessible(true);
        logger.debug("load binary code of {} to classload {}", className, targetClassLoader);
        Class<?> type = (Class<?>) defineClassMethod.invoke(targetClassLoader, className, data, 0, data.length, null);
        return (T) type.newInstance();
    }

    /**
     * Find loaded class in the current classloader.
     * Just in case some classes have already been loaded for some reasons.s
     *
     * @param className         interceptor class name.
     * @param targetClassLoader the classloader, which should load the interceptor.
     * @param <T>
     * @return interceptor instance.
     */
    private static <T> T findLoadedClass(String className, ClassLoader targetClassLoader) throws InvocationTargetException, IllegalAccessException, InstantiationException {
        Method defineClassMethod = null;
        Class<?> targetClassLoaderType = targetClassLoader.getClass();
        while (defineClassMethod == null && targetClassLoaderType != null) {
            try {
                defineClassMethod = targetClassLoaderType.getDeclaredMethod("findLoadedClass", String.class);
            } catch (NoSuchMethodException e) {
                targetClassLoaderType = targetClassLoaderType.getSuperclass();
            }
        }
        defineClassMethod.setAccessible(true);
        Class<?> type = (Class<?>) defineClassMethod.invoke(targetClassLoader, className);
        if (type == null) {
            return null;
        }
        return (T) type.newInstance();
    }
}
