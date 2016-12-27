package com.a.eye.skywalking.plugin.rmi.oracle18.define;

import com.a.eye.skywalking.plugin.interceptor.ConstructorInterceptPoint;
import com.a.eye.skywalking.plugin.interceptor.InstanceMethodsInterceptPoint;
import com.a.eye.skywalking.plugin.interceptor.MethodMatcher;
import com.a.eye.skywalking.plugin.interceptor.enhance.ClassInstanceMethodsEnhancePluginDefine;
import com.a.eye.skywalking.plugin.interceptor.matcher.SimpleMethodMatcher;

/**
 * Created by xin on 2016/12/22.
 */
public class RMIClientDefine extends ClassInstanceMethodsEnhancePluginDefine {

    @Override
    protected String enhanceClassName() {
        return "java.rmi.serve.RemoteObjectInvocationHandler";
    }

    @Override
    protected ConstructorInterceptPoint[] getConstructorsInterceptPoints() {
        return new ConstructorInterceptPoint[0];
    }

    @Override
    protected InstanceMethodsInterceptPoint[] getInstanceMethodsInterceptPoints() {
        return new InstanceMethodsInterceptPoint[]{
                new InstanceMethodsInterceptPoint() {
                    @Override
                    public MethodMatcher[] getMethodsMatchers() {
                        return new MethodMatcher[]{
                                new SimpleMethodMatcher("invokeRemoteMethod")
                        };
                    }

                    @Override
                    public String getMethodsInterceptor() {
                        return "com.a.eye.skywalking.plugin.rmi.oracle18.RMIClientInterceptor";
                    }
                }
        };
    }
}
