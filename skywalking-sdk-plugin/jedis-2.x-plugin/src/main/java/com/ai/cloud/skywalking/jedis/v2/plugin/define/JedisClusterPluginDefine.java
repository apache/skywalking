package com.ai.cloud.skywalking.jedis.v2.plugin.define;

import com.ai.cloud.skywalking.jedis.v2.plugin.JedisClusterInterceptor;
import com.ai.cloud.skywalking.plugin.interceptor.IAroundInterceptor;
import com.ai.cloud.skywalking.plugin.interceptor.InterceptorDefine;
import com.ai.cloud.skywalking.plugin.interceptor.MethodMatcher;
import com.ai.cloud.skywalking.plugin.interceptor.matcher.AnyMethodsMatcher;

public class JedisClusterPluginDefine implements InterceptorDefine {

    @Override
    public String getBeInterceptedClassName() {
        return "redis.clients.jedis.JedisCluster";
    }

    @Override
    public MethodMatcher[] getBeInterceptedMethodsMatchers() {
        return new MethodMatcher[]{
                new AnyMethodsMatcher()
        };
    }

    @Override
    public IAroundInterceptor instance() {
        return new JedisClusterInterceptor();
    }
}
