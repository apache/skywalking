package test.ai.cloud.matcher;

import com.ai.cloud.skywalking.plugin.interceptor.ConstructorInvokeContext;
import com.ai.cloud.skywalking.plugin.interceptor.EnhancedClassInstanceContext;
import com.ai.cloud.skywalking.plugin.interceptor.IAroundInterceptor;
import com.ai.cloud.skywalking.plugin.interceptor.MethodInvokeContext;

/**
 * Created by xin on 16-6-8.
 */
public class TestAroundInterceptor implements IAroundInterceptor {
    @Override
    public void onConstruct(EnhancedClassInstanceContext context, ConstructorInvokeContext interceptorContext) {

    }

    @Override
    public void beforeMethod(EnhancedClassInstanceContext context, MethodInvokeContext interceptorContext) {
        System.out.println("before method");
    }

    @Override
    public Object afterMethod(EnhancedClassInstanceContext context, MethodInvokeContext interceptorContext, Object ret) {
        System.out.println("after method");
        return ret;
    }

    @Override
    public void handleMethodException(Throwable t, EnhancedClassInstanceContext context, MethodInvokeContext interceptorContext, Object ret) {

    }
}
