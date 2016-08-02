package com.ai.cloud.skywalking.plugin.dubbo;

import com.ai.cloud.skywalking.plugin.interceptor.MethodMatcher;
import com.ai.cloud.skywalking.plugin.interceptor.enhance.ClassStaticMethodsEnhancePluginDefine;
import com.ai.cloud.skywalking.plugin.interceptor.enhance.StaticMethodsAroundInterceptor;
import com.ai.cloud.skywalking.plugin.interceptor.matcher.SimpleMethodMatcher;

public class DubboPluginDefine extends ClassStaticMethodsEnhancePluginDefine {
    @Override
    protected MethodMatcher[] getStaticMethodsMatchers() {
        return new MethodMatcher[]{new SimpleMethodMatcher("buildInvokerChain")};
    }

    @Override
    protected String getStaticMethodsInterceptor() {
        return "com.ai.cloud.skywalking.plugin.dubbo.ProtocolFilterBuildChainInterceptor";
    }

    @Override
    protected String enhanceClassName() {
        return "com.alibaba.dubbo.rpc.protocol.ProtocolFilterWrapper";
    }
}
