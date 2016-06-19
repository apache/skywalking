package test.ai.cloud.plugin;

import com.ai.cloud.skywalking.plugin.interceptor.enhance.MethodInvokeContext;
import com.ai.cloud.skywalking.plugin.interceptor.enhance.StaticMethodsAroundInterceptor;

public class TestStaticAroundInterceptor implements StaticMethodsAroundInterceptor {

	@Override
	public void beforeMethod(MethodInvokeContext interceptorContext) {
		System.out.println("beforeMethod : static");
	}

	@Override
	public Object afterMethod(MethodInvokeContext interceptorContext, Object ret) {
		System.out.println("afterMethod: static");
		return ret;
	}

	@Override
	public void handleMethodException(Throwable t, 
			MethodInvokeContext interceptorContext, Object ret) {
		// TODO Auto-generated method stub
		
	}

}
