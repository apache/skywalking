package com.ai.cloud.skywalking.plugin.interceptor.enhance;

import com.ai.cloud.skywalking.logging.LogManager;
import com.ai.cloud.skywalking.logging.Logger;

/**
 * 类静态方法拦截、控制器
 *
 * @author wusheng
 */
public class ClassStaticMethodsInterceptor {
    private static Logger logger = LogManager.getLogger(ClassStaticMethodsInterceptor.class);

    private StaticMethodsAroundInterceptor interceptor;

    public ClassStaticMethodsInterceptor(StaticMethodsAroundInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    public Object intercept(Class<?> clazz,  Object[] allArguments, String methodName,  OriginCall zuper) throws Exception {
        MethodInvokeContext interceptorContext = new MethodInvokeContext(methodName, allArguments);
        MethodInterceptResult result = new MethodInterceptResult();
        try {
            interceptor.beforeMethod(interceptorContext, result);
        } catch (Throwable t) {
            logger.error("class[{}] before static method[{}] intercept failue:{}", new Object[] {clazz, methodName, t.getMessage()}, t);
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
                logger.error("class[{}] handle static method[{}] exception failue:{}", new Object[] {clazz, methodName, t2.getMessage()}, t2);
            }
            throw t;
        } finally {
            try {
                ret = interceptor.afterMethod(interceptorContext, ret);
            } catch (Throwable t) {
                logger.error("class[{}] after static method[{}] intercept failue:{}", new Object[] {clazz, methodName, t.getMessage()}, t);
            }
        }
        return ret;
    }
}
