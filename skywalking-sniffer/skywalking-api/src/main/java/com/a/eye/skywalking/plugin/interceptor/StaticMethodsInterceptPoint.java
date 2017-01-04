package com.a.eye.skywalking.plugin.interceptor;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * One of the three "Intercept Point".
 * "Intercept Point" is a definition about where and how intercept happens.
 * In this "Intercept Point", the definition targets class's static methods, and the interceptor.
 *
 * ref to two others: {@link ConstructorInterceptPoint} and {@link InstanceMethodsInterceptPoint}
 *
 * Created by wusheng on 2016/11/29.
 */
public interface StaticMethodsInterceptPoint {
    /**
     * static methods matcher.
     *
     * @return matcher instance.
     */
    ElementMatcher<MethodDescription> getMethodsMatcher();

    /**
     * @return represents a class name, the class instance must instanceof {@link com.a.eye.skywalking.plugin.interceptor.enhance.StaticMethodsAroundInterceptor}.
     */
    String getMethodsInterceptor();
}
