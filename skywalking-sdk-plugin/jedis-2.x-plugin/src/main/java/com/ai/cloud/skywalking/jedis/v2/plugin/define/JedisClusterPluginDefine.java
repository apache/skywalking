package com.ai.cloud.skywalking.jedis.v2.plugin.define;

import com.ai.cloud.skywalking.jedis.v2.plugin.JedisClusterInterceptor;
import com.ai.cloud.skywalking.plugin.interceptor.MethodMatcher;
import com.ai.cloud.skywalking.plugin.interceptor.enhance.ClassInstanceMethodsEnhancePluginDefine;
import com.ai.cloud.skywalking.plugin.interceptor.enhance.IntanceMethodsAroundInterceptor;
import com.ai.cloud.skywalking.plugin.interceptor.matcher.AnyMethodsMatcher;

public class JedisClusterPluginDefine extends ClassInstanceMethodsEnhancePluginDefine {

    @Override
    public String getBeInterceptedClassName() {
        return "redis.clients.jedis.JedisCluster";
    }

    @Override
    public MethodMatcher[] getInstanceMethodsMatchers() {
        return new MethodMatcher[]{
                new AnyMethodsMatcher()
        };
    }

    @Override
    public IntanceMethodsAroundInterceptor getInstanceMethodsInterceptor() {
        return new JedisClusterInterceptor();
    }
}
