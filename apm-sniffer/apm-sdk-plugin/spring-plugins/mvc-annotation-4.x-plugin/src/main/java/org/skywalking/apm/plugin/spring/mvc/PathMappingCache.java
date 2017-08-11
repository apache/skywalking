package org.skywalking.apm.plugin.spring.mvc;

import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The <code>PathMappingCache</code> represents a field
 *
 *
 * @author wusheng
 */
public class PathMappingCache {
    private String classPath = "";

    private ConcurrentHashMap<Method, String> methodPathMapping = new ConcurrentHashMap<Method, String>();

    public PathMappingCache(String classPath) {
        this.classPath = classPath;
    }

    public String findPathMapping(Method method) {
        return methodPathMapping.get(method);
    }

    public void addPathMapping(Method method, String methodPath) {
        methodPathMapping.put(method, classPath + methodPath);
    }
}
