package com.ai.cloud.skywalking.jedis.v2.plugin.define;

import com.ai.cloud.skywalking.jedis.v2.plugin.JedisInterceptor;
import com.ai.cloud.skywalking.plugin.interceptor.MethodMatcher;
import com.ai.cloud.skywalking.plugin.interceptor.enhance.ClassInstanceMethodsEnhancePluginDefine;
import com.ai.cloud.skywalking.plugin.interceptor.enhance.IntanceMethodsAroundInterceptor;
import com.ai.cloud.skywalking.plugin.interceptor.matcher.MethodsExclusiveMatcher;
import com.ai.cloud.skywalking.plugin.interceptor.matcher.PrivateMethodMatcher;
import com.ai.cloud.skywalking.plugin.interceptor.matcher.SimpleMethodMatcher;

public class JedisPluginDefine extends ClassInstanceMethodsEnhancePluginDefine {

    @Override
    public String enhanceClassName() {
        return "redis.clients.jedis.Jedis";
    }

    @Override
    public MethodMatcher[] getInstanceMethodsMatchers() {
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
    public IntanceMethodsAroundInterceptor getInstanceMethodsInterceptor() {
        return new JedisInterceptor();
    }

}
