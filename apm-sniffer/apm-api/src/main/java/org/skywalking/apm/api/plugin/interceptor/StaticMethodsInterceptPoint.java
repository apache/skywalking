package org.skywalking.apm.api.plugin.interceptor;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * One of the three "Intercept Point".
 * "Intercept Point" is a definition about where and how intercept happens.
 * In this "Intercept Point", the definition targets class's static methods, and the interceptor.
 * <p>
 * ref to two others: {@link ConstructorInterceptPoint} and {@link InstanceMethodsInterceptPoint}
 * <p>
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
     * @return represents a class name, the class instance must instanceof {@link org.skywalking.apm.plugin.interceptor.enhance.StaticMethodsAroundInterceptor}.
     */
    String getMethodsInterceptor();
}
