package com.a.eye.skywalking.api.plugin.tomcat78x.define;

import com.a.eye.skywalking.plugin.interceptor.ConstructorInterceptPoint;
import com.a.eye.skywalking.api.plugin.interceptor.InstanceMethodsInterceptPoint;
import com.a.eye.skywalking.plugin.interceptor.enhance.ClassInstanceMethodsEnhancePluginDefine;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;

import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;

import static net.bytebuddy.matcher.ElementMatchers.named;

/**
 * {@link TomcatPluginDefine} present define of tomcat plugin
 *
 * Tomcat plugin weave class {@link org.apache.catalina.core.StandardEngineValve#invoke(Request, Response)}.
 *
 * @author zhangxin
 */
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
