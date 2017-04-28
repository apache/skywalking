package org.skywalking.apm.plugin.motan.define;

import com.weibo.api.motan.rpc.Request;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.skywalking.apm.api.plugin.interceptor.ConstructorInterceptPoint;
import org.skywalking.apm.api.plugin.interceptor.InstanceMethodsInterceptPoint;
import org.skywalking.apm.api.plugin.interceptor.enhance.ClassInstanceMethodsEnhancePluginDefine;
import org.skywalking.apm.plugin.motan.MotanConsumerInterceptor;

import static net.bytebuddy.matcher.ElementMatchers.any;
import static net.bytebuddy.matcher.ElementMatchers.named;

/**
 * {@link MotanProviderInstrumentation} presents that skywalking will use
 * {@link MotanConsumerInterceptor} to intercept
 * all constructor of {@link com.weibo.api.motan.rpc.AbstractProvider} and
 * {@link com.weibo.api.motan.rpc.AbstractProvider#call(Request)}.
 *
 * @author zhangxin
 */
public class MotanProviderInstrumentation extends ClassInstanceMethodsEnhancePluginDefine {

    /**
     * Enhance class.
     */
    private static final String ENHANCE_CLASS = "com.weibo.api.motan.rpc.AbstractReferer";
    /**
     * Class that intercept all constructor of ${@link com.weibo.api.motan.rpc.AbstractProvider}.
     */
    private static final String CONSTRUCTOR_INTERCEPT_CLASS = "org.skywalking.apm.plugin.motan.MotanConsumerInterceptor";
    /**
     * Class that intercept {@link com.weibo.api.motan.rpc.AbstractProvider#call(Request)}.
     */
    private static final String PROVIDER_INVOKE_INTERCEPT_CLASS = "org.skywalking.apm.plugin.motan.MotanConsumerInterceptor";

    @Override
    protected String enhanceClassName() {
        return ENHANCE_CLASS;
    }

    @Override
    protected ConstructorInterceptPoint[] getConstructorsInterceptPoints() {
        return new ConstructorInterceptPoint[] {
            new ConstructorInterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getConstructorMatcher() {
                    return any();
                }

                @Override
                public String getConstructorInterceptor() {
                    return CONSTRUCTOR_INTERCEPT_CLASS;
                }
            }
        };
    }

    @Override
    protected InstanceMethodsInterceptPoint[] getInstanceMethodsInterceptPoints() {
        return new InstanceMethodsInterceptPoint[] {
            new InstanceMethodsInterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getMethodsMatcher() {
                    return named("call");
                }

                @Override
                public String getMethodsInterceptor() {
                    return PROVIDER_INVOKE_INTERCEPT_CLASS;
                }
            }
        };
    }
}
