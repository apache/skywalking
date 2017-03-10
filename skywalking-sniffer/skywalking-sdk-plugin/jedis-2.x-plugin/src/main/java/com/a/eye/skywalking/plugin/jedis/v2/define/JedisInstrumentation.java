package com.a.eye.skywalking.plugin.jedis.v2.define;

import com.a.eye.skywalking.api.plugin.bytebuddy.AllObjectDefaultMethodsMatch;
import com.a.eye.skywalking.api.plugin.interceptor.ConstructorInterceptPoint;
import com.a.eye.skywalking.api.plugin.interceptor.InstanceMethodsInterceptPoint;
import com.a.eye.skywalking.api.plugin.interceptor.enhance.ClassInstanceMethodsEnhancePluginDefine;
import com.a.eye.skywalking.plugin.jedis.v2.JedisConstructorWithShardInfoArgInterceptor;
import com.a.eye.skywalking.plugin.jedis.v2.JedisConstructorWithUriArgInterceptor;
import com.a.eye.skywalking.plugin.jedis.v2.JedisMethodInterceptor;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;

import java.net.URI;

import static com.a.eye.skywalking.api.plugin.bytebuddy.ArgumentTypeNameMatch.takesArgumentWithType;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

/**
 * {@link JedisInstrumentation} presents that skywalking intercept all constructors and methods of {@link redis.clients.jedis.Jedis}.
 * {@link JedisConstructorWithShardInfoArgInterceptor} intercepts all constructor with argument {@link redis.clients.jedis.HostAndPort}
 * ,{@link JedisConstructorWithUriArgInterceptor} intercepts the constructors with uri argument and
 * the other constructor intercept by class {@link JedisConstructorWithShardInfoArgInterceptor}.
 * {@link JedisMethodInterceptor} intercept all methods of {@link redis.clients.jedis.Jedis}.
 *
 * @author zhangxin
 */
public class JedisInstrumentation extends ClassInstanceMethodsEnhancePluginDefine {

    private static final String HOST_AND_PROT_ARG_TYPE_NAME = "redis.clients.jedis.HostAndPort";
    private static final String ENHANCE_CLASS = "redis.clients.jedis.Jedis";
    private static final String CONSTRUCTOR_WITH_STRING_ARG_INTERCEPT_CLASS = "com.a.eye.skywalking.plugin.jedis.v2.JedisConstructorWithStringArgInterceptor";
    private static final String CONSTRUCTOR_WITH_SHARD_INFO_ARG_INTERCEPT_CLASS = "com.a.eye.skywalking.plugin.jedis.v2.JedisConstructorWithShardInfoArgInterceptor";
    private static final String CONSTRUCTOR_WITH_URI_ARG_INTERCEPT_CLASS = "com.a.eye.skywalking.plugin.jedis.v2.JedisConstructorWithUriArgInterceptor";
    private static final String JEDIS_METHOD_INTERCET_CLASS = "com.a.eye.skywalking.plugin.jedis.v2.JedisMethodInterceptor";

    @Override
    public String enhanceClassName() {
        return ENHANCE_CLASS;
    }

    @Override
    protected ConstructorInterceptPoint[] getConstructorsInterceptPoints() {
        return new ConstructorInterceptPoint[]{new ConstructorInterceptPoint() {
            @Override
            public ElementMatcher<MethodDescription> getConstructorMatcher() {
                return takesArgument(0, String.class);
            }

            @Override
            public String getConstructorInterceptor() {
                return CONSTRUCTOR_WITH_STRING_ARG_INTERCEPT_CLASS;
            }
        }, new ConstructorInterceptPoint() {
            @Override
            public ElementMatcher<MethodDescription> getConstructorMatcher() {
                return takesArgumentWithType(0, HOST_AND_PROT_ARG_TYPE_NAME);
            }

            @Override
            public String getConstructorInterceptor() {
                return CONSTRUCTOR_WITH_SHARD_INFO_ARG_INTERCEPT_CLASS;
            }
        }, new ConstructorInterceptPoint() {
            @Override
            public ElementMatcher<MethodDescription> getConstructorMatcher() {
                return takesArgument(0, URI.class);
            }

            @Override
            public String getConstructorInterceptor() {
                return CONSTRUCTOR_WITH_URI_ARG_INTERCEPT_CLASS;
            }
        }};
    }

    @Override
    protected InstanceMethodsInterceptPoint[] getInstanceMethodsInterceptPoints() {
        return new InstanceMethodsInterceptPoint[]{new InstanceMethodsInterceptPoint() {
            @Override
            public ElementMatcher<MethodDescription> getMethodsMatcher() {
                return not(ElementMatchers.<MethodDescription>isPrivate()
                        .or(AllObjectDefaultMethodsMatch.INSTANCE)
                        .or(named("close"))
                        .or(named("getDB"))
                        .or(named("connect"))
                        .or(named("setDataSource"))
                        .or(named("resetState"))
                        .or(named("clusterSlots"))
                        .or(named("checkIsInMultiOrPipeline")));
            }

            @Override
            public String getMethodsInterceptor() {
                return JEDIS_METHOD_INTERCET_CLASS;
            }
        }};
    }
}
