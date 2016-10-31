package com.a.eye.skywalking.plugin.jedis.v2.define;

import com.a.eye.skywalking.plugin.interceptor.MethodMatcher;
import com.a.eye.skywalking.plugin.interceptor.enhance.ClassInstanceMethodsEnhancePluginDefine;
import com.a.eye.skywalking.plugin.interceptor.matcher.AnyMethodsMatcher;

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
    public String getInstanceMethodsInterceptor() {
        return "com.a.eye.skywalking.plugin.jedis.v2.JedisClusterInterceptor";
    }
}
