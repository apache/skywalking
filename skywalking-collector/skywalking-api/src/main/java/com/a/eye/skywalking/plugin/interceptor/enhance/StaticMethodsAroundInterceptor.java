package com.a.eye.skywalking.plugin.interceptor.enhance;


public interface StaticMethodsAroundInterceptor {
	public void beforeMethod(StaticMethodInvokeContext interceptorContext, MethodInterceptResult result);
	
	public Object afterMethod(StaticMethodInvokeContext interceptorContext, Object ret);
	
	public void handleMethodException(Throwable t, MethodInvokeContext interceptorContext);
}
