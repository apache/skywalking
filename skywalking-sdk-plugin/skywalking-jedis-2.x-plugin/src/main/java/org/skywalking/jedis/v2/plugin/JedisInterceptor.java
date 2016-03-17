package org.skywalking.jedis.v2.plugin;

import com.ai.cloud.skywalking.plugin.interceptor.ConstructorInvokeContext;
import com.ai.cloud.skywalking.plugin.interceptor.EnhancedClassInstanceContext;
import com.ai.cloud.skywalking.plugin.interceptor.IAroundInterceptor;
import com.ai.cloud.skywalking.plugin.interceptor.MethodInvokeContext;

public class JedisInterceptor implements IAroundInterceptor{

	@Override
	public void onConstruct(EnhancedClassInstanceContext context,
			ConstructorInvokeContext interceptorContext) {
		
	}

	@Override
	public void beforeMethod(EnhancedClassInstanceContext context,
			MethodInvokeContext interceptorContext) {
		
	}

	@Override
	public Object afterMethod(EnhancedClassInstanceContext context,
			MethodInvokeContext interceptorContext, Object ret) {
		return ret;
	}

	@Override
	public void handleMethodException(Throwable t,
			EnhancedClassInstanceContext context,
			MethodInvokeContext interceptorContext, Object ret) {
		
	}

}
