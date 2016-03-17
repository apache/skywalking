package org.skywalking.jedis.v2.plugin.define;

import org.skywalking.jedis.v2.plugin.JedisInterceptor;

import com.ai.cloud.skywalking.plugin.interceptor.IAroundInterceptor;
import com.ai.cloud.skywalking.plugin.interceptor.InterceptPoint;
import com.ai.cloud.skywalking.plugin.interceptor.InterceptorDefine;

public class JedisPluginDefine implements InterceptorDefine {

	@Override
	public String getBeInterceptedClassName() {
		return "redis.clients.jedis.Jedis";
	}

	@Override
	public InterceptPoint[] getBeInterceptedMethods() {
		return new InterceptPoint[] { new InterceptPoint("*") };
	}

	@Override
	public IAroundInterceptor instance() {
		return new JedisInterceptor();
	}

}
