package com.a.eye.skywalking.api.plugin.motan.define;

import com.a.eye.skywalking.api.plugin.interceptor.InstanceMethodsInterceptPoint;
import com.a.eye.skywalking.plugin.interceptor.ConstructorInterceptPoint;
import com.a.eye.skywalking.plugin.interceptor.enhance.ClassInstanceMethodsEnhancePluginDefine;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;

import static net.bytebuddy.matcher.ElementMatchers.named;

public class MotanClientDefine extends ClassInstanceMethodsEnhancePluginDefine {
    @Override
    protected String enhanceClassName() {
        return "com.weibo.api.motan.cluster.support.ClusterSpi";
    }

    @Override
    protected ConstructorInterceptPoint[] getConstructorsInterceptPoints() {
        return new ConstructorInterceptPoint[0];
    }

    @Override
    protected InstanceMethodsInterceptPoint[] getInstanceMethodsInterceptPoints() {
        return new InstanceMethodsInterceptPoint[]{new InstanceMethodsInterceptPoint() {
            @Override
            public ElementMatcher<MethodDescription> getMethodsMatcher() {
                return named("setUrl");
            }

            @Override
            public String getMethodsInterceptor() {
                return "com.a.eye.skywalking.plugin.motan.MotanClientFetchCallURLInterceptor";
            }
        }, new InstanceMethodsInterceptPoint() {
            @Override
            public ElementMatcher<MethodDescription> getMethodsMatcher() {
                return named("call");
            }

            @Override
            public String getMethodsInterceptor() {
                return "com.a.eye.skywalking.plugin.motan.MotanClientCallInterceptor";
            }
        }};
    }
}
