package com.ai.cloud.skywalking.plugin.interceptor.enhance;

import com.ai.cloud.skywalking.logging.LogManager;
import com.ai.cloud.skywalking.logging.Logger;
import com.ai.cloud.skywalking.plugin.interceptor.loader.InterceptorInstanceLoader;
import net.bytebuddy.implementation.bind.annotation.AllArguments;
import net.bytebuddy.implementation.bind.annotation.Origin;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.SuperCall;

import java.lang.reflect.Method;
import java.util.concurrent.Callable;

/**
 * 类静态方法拦截、控制器
 *
 * @author wusheng
 */
public class ClassStaticMethodsInterceptor {
    private static Logger logger = LogManager.getLogger(ClassStaticMethodsInterceptor.class);

    private String staticMethodsAroundInterceptorClassName;

    public ClassStaticMethodsInterceptor(String staticMethodsAroundInterceptorClassName) {
        this.staticMethodsAroundInterceptorClassName = staticMethodsAroundInterceptorClassName;
    }

    @RuntimeType
    public Object intercept(@Origin Class<?> clazz, @AllArguments Object[] allArguments, @Origin Method method, @SuperCall Callable<?> zuper) throws Exception {
        StaticMethodsAroundInterceptor interceptor = InterceptorInstanceLoader.load(staticMethodsAroundInterceptorClassName, clazz.getClassLoader());

        MethodInvokeContext interceptorContext = new MethodInvokeContext(method.getName(), allArguments);
        MethodInterceptResult result = new MethodInterceptResult();
        try {
            interceptor.beforeMethod(interceptorContext, result);
        } catch (Throwable t) {
            logger.error("class[{}] before static method[{}] intercept failue:{}", new Object[] {clazz, method.getName(), t.getMessage()}, t);
        }
        if (!result.isContinue()) {
            return result._ret();
        }

        Object ret = null;
        try {
            ret = zuper.call();
        } catch (Throwable t) {
            try {
                interceptor.handleMethodException(t, interceptorContext, ret);
            } catch (Throwable t2) {
                logger.error("class[{}] handle static method[{}] exception failue:{}", new Object[] {clazz, method.getName(), t2.getMessage()}, t2);
            }
            throw t;
        } finally {
            try {
                ret = interceptor.afterMethod(interceptorContext, ret);
            } catch (Throwable t) {
                logger.error("class[{}] after static method[{}] intercept failue:{}", new Object[] {clazz, method.getName(), t.getMessage()}, t);
            }
        }
        return ret;
    }
}
