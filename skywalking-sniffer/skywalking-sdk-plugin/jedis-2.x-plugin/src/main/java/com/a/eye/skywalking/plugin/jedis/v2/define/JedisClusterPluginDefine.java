package com.a.eye.skywalking.plugin.jedis.v2.define;

import com.a.eye.skywalking.plugin.bytebuddy.AllObjectDefaultMethodsMatch;
import com.a.eye.skywalking.plugin.bytebuddy.ArgumentTypeNameMatch;
import com.a.eye.skywalking.plugin.interceptor.ConstructorInterceptPoint;
import com.a.eye.skywalking.plugin.interceptor.InstanceMethodsInterceptPoint;
import com.a.eye.skywalking.plugin.interceptor.enhance.ClassInstanceMethodsEnhancePluginDefine;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;

import java.util.Set;

import static com.a.eye.skywalking.plugin.bytebuddy.ArgumentTypeNameMatch.takesArgumentWithType;
import static net.bytebuddy.matcher.ElementMatchers.*;

public class JedisClusterPluginDefine extends ClassInstanceMethodsEnhancePluginDefine {

    @Override
    public String enhanceClassName() {
        return "redis.clients.jedis.JedisCluster";
    }

    @Override
    protected ConstructorInterceptPoint[] getConstructorsInterceptPoints() {
        return new ConstructorInterceptPoint[] {new ConstructorInterceptPoint() {
            @Override
            public ElementMatcher<MethodDescription> getConstructorMatcher() {
                return takesArgument(0, Set.class);
            }

            @Override
            public String getConstructorInterceptor() {
                return "com.a.eye.skywalking.plugin.jedis.v2.JedisClusterConstructorInterceptor4SetArg";
            }
        }, new ConstructorInterceptPoint() {
            @Override
            public ElementMatcher<MethodDescription> getConstructorMatcher() {
                return takesArgumentWithType(0, "redis.clients.jedis.HostAndPort");
            }

            @Override
            public String getConstructorInterceptor() {
                return "com.a.eye.skywalking.plugin.jedis.v2.JedisClusterConstructorInterceptor4HostAndPortArg";
            }
        }};
    }

    @Override
    protected InstanceMethodsInterceptPoint[] getInstanceMethodsInterceptPoints() {
        return new InstanceMethodsInterceptPoint[] {new InstanceMethodsInterceptPoint() {
            @Override
            public ElementMatcher<MethodDescription> getMethodsMatcher() {
                return any().and(not(AllObjectDefaultMethodsMatch.INSTANCE));
            }

            @Override
            public String getMethodsInterceptor() {
                return "com.a.eye.skywalking.plugin.jedis.v2.JedisMethodInterceptor";
            }
        }};
    }
}
