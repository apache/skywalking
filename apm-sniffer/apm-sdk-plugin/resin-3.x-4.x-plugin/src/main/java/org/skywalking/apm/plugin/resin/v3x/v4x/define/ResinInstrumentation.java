package org.skywalking.apm.plugin.resin.v3x.v4x.define;

import static net.bytebuddy.matcher.ElementMatchers.named;

import org.skywalking.apm.api.plugin.interceptor.ConstructorInterceptPoint;
import org.skywalking.apm.api.plugin.interceptor.InstanceMethodsInterceptPoint;
import org.skywalking.apm.api.plugin.interceptor.enhance.ClassInstanceMethodsEnhancePluginDefine;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * {@link ResinInstrumentation} presents that skywalking intercepts {@link com.caucho.server.dispatch.ServletInvocation#service(javax.servlet.ServletRequest,
 * javax.servlet.ServletResponse)} by using {@link ResinInterceptor}.
 *
 * @author baiyang
 */
public class ResinInstrumentation extends ClassInstanceMethodsEnhancePluginDefine {

    private static final String ENHANCE_CLASS = "com.caucho.server.dispatch.ServletInvocation";

    private static final String METHOD_INTERCET_CLASS = "org.skywalking.apm.plugin.resin.v3x.v4x.ResinInterceptor";

    @Override
    protected ConstructorInterceptPoint[] getConstructorsInterceptPoints() {
        return null;
    }

    @Override
    protected InstanceMethodsInterceptPoint[] getInstanceMethodsInterceptPoints() {
        return new InstanceMethodsInterceptPoint[] {
            new InstanceMethodsInterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getMethodsMatcher() {
                    return named("service");
                }

                @Override
                public String getMethodsInterceptor() {
                    return METHOD_INTERCET_CLASS;
                }
            }
        };
    }

    @Override
    protected String enhanceClassName() {
        return ENHANCE_CLASS;
    }

}
