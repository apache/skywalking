package com.a.eye.skywalking.api.plugin.motan.define;

import com.a.eye.skywalking.api.plugin.interceptor.ConstructorInterceptPoint;
import com.a.eye.skywalking.api.plugin.interceptor.InstanceMethodsInterceptPoint;
import com.a.eye.skywalking.plugin.interceptor.ConstructorInterceptPoint;
import com.a.eye.skywalking.plugin.interceptor.enhance.ClassInstanceMethodsEnhancePluginDefine;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;

import static net.bytebuddy.matcher.ElementMatchers.any;
import static net.bytebuddy.matcher.ElementMatchers.named;

public class MotanServerDefine extends ClassInstanceMethodsEnhancePluginDefine {

    @Override
    protected String enhanceClassName() {
        return "com.weibo.api.motan.rpc.AbstractProvider";
    }

    @Override
    protected ConstructorInterceptPoint[] getConstructorsInterceptPoints() {
        return new ConstructorInterceptPoint[] {new ConstructorInterceptPoint() {
            @Override
            public ElementMatcher<MethodDescription> getConstructorMatcher() {
                return any();
            }

            @Override
            public String getConstructorInterceptor() {
                return "com.a.eye.skywalking.plugin.motan.MotanServerInterceptor";
            }
        }};
    }

    @Override
    protected InstanceMethodsInterceptPoint[] getInstanceMethodsInterceptPoints() {
        return new InstanceMethodsInterceptPoint[] {new InstanceMethodsInterceptPoint() {
            @Override
            public ElementMatcher<MethodDescription> getMethodsMatcher() {
                return named("call");
            }

            @Override
            public String getMethodsInterceptor() {
                return "com.a.eye.skywalking.plugin.motan.MotanServerInterceptor";
            }
        }};
    }
}
