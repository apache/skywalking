package com.a.eye.skywalking.plugin.interceptor;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * Created by wusheng on 2016/11/29.
 */
public interface ConstructorInterceptPoint{
    ElementMatcher.Junction<MethodDescription> getConstructorMatcher();

    /**
     *
     * @return represents a class name, the class instance must instanceof InstanceConstructorInterceptor.
     */
    String getConstructorInterceptor();
}
