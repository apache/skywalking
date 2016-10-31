package test.a.eye.cloud.plugin;

import com.a.eye.skywalking.plugin.interceptor.EnhancedClassInstanceContext;
import com.a.eye.skywalking.plugin.interceptor.enhance.ConstructorInvokeContext;
import com.a.eye.skywalking.plugin.interceptor.enhance.InstanceMethodInvokeContext;
import com.a.eye.skywalking.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import com.a.eye.skywalking.plugin.interceptor.enhance.MethodInterceptResult;

public class TestAroundInterceptor implements InstanceMethodsAroundInterceptor {

	@Override
	public void onConstruct(EnhancedClassInstanceContext context, ConstructorInvokeContext interceptorContext) {
		context.set("test.key", "123");
		System.out.println("onConstruct, args size=" + interceptorContext.allArguments().length);
	}

	@Override
	public void beforeMethod(EnhancedClassInstanceContext context, InstanceMethodInvokeContext interceptorContext, MethodInterceptResult result) {
		System.out.println("beforeMethod : " + context.get("test.key", String.class));
	}

	@Override
	public Object afterMethod(EnhancedClassInstanceContext context, InstanceMethodInvokeContext interceptorContext, Object ret) {
		System.out.println("afterMethod: " + context.get("test.key", String.class));
		return ret;
	}

	@Override
	public void handleMethodException(Throwable t, EnhancedClassInstanceContext context, InstanceMethodInvokeContext interceptorContext) {
		// TODO Auto-generated method stub
		
	}

}
