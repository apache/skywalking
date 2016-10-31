package com.a.eye.skywalking.plugin.interceptor.loader;

import com.a.eye.skywalking.logging.LogManager;
import com.a.eye.skywalking.logging.Logger;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.ProtectionDomain;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by wusheng on 16/8/2.
 */
public class InterceptorInstanceLoader {
    private static Logger logger = LogManager.getLogger(InterceptorInstanceLoader.class);

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
     * 通过二进制读取,直接加载类文件,然后通过上下文所需的classLoader强制加载
     *
     * @param className
     * @param targetClassLoader
     * @param <T>
     * @return
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
     * 在当前classloader中查找是否已经加载此类。
     *
     * @param className
     * @param targetClassLoader
     * @param <T>
     * @return
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
