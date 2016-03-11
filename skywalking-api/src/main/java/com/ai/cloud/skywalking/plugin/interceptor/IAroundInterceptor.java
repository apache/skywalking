package com.ai.cloud.skywalking.plugin.interceptor;

public interface IAroundInterceptor {
	public void onConstruct(EnhancedClassInstanceContext context);
	
	public void beforeMethod(EnhancedClassInstanceContext context);
	
	public void afterMethod(EnhancedClassInstanceContext context);
	
	
}
