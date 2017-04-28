package org.skywalking.apm.plugin.jedis.v2.define;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.skywalking.apm.api.plugin.bytebuddy.AllObjectDefaultMethodsMatch;
import org.skywalking.apm.api.plugin.interceptor.ConstructorInterceptPoint;
import org.skywalking.apm.api.plugin.interceptor.InstanceMethodsInterceptPoint;
import org.skywalking.apm.api.plugin.interceptor.enhance.ClassInstanceMethodsEnhancePluginDefine;
import org.skywalking.apm.plugin.jedis.v2.JedisClusterConstructorWithHostAndPortArgInterceptor;
import org.skywalking.apm.plugin.jedis.v2.JedisClusterConstructorWithListHostAndPortArgInterceptor;
import org.skywalking.apm.plugin.jedis.v2.JedisMethodInterceptor;

import java.util.Set;

import static net.bytebuddy.matcher.ElementMatchers.*;
import static org.skywalking.apm.api.plugin.bytebuddy.ArgumentTypeNameMatch.takesArgumentWithType;

/**
 * {@link JedisClusterInstrumentation} presents that skywalking intercepts all constructors and methods of {@link
 * redis.clients.jedis.JedisCluster}. {@link JedisClusterConstructorWithHostAndPortArgInterceptor}
 * intercepts all constructor with argument {@link redis.clients.jedis.HostAndPort} and the other constructor intercept
 * by class {@link JedisClusterConstructorWithListHostAndPortArgInterceptor}. {@link JedisMethodInterceptor} intercept
 * all methods of {@link redis.clients.jedis.JedisCluster}
 *
 * @author zhangxin
 */
public class JedisClusterInstrumentation extends ClassInstanceMethodsEnhancePluginDefine {

    private static final String ARGUMENT_TYPE_NAME = "redis.clients.jedis.HostAndPort";
    private static final String ENHANCE_CLASS = "redis.clients.jedis.JedisCluster";
    private static final String CONSTRUCTOR_WITH_LIST_HOSTANDPORT_ARG_INTERCEPT_CLASS = "org.skywalking.apm.plugin.jedis.v2.JedisClusterConstructorWithListHostAndPortArgInterceptor";
    private static final String METHOD_INTERCEPT_CLASS = "org.skywalking.apm.plugin.jedis.v2.JedisMethodInterceptor";
    private static final String CONSTRUCTOR_WITH_HOSTANDPORT_ARG_INTERCEPT_CLASS = "org.skywalking.apm.plugin.jedis.v2.JedisClusterConstructorWithHostAndPortArgInterceptor";

    @Override
    public String enhanceClassName() {
        return ENHANCE_CLASS;
    }

    @Override
    protected ConstructorInterceptPoint[] getConstructorsInterceptPoints() {
        return new ConstructorInterceptPoint[] {
            new ConstructorInterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getConstructorMatcher() {
                    return takesArgument(0, Set.class);
                }

                @Override
                public String getConstructorInterceptor() {
                    return CONSTRUCTOR_WITH_LIST_HOSTANDPORT_ARG_INTERCEPT_CLASS;
                }
            },
            new ConstructorInterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getConstructorMatcher() {
                    return takesArgumentWithType(0, ARGUMENT_TYPE_NAME);
                }

                @Override
                public String getConstructorInterceptor() {
                    return CONSTRUCTOR_WITH_HOSTANDPORT_ARG_INTERCEPT_CLASS;
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
                    return any().and(not(AllObjectDefaultMethodsMatch.INSTANCE));
                }

                @Override
                public String getMethodsInterceptor() {
                    return METHOD_INTERCEPT_CLASS;
                }
            }
        };
    }
}
