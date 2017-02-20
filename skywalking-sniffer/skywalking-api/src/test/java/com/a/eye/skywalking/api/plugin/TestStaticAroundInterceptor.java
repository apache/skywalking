package com.a.eye.skywalking.api.plugin;

import com.a.eye.skywalking.api.plugin.interceptor.enhance.MethodInterceptResult;
import com.a.eye.skywalking.plugin.interceptor.enhance.MethodInvokeContext;
import com.a.eye.skywalking.api.plugin.interceptor.enhance.StaticMethodInvokeContext;
import com.a.eye.skywalking.api.plugin.interceptor.enhance.StaticMethodsAroundInterceptor;

public class TestStaticAroundInterceptor implements StaticMethodsAroundInterceptor {

	@Override
	public void beforeMethod(StaticMethodInvokeContext interceptorContext, MethodInterceptResult result) {
		System.out.println("beforeMethod : static");
	}

	@Override
	public Object afterMethod(StaticMethodInvokeContext interceptorContext, Object ret) {
		System.out.println("afterMethod: static");
		return ret;
	}

	@Override
	public void handleMethodException(Throwable t, MethodInvokeContext interceptorContext) {
		// TODO Auto-generated method stub
		
	}

}
