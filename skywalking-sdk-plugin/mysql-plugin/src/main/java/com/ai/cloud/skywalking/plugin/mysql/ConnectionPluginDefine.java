package com.ai.cloud.skywalking.plugin.mysql;

import com.ai.cloud.skywalking.plugin.interceptor.IAroundInterceptor;
import com.ai.cloud.skywalking.plugin.interceptor.InterceptPoint;
import com.ai.cloud.skywalking.plugin.interceptor.InterceptorDefine;

public class ConnectionPluginDefine implements InterceptorDefine {

	@Override
	public String getBeInterceptedClassName() {
		return "com.mysql.jdbc.JDBC4Connection";
	}

	@Override
	public InterceptPoint[] getBeInterceptedMethods() {
		return new InterceptPoint[] { new InterceptPoint("createStatement", 2),
				new InterceptPoint("prepareStatement", 3),
				new InterceptPoint("prepareCall", 3),
				new InterceptPoint("commit"), new InterceptPoint("rollback"),
				new InterceptPoint("close") };
	}

	@Override
	public IAroundInterceptor instance() {
		return new ConnectionInterceptor();
	}

}
