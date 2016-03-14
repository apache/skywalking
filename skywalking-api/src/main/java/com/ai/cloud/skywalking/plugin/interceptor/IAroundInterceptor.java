package com.ai.cloud.skywalking.plugin.interceptor;

public interface IAroundInterceptor {
	public void onConstruct(EnhancedClassInstanceContext context, ConstructorContext interceptorContext);
	
	public void beforeMethod(EnhancedClassInstanceContext context, InterceptorContext interceptorContext);
	
	public Object afterMethod(EnhancedClassInstanceContext context, InterceptorContext interceptorContext, Object ret);
	
	
}
