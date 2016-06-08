package com.ai.cloud.skywalking.plugin.mysql;

import com.ai.cloud.skywalking.plugin.interceptor.IAroundInterceptor;
import com.ai.cloud.skywalking.plugin.interceptor.InterceptorDefine;
import com.ai.cloud.skywalking.plugin.interceptor.MethodNameMatcher;
import com.ai.cloud.skywalking.plugin.interceptor.matcher.FullNameMatcher;

public class ConnectionPluginDefine implements InterceptorDefine {

	@Override
	public String getBeInterceptedClassName() {
		return "com.mysql.jdbc.JDBC4Connection";
	}

	@Override
	public MethodNameMatcher[] getBeInterceptedMethodsMatchers() {
		return new MethodNameMatcher[] { new FullNameMatcher("createStatement", 2),
				new FullNameMatcher("prepareStatement", 3),
				new FullNameMatcher("prepareCall", 3),
				new FullNameMatcher("commit"), new FullNameMatcher("rollback"),
				new FullNameMatcher("close") };
	}

	@Override
	public IAroundInterceptor instance() {
		return new ConnectionInterceptor();
	}

}
