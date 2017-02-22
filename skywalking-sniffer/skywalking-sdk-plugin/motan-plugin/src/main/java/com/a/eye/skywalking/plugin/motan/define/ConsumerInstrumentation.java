package com.a.eye.skywalking.plugin.motan.define;

import com.a.eye.skywalking.api.plugin.interceptor.ConstructorInterceptPoint;
import com.a.eye.skywalking.api.plugin.interceptor.InstanceMethodsInterceptPoint;
import com.a.eye.skywalking.api.plugin.interceptor.enhance.ClassInstanceMethodsEnhancePluginDefine;
import com.a.eye.skywalking.plugin.motan.ConsumerFetchRequestURLInterceptor;
import com.a.eye.skywalking.plugin.motan.ConsumerInvokeInterceptor;
import com.weibo.api.motan.rpc.Request;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;

import static net.bytebuddy.matcher.ElementMatchers.named;

/**
 * {@link ConsumerInstrumentation} presents that skywalking use {@link ConsumerInvokeInterceptor}
 * to intercept {@link com.weibo.api.motan.cluster.support.ClusterSpi#call(Request)} and use {@link ConsumerFetchRequestURLInterceptor}
 * to intercept{@link ConsumerFetchRequestURLInterceptor}
 */
public class ConsumerInstrumentation extends ClassInstanceMethodsEnhancePluginDefine {

    private static final String ENHANCE_CLASS = "com.weibo.api.motan.cluster.support.ClusterSpi";
    private static final String FETCH_REQUEST_URL_INTERCEPT_CLASS = "com.a.eye.skywalking.plugin.motan.ConsumerFetchRequestURLInterceptor";
    private static final String INVOKE_INTECEPT_CLASS = "com.a.eye.skywalking.plugin.motan.ConsumerInvokeInterceptor";

    @Override
    protected String enhanceClassName() {
        return ENHANCE_CLASS;
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
                return FETCH_REQUEST_URL_INTERCEPT_CLASS;
            }
        }, new InstanceMethodsInterceptPoint() {
            @Override
            public ElementMatcher<MethodDescription> getMethodsMatcher() {
                return named("call");
            }

            @Override
            public String getMethodsInterceptor() {
                return INVOKE_INTECEPT_CLASS;
            }
        }};
    }
}
