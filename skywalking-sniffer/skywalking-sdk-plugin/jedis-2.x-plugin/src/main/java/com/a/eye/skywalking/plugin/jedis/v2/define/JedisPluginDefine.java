package com.a.eye.skywalking.plugin.jedis.v2.define;

import com.a.eye.skywalking.plugin.bytebuddy.ArgumentTypeNameMatch;
import com.a.eye.skywalking.plugin.interceptor.ConstructorInterceptPoint;
import com.a.eye.skywalking.plugin.interceptor.InstanceMethodsInterceptPoint;
import com.a.eye.skywalking.plugin.interceptor.MethodMatcher;
import com.a.eye.skywalking.plugin.interceptor.enhance.ClassInstanceMethodsEnhancePluginDefine;
import com.a.eye.skywalking.plugin.interceptor.matcher.MethodsExclusiveMatcher;
import com.a.eye.skywalking.plugin.interceptor.matcher.PrivateMethodMatcher;
import com.a.eye.skywalking.plugin.interceptor.matcher.SimpleMethodMatcher;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;

import java.net.URI;

import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

public class JedisPluginDefine extends ClassInstanceMethodsEnhancePluginDefine {

    @Override
    public String enhanceClassName() {
        return "redis.clients.jedis.Jedis";
    }

    @Override
    protected ConstructorInterceptPoint[] getConstructorsInterceptPoints() {
        return new ConstructorInterceptPoint[] {new ConstructorInterceptPoint() {
            @Override
            public ElementMatcher<MethodDescription> getConstructorMatcher() {
                return takesArgument(0, String.class);
            }

            @Override
            public String getConstructorInterceptor() {
                return "com.a.eye.skywalking.plugin.jedis.v2.JedisConstructorInterceptor4StringArg";
            }
        }, new ConstructorInterceptPoint() {
            @Override
            public ElementMatcher<MethodDescription> getConstructorMatcher() {
                return new ArgumentTypeNameMatch(0, "redis.clients.jedis.HostAndPort");
            }

            @Override
            public String getConstructorInterceptor() {
                return "com.a.eye.skywalking.plugin.jedis.v2.JedisConstructorInterceptor4ShardInfoArgg";
            }
        }, new ConstructorInterceptPoint() {
            @Override
            public ElementMatcher<MethodDescription> getConstructorMatcher() {
                return takesArgument(0, URI.class);
            }

            @Override
            public String getConstructorInterceptor() {
                return "com.a.eye.skywalking.plugin.jedis.v2.JedisConstructorInterceptor4UriArg";
            }
        }};
    }

    @Override
    protected InstanceMethodsInterceptPoint[] getInstanceMethodsInterceptPoints() {
        return new InstanceMethodsInterceptPoint[] {new InstanceMethodsInterceptPoint() {
            @Override
            public MethodMatcher[] getMethodsMatchers() {
                return new MethodMatcher[] {new MethodsExclusiveMatcher(new PrivateMethodMatcher(), new SimpleMethodMatcher("close"), new SimpleMethodMatcher("getDB"),
                        new SimpleMethodMatcher("connect"), new SimpleMethodMatcher("setDataSource"), new SimpleMethodMatcher("resetState"),
                        new SimpleMethodMatcher("clusterSlots"), new SimpleMethodMatcher("checkIsInMultiOrPipeline"))};
            }

            @Override
            public String getMethodsInterceptor() {
                return "com.a.eye.skywalking.plugin.jedis.v2.JedisMethodInterceptor";
            }
        }};
    }
}
