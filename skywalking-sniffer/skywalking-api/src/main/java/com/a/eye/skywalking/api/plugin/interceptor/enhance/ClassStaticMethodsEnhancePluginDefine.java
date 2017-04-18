package com.a.eye.skywalking.api.plugin.interceptor.enhance;

import com.a.eye.skywalking.api.plugin.interceptor.ConstructorInterceptPoint;
import com.a.eye.skywalking.api.plugin.interceptor.InstanceMethodsInterceptPoint;

/**
 * Plugins, which only need enhance class static methods. Actually, inherit from {@link
 * ClassStaticMethodsEnhancePluginDefine} has no differences with inherit from {@link ClassEnhancePluginDefine}. Just
 * override {@link ClassEnhancePluginDefine#getConstructorsInterceptPoints} and {@link
 * ClassEnhancePluginDefine#getInstanceMethodsInterceptPoints}, and return {@link null}, which means nothing to
 * enhance.
 *
 * @author wusheng
 */
public abstract class ClassStaticMethodsEnhancePluginDefine extends ClassEnhancePluginDefine {

    /**
     * @return null, means enhance no constructors.
     */
    @Override
    protected ConstructorInterceptPoint[] getConstructorsInterceptPoints() {
        return null;
    }

    /**
     * @return null, means enhance no instance methods.
     */
    @Override
    protected InstanceMethodsInterceptPoint[] getInstanceMethodsInterceptPoints() {
        return null;
    }
}
