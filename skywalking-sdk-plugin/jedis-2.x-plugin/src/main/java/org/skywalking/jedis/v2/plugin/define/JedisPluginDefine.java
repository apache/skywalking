package org.skywalking.jedis.v2.plugin.define;

import org.skywalking.jedis.v2.plugin.JedisInterceptor;

import com.ai.cloud.skywalking.plugin.interceptor.IAroundInterceptor;
import com.ai.cloud.skywalking.plugin.interceptor.InterceptorDefine;
import com.ai.cloud.skywalking.plugin.interceptor.MethodMatcher;
import com.ai.cloud.skywalking.plugin.interceptor.matcher.AnyMethodMatcher;

public class JedisPluginDefine implements InterceptorDefine {

	@Override
	public String getBeInterceptedClassName() {
		return "redis.clients.jedis.Jedis";
	}

	@Override
	public MethodMatcher[] getBeInterceptedMethodsMatchers() {
		return new MethodMatcher[] { new AnyMethodMatcher() };
	}

	@Override
	public IAroundInterceptor instance() {
		return new JedisInterceptor();
	}

}
