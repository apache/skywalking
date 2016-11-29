package com.a.eye.skywalking.plugin.motan.define;

import com.a.eye.skywalking.plugin.interceptor.ConstructorInterceptPoint;
import com.a.eye.skywalking.plugin.interceptor.InstanceMethodsInterceptPoint;
import com.a.eye.skywalking.plugin.interceptor.MethodMatcher;
import com.a.eye.skywalking.plugin.interceptor.enhance.ClassInstanceMethodsEnhancePluginDefine;
import com.a.eye.skywalking.plugin.interceptor.matcher.SimpleMethodMatcher;

public class MotanServerDefine extends ClassInstanceMethodsEnhancePluginDefine {

    @Override
    protected String enhanceClassName() {
        return "com.weibo.api.motan.rpc.AbstractProvider";
    }

    @Override
    protected ConstructorInterceptPoint getConstructorsInterceptPoint() {
        return new ConstructorInterceptPoint() {
            @Override
            public String getConstructorInterceptor() {
                return "com.a.eye.skywalking.plugin.motan.MotanServerInterceptor";
            }
        };
    }

    @Override
    protected InstanceMethodsInterceptPoint[] getInstanceMethodsInterceptPoints() {
        return new InstanceMethodsInterceptPoint[]{new InstanceMethodsInterceptPoint() {
            @Override
            public MethodMatcher[] getMethodsMatchers() {
                return new MethodMatcher[] {new SimpleMethodMatcher("call")};
            }

            @Override
            public String getMethodsInterceptor() {
                return "com.a.eye.skywalking.plugin.motan.MotanServerInterceptor";
            }
        }};
    }
}
