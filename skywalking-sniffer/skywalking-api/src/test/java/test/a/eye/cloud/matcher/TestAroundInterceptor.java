package test.a.eye.cloud.matcher;

import com.a.eye.skywalking.plugin.interceptor.EnhancedClassInstanceContext;
import com.a.eye.skywalking.plugin.interceptor.enhance.ConstructorInvokeContext;
import com.a.eye.skywalking.plugin.interceptor.enhance.InstanceMethodInvokeContext;
import com.a.eye.skywalking.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import com.a.eye.skywalking.plugin.interceptor.enhance.MethodInterceptResult;

/**
 * Created by xin on 16-6-8.
 */
public class TestAroundInterceptor implements InstanceMethodsAroundInterceptor {
    @Override
    public void onConstruct(EnhancedClassInstanceContext context, ConstructorInvokeContext interceptorContext) {

    }

    @Override
    public void beforeMethod(EnhancedClassInstanceContext context, InstanceMethodInvokeContext interceptorContext, MethodInterceptResult result) {
        System.out.println("before method");
    }

    @Override
    public Object afterMethod(EnhancedClassInstanceContext context, InstanceMethodInvokeContext interceptorContext, Object ret) {
        System.out.println("after method");
        return ret;
    }

    @Override
    public void handleMethodException(Throwable t, EnhancedClassInstanceContext context, InstanceMethodInvokeContext interceptorContext) {

    }
}
