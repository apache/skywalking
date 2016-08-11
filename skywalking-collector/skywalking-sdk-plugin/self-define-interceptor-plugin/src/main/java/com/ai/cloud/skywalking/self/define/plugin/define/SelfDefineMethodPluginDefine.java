package com.ai.cloud.skywalking.self.define.plugin.define;

import com.ai.cloud.skywalking.conf.Config;
import com.ai.cloud.skywalking.plugin.PluginException;
import com.ai.cloud.skywalking.plugin.interceptor.MethodMatcher;
import com.ai.cloud.skywalking.plugin.interceptor.enhance.ClassEnhancePluginDefine;
import com.ai.cloud.skywalking.plugin.interceptor.matcher.AnyMethodsMatcher;
import net.bytebuddy.dynamic.DynamicType;

public class SelfDefineMethodPluginDefine extends ClassEnhancePluginDefine {

    @Override
    protected DynamicType.Builder<?> enhance(String enhanceOriginClassName, DynamicType.Builder<?> newClassBuilder)
            throws PluginException {
        return Config.SkyWalking.SELF_DEFINE_METHOD_INTERCEPTOR ?
                super.enhance(enhanceOriginClassName, newClassBuilder) :
                newClassBuilder;
    }

    @Override
    protected MethodMatcher[] getInstanceMethodsMatchers() {
        return new MethodMatcher[] {new AnyMethodsMatcher()};
    }

    @Override
    protected String getInstanceMethodsInterceptor() {
        return "com.ai.cloud.skywalking.self.define.plugin.SelfDefineMethodInterceptor";
    }

    @Override
    protected MethodMatcher[] getStaticMethodsMatchers() {
        return new MethodMatcher[] {new AnyMethodsMatcher()};
    }

    @Override
    protected String getStaticMethodsInterceptor() {
        return "com.ai.cloud.skywalking.self.define.plugin.SelfDefineMethodInterceptor";
    }

    @Override
    protected String enhanceClassName() {
        if (!Config.SkyWalking.SELF_DEFINE_METHOD_PACKAGE.endsWith(".*")) {
            return Config.SkyWalking.SELF_DEFINE_METHOD_PACKAGE + ".*";
        }
        return Config.SkyWalking.SELF_DEFINE_METHOD_PACKAGE;
    }
}
