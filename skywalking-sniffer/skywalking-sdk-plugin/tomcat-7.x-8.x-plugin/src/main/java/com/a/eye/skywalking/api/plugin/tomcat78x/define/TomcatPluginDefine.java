package com.a.eye.skywalking.api.plugin.tomcat78x.define;

import com.a.eye.skywalking.plugin.interceptor.ConstructorInterceptPoint;
import com.a.eye.skywalking.api.plugin.interceptor.InstanceMethodsInterceptPoint;
import com.a.eye.skywalking.plugin.interceptor.enhance.ClassInstanceMethodsEnhancePluginDefine;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;

import static net.bytebuddy.matcher.ElementMatchers.named;

public class TomcatPluginDefine extends ClassInstanceMethodsEnhancePluginDefine {
    @Override
    protected String enhanceClassName() {
        return "org.apache.catalina.core.StandardEngineValve";
    }

    @Override
    protected ConstructorInterceptPoint[] getConstructorsInterceptPoints() {
        return null;
    }

    @Override
    protected InstanceMethodsInterceptPoint[] getInstanceMethodsInterceptPoints() {
        return new InstanceMethodsInterceptPoint[]{new InstanceMethodsInterceptPoint() {
            @Override
            public ElementMatcher<MethodDescription> getMethodsMatcher() {
                return named("invoke");
            }

            @Override
            public String getMethodsInterceptor() {
                return "com.a.eye.skywalking.plugin.tomcat78x.TomcatPluginInterceptor";
            }
        }};
    }
}
