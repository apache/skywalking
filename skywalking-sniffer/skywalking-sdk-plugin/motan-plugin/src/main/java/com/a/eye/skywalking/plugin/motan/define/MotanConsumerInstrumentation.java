package com.a.eye.skywalking.plugin.motan.define;

import com.a.eye.skywalking.api.plugin.interceptor.ConstructorInterceptPoint;
import com.a.eye.skywalking.api.plugin.interceptor.InstanceMethodsInterceptPoint;
import com.a.eye.skywalking.api.plugin.interceptor.enhance.ClassInstanceMethodsEnhancePluginDefine;
import com.a.eye.skywalking.plugin.motan.MotanConsumerFetchRequestURLInterceptor;
import com.a.eye.skywalking.plugin.motan.MotanConsumerInvokeInterceptor;
import com.weibo.api.motan.rpc.Request;
import com.weibo.api.motan.rpc.URL;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;

import static net.bytebuddy.matcher.ElementMatchers.named;

/**
 * {@link MotanConsumerInstrumentation} presents that skywalking intercept
 * {@link com.weibo.api.motan.cluster.support.ClusterSpi#call(Request)} by using {@link MotanConsumerInvokeInterceptor} and
 * intercept {@link com.weibo.api.motan.cluster.support.ClusterSpi#setUrl(URL)} by using
 * {@link MotanConsumerFetchRequestURLInterceptor} to intercept{@link MotanConsumerFetchRequestURLInterceptor}.
 *
 * @author zhangxin
 */
public class MotanConsumerInstrumentation extends ClassInstanceMethodsEnhancePluginDefine {

    private static final String ENHANCE_CLASS = "com.weibo.api.motan.cluster.support.ClusterSpi";

    private static final String FETCH_REQUEST_URL_INTERCEPT_CLASS = "com.a.eye.skywalking.plugin.motan.MotanConsumerFetchRequestURLInterceptor";

    private static final String INVOKE_INTERCEPT_CLASS = "com.a.eye.skywalking.plugin.motan.MotanConsumerInvokeInterceptor";

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
                return INVOKE_INTERCEPT_CLASS;
            }
        }};
    }
}
