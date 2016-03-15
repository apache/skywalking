package com.ai.cloud.skywalking.plugin.interceptor;

public interface IAroundInterceptor {
	public void onConstruct(EnhancedClassInstanceContext context, ConstructorInvokeContext interceptorContext);
	
	public void beforeMethod(EnhancedClassInstanceContext context, MethodInvokeContext interceptorContext);
	
	public Object afterMethod(EnhancedClassInstanceContext context, MethodInvokeContext interceptorContext, Object ret);
	
	public void handleMethodException(Throwable t, EnhancedClassInstanceContext context, MethodInvokeContext interceptorContext, Object ret);
}
