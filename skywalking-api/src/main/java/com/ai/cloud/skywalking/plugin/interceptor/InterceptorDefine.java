package com.ai.cloud.skywalking.plugin.interceptor;

public interface InterceptorDefine {
	public String getBeInterceptedClassName();
	
	public String[] getBeInterceptedMethods();
	
	public IAroundInterceptor instance();
}
