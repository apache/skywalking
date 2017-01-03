package com.a.eye.skywalking.plugin.jedis.v2.define;

import com.a.eye.skywalking.plugin.bytebuddy.AllObjectDefaultMethodsMatch;
import com.a.eye.skywalking.plugin.bytebuddy.ArgumentTypeNameMatch;
import com.a.eye.skywalking.plugin.interceptor.ConstructorInterceptPoint;
import com.a.eye.skywalking.plugin.interceptor.InstanceMethodsInterceptPoint;
import com.a.eye.skywalking.plugin.interceptor.enhance.ClassInstanceMethodsEnhancePluginDefine;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;

import java.net.URI;

import static com.a.eye.skywalking.plugin.bytebuddy.ArgumentTypeNameMatch.takesArgumentWithType;
import static net.bytebuddy.matcher.ElementMatchers.*;

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
                return takesArgumentWithType(0, "redis.clients.jedis.HostAndPort");
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
                return "com.a.eye.skywalking.plugin.jedis.v2.JedisMethodInterceptor";
            }
        }};
    }
}
