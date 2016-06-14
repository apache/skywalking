package com.ai.cloud.skywalking.jedis.v2.plugin.define;

import com.ai.cloud.skywalking.jedis.v2.plugin.JedisInterceptor;
import com.ai.cloud.skywalking.plugin.interceptor.IAroundInterceptor;
import com.ai.cloud.skywalking.plugin.interceptor.InterceptorPluginDefine;
import com.ai.cloud.skywalking.plugin.interceptor.MethodMatcher;
import com.ai.cloud.skywalking.plugin.interceptor.matcher.MethodsExclusiveMatcher;
import com.ai.cloud.skywalking.plugin.interceptor.matcher.PrivateMethodMatcher;
import com.ai.cloud.skywalking.plugin.interceptor.matcher.SimpleMethodMatcher;

public class JedisPluginDefine extends InterceptorPluginDefine {

    @Override
    public String getBeInterceptedClassName() {
        return "redis.clients.jedis.Jedis";
    }

    @Override
    public MethodMatcher[] getBeInterceptedMethodsMatchers() {
        return new MethodMatcher[]{
                new MethodsExclusiveMatcher(
                        new PrivateMethodMatcher(),
                        new SimpleMethodMatcher("close"),
                        new SimpleMethodMatcher("getDB"),
                        new SimpleMethodMatcher("connect"),
                        new SimpleMethodMatcher("setDataSource"),
                        new SimpleMethodMatcher("resetState"),
                        new SimpleMethodMatcher("clusterSlots"),
                        new SimpleMethodMatcher("checkIsInMultiOrPipeline")
                )
        };
    }

    @Override
    public IAroundInterceptor instance() {
        return new JedisInterceptor();
    }

}
