package com.a.eye.skywalking.plugin.jedis.v2.define;

import com.a.eye.skywalking.plugin.interceptor.matcher.MethodsExclusiveMatcher;
import com.a.eye.skywalking.plugin.interceptor.matcher.SimpleMethodMatcher;
import com.a.eye.skywalking.plugin.interceptor.MethodMatcher;
import com.a.eye.skywalking.plugin.interceptor.enhance.ClassInstanceMethodsEnhancePluginDefine;
import com.a.eye.skywalking.plugin.interceptor.matcher.PrivateMethodMatcher;

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
    public String getInstanceMethodsInterceptor() {
        return "com.a.eye.skywalking.plugin.jedis.v2.JedisInterceptor";
    }

}
