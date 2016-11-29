package com.a.eye.skywalking.plugin.interceptor;

/**
 * Created by wusheng on 2016/11/29.
 */
public interface ConstructorInterceptPoint{
    /**
     *
     * @return represents a class name, the class instance must instanceof InstanceConstructorInterceptor.
     */
    String getConstructorInterceptor();
}
