package com.a.eye.skywalking.plugin.interceptor.enhance;

import com.a.eye.skywalking.plugin.interceptor.StaticMethodsInterceptPoint;

/**
 * Plugins, which only need enhance class static methods.
 * Actually, inherit from {@link ClassInstanceMethodsEnhancePluginDefine} has no differences with inherit from {@link ClassEnhancePluginDefine}.
 * Just override {@link ClassEnhancePluginDefine#getStaticMethodsInterceptPoints},
 * and return {@link null}, which means nothing to enhance.
 *
 * @author wusheng
 */
public abstract class ClassInstanceMethodsEnhancePluginDefine extends ClassEnhancePluginDefine {

    /**
     * @return null, means enhance no static methods.
     */
    @Override
    protected StaticMethodsInterceptPoint[] getStaticMethodsInterceptPoints() {
        return null;
    }

}
