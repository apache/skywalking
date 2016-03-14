package test.ai.cloud.plugin;

import com.ai.cloud.skywalking.plugin.interceptor.ConstructorContext;
import com.ai.cloud.skywalking.plugin.interceptor.EnhancedClassInstanceContext;
import com.ai.cloud.skywalking.plugin.interceptor.IAroundInterceptor;
import com.ai.cloud.skywalking.plugin.interceptor.InterceptorContext;

public class TestAroundInterceptor implements IAroundInterceptor {

	@Override
	public void onConstruct(EnhancedClassInstanceContext context, ConstructorContext interceptorContext) {
		System.out.println("onConstruct, args size=" + interceptorContext.allArguments().length);
	}

	@Override
	public void beforeMethod(EnhancedClassInstanceContext context, InterceptorContext interceptorContext) {
		System.out.println("beforeMethod : " + context);
	}

	@Override
	public void afterMethod(EnhancedClassInstanceContext context, InterceptorContext interceptorContext) {
		System.out.println("afterMethod: " + context);
	}

}
