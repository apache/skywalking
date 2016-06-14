package com.ai.cloud.skywalking.plugin.mysql;

import com.ai.cloud.skywalking.plugin.interceptor.IAroundInterceptor;
import com.ai.cloud.skywalking.plugin.interceptor.InterceptorPluginDefine;
import com.ai.cloud.skywalking.plugin.interceptor.MethodMatcher;
import com.ai.cloud.skywalking.plugin.interceptor.matcher.SimpleMethodMatcher;

public class ConnectionPluginDefine extends InterceptorPluginDefine {

	@Override
	public String getBeInterceptedClassName() {
		return "com.mysql.jdbc.JDBC4Connection";
	}

	@Override
	public MethodMatcher[] getBeInterceptedMethodsMatchers() {
		return new MethodMatcher[] { new SimpleMethodMatcher("createStatement", 2),
				new SimpleMethodMatcher("prepareStatement", 3),
				new SimpleMethodMatcher("prepareCall", 3),
				new SimpleMethodMatcher("commit"), new SimpleMethodMatcher("rollback"),
				new SimpleMethodMatcher("close") };
	}

	@Override
	public IAroundInterceptor instance() {
		return new ConnectionInterceptor();
	}

}
