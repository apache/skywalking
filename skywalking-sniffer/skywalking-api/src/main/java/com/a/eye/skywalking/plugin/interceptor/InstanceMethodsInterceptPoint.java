package com.a.eye.skywalking.plugin.interceptor;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * Created by wusheng on 2016/11/29.
 */
public interface InstanceMethodsInterceptPoint {
    /**
     * 返回需要被增强的方法列表
     *
     * @return
     */
    ElementMatcher<MethodDescription> getMethodsMatcher();

    /**
     *
     * @return represents a class name, the class instance must instanceof InstanceMethodsAroundInterceptor.
     */
    String getMethodsInterceptor();
}
