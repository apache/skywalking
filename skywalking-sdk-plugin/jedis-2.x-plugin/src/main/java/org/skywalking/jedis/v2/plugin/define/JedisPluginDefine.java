package org.skywalking.jedis.v2.plugin.define;

import com.ai.cloud.skywalking.plugin.interceptor.IAroundInterceptor;
import com.ai.cloud.skywalking.plugin.interceptor.InterceptorDefine;
import com.ai.cloud.skywalking.plugin.interceptor.MethodMatcher;
import com.ai.cloud.skywalking.plugin.interceptor.matcher.MethodsExclusiveMatcher;
import com.ai.cloud.skywalking.plugin.interceptor.matcher.PrivateMethodMatcher;
import com.ai.cloud.skywalking.plugin.interceptor.matcher.SimpleMethodMatcher;
import org.skywalking.jedis.v2.plugin.JedisInterceptor;

public class JedisPluginDefine implements InterceptorDefine {

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
                        new SimpleMethodMatcher("resetState")
                )
        };
    }

    @Override
    public IAroundInterceptor instance() {
        return new JedisInterceptor();
    }

}
