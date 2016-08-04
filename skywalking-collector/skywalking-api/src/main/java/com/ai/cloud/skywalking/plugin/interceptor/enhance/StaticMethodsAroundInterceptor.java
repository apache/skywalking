package com.ai.cloud.skywalking.plugin.interceptor.enhance;


public interface StaticMethodsAroundInterceptor {
	public void beforeMethod(MethodInvokeContext interceptorContext, MethodInterceptResult result);
	
	public Object afterMethod(MethodInvokeContext interceptorContext, Object ret);
	
	public void handleMethodException(Throwable t, MethodInvokeContext interceptorContext);
}
