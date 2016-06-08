package org.skywalking.jedis.v2.plugin.define;

import com.ai.cloud.skywalking.plugin.interceptor.IAroundInterceptor;
import com.ai.cloud.skywalking.plugin.interceptor.InterceptPoint;
import com.ai.cloud.skywalking.plugin.interceptor.InterceptorDefine;
import com.ai.cloud.skywalking.plugin.interceptor.MethodNameMatcher;
import com.ai.cloud.skywalking.plugin.interceptor.matcher.ExclusionNameMatcher;
import org.skywalking.jedis.v2.plugin.JedisInterceptor;

public class JedisPluginDefine implements InterceptorDefine {

    @Override
    public String getBeInterceptedClassName() {
        return "redis.clients.jedis.Jedis";
    }

    @Override
    public MethodNameMatcher[] getBeInterceptedMethods() {
        return new MethodNameMatcher[]{
                new ExclusionNameMatcher("set"),
        };
    }

    @Override
    public IAroundInterceptor instance() {
        return new JedisInterceptor();
    }

}
