package org.skywalking.jedis.v2.plugin.define;

import org.skywalking.jedis.v2.plugin.JedisInterceptor;

import com.ai.cloud.skywalking.plugin.interceptor.IAroundInterceptor;
import com.ai.cloud.skywalking.plugin.interceptor.InterceptorDefine;
import com.ai.cloud.skywalking.plugin.interceptor.MethodNameMatcher;
import com.ai.cloud.skywalking.plugin.interceptor.matcher.AnyMethodMatcher;

public class JedisPluginDefine implements InterceptorDefine {

	@Override
	public String getBeInterceptedClassName() {
		return "redis.clients.jedis.Jedis";
	}

	@Override
	public MethodNameMatcher[] getBeInterceptedMethodsMatchers() {
		return new MethodNameMatcher[] { new AnyMethodMatcher() };
	}

	@Override
	public IAroundInterceptor instance() {
		return new JedisInterceptor();
	}

}
