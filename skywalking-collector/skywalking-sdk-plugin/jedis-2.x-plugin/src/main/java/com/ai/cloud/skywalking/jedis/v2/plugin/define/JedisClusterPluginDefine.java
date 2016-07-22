package com.ai.cloud.skywalking.jedis.v2.plugin.define;

import com.ai.cloud.skywalking.jedis.v2.plugin.JedisClusterInterceptor;
import com.ai.cloud.skywalking.plugin.interceptor.MethodMatcher;
import com.ai.cloud.skywalking.plugin.interceptor.enhance.ClassInstanceMethodsEnhancePluginDefine;
import com.ai.cloud.skywalking.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import com.ai.cloud.skywalking.plugin.interceptor.matcher.AnyMethodsMatcher;

public class JedisClusterPluginDefine extends ClassInstanceMethodsEnhancePluginDefine {

    @Override
    public String enhanceClassName() {
        return "redis.clients.jedis.JedisCluster";
    }

    @Override
    public MethodMatcher[] getInstanceMethodsMatchers() {
        return new MethodMatcher[]{
                new AnyMethodsMatcher()
        };
    }

    @Override
    public InstanceMethodsAroundInterceptor getInstanceMethodsInterceptor() {
        return new JedisClusterInterceptor();
    }
}
