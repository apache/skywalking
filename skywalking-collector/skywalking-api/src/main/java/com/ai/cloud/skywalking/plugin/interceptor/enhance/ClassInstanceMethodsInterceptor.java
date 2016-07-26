package com.ai.cloud.skywalking.plugin.interceptor.enhance;

import com.ai.cloud.skywalking.logging.LogManager;
import com.ai.cloud.skywalking.logging.Logger;
import com.ai.cloud.skywalking.plugin.interceptor.EnhancedClassInstanceContext;

/**
 * 类方法拦截、控制器
 *
 * @author wusheng
 */
public class ClassInstanceMethodsInterceptor {
    private static Logger logger = LogManager.getLogger(ClassInstanceMethodsInterceptor.class);

    private InstanceMethodsAroundInterceptor interceptor;

    public ClassInstanceMethodsInterceptor(InstanceMethodsAroundInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    public Object intercept(Object obj, Object[] allArguments, String methodName, OriginCall zuper, EnhancedClassInstanceContext instanceContext) throws Exception {
        InstanceMethodInvokeContext interceptorContext = new InstanceMethodInvokeContext(obj, methodName, allArguments);
        MethodInterceptResult result = new MethodInterceptResult();
        try {
            interceptor.beforeMethod(instanceContext, interceptorContext, result);
        } catch (Throwable t) {
            logger.error("class[{}] before method[{}] intercept failue:{}", new Object[] {obj.getClass(), methodName, t.getMessage()}, t);
        }
        if (!result.isContinue()) {
            return result._ret();
        }

        Object ret = null;
        try {
            ret = zuper.call();
        } catch (Throwable t) {
            try {
                interceptor.handleMethodException(t, instanceContext, interceptorContext, ret);
            } catch (Throwable t2) {
                logger.error("class[{}] handle method[{}] exception failue:{}", new Object[] {obj.getClass(), methodName, t2.getMessage()}, t2);
            }
            throw t;
        } finally {
            try {
                ret = interceptor.afterMethod(instanceContext, interceptorContext, ret);
            } catch (Throwable t) {
                logger.error("class[{}] after method[{}] intercept failue:{}", new Object[] {obj.getClass(), methodName, t.getMessage()}, t);
            }
        }
        return ret;
    }
}
