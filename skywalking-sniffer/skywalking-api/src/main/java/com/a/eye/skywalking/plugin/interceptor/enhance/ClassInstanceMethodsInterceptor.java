package com.a.eye.skywalking.plugin.interceptor.enhance;

import com.a.eye.skywalking.logging.LogManager;
import com.a.eye.skywalking.logging.EasyLogger;
import com.a.eye.skywalking.plugin.interceptor.EnhancedClassInstanceContext;
import com.a.eye.skywalking.plugin.interceptor.loader.InterceptorInstanceLoader;
import net.bytebuddy.implementation.bind.annotation.*;

import java.lang.reflect.Method;
import java.util.concurrent.Callable;

/**
 * 类方法拦截、控制器
 *
 * @author wusheng
 */
public class ClassInstanceMethodsInterceptor {
    private static EasyLogger easyLogger = LogManager.getLogger(ClassInstanceMethodsInterceptor.class);

    private String instanceMethodsAroundInterceptorClassName;

    public ClassInstanceMethodsInterceptor(String instanceMethodsAroundInterceptorClassName) {
        this.instanceMethodsAroundInterceptorClassName = instanceMethodsAroundInterceptorClassName;
    }

    @RuntimeType
    public Object intercept(@This Object obj, @AllArguments Object[] allArguments, @Origin Method method, @SuperCall Callable<?> zuper,
            @FieldValue(ClassEnhancePluginDefine.contextAttrName) EnhancedClassInstanceContext instanceContext) throws Exception {
        InstanceMethodsAroundInterceptor interceptor = InterceptorInstanceLoader
                .load(instanceMethodsAroundInterceptorClassName, obj.getClass().getClassLoader());

        InstanceMethodInvokeContext interceptorContext = new InstanceMethodInvokeContext(obj, method.getName(), allArguments, method.getParameterTypes());
        MethodInterceptResult result = new MethodInterceptResult();
        try {
            interceptor.beforeMethod(instanceContext, interceptorContext, result);
        } catch (Throwable t) {
            easyLogger.error("class[{}] before method[{}] intercept failue:{}", new Object[] {obj.getClass(), method.getName(), t.getMessage()}, t);
        }

        Object ret = null;
        try {
            if (!result.isContinue()) {
                ret = result._ret();
            }else {
                ret = zuper.call();
            }
        } catch (Throwable t) {
            try {
                interceptor.handleMethodException(t, instanceContext, interceptorContext);
            } catch (Throwable t2) {
                easyLogger.error("class[{}] handle method[{}] exception failue:{}", new Object[] {obj.getClass(), method.getName(), t2.getMessage()}, t2);
            }
            throw t;
        } finally {
            try {
                ret = interceptor.afterMethod(instanceContext, interceptorContext, ret);
            } catch (Throwable t) {
                easyLogger.error("class[{}] after method[{}] intercept failue:{}", new Object[] {obj.getClass(), method.getName(), t.getMessage()}, t);
            }
        }
        return ret;
    }
}
