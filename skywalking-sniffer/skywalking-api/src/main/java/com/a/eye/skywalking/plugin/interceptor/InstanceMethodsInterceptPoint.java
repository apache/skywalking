package com.a.eye.skywalking.plugin.interceptor;

/**
 * Created by wusheng on 2016/11/29.
 */
public interface InstanceMethodsInterceptPoint {
    /**
     * 返回需要被增强的方法列表
     *
     * @return
     */
     MethodMatcher[] getMethodsMatchers();

    /**
     *
     * @return represents a class name, the class instance must instanceof InstanceMethodsAroundInterceptor.
     */
    String getMethodsInterceptor();
}
