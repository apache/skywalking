package com.a.eye.skywalking.plugin.httpClient.v4.define;

import com.a.eye.skywalking.plugin.interceptor.InstanceMethodsInterceptPoint;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;

import static net.bytebuddy.matcher.ElementMatchers.named;

public class DefaultRequestDirectorPluginDefine extends HttpClientPluginDefine {
    /**
     * DefaultRequestDirector is default implement.<br/>
     * usually use in version 4.0-4.2<br/>
     * since 4.3, this class is Deprecated.
     */
    @Override
    public String enhanceClassName() {
        return "org.apache.http.impl.client.DefaultRequestDirector";
    }

    @Override
    protected InstanceMethodsInterceptPoint[] getInstanceMethodsInterceptPoints() {
        return new InstanceMethodsInterceptPoint[] {new InstanceMethodsInterceptPoint() {
            @Override
            public ElementMatcher<MethodDescription> getMethodsMatcher() {
                return named("execute");
            }

            @Override
            public String getMethodsInterceptor() {
                return getInstanceMethodsInterceptor();
            }
        }};
    }
}
