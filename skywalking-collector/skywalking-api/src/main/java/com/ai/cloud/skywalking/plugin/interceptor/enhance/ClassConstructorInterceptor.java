package com.ai.cloud.skywalking.plugin.interceptor.enhance;

import com.ai.cloud.skywalking.logging.LogManager;
import com.ai.cloud.skywalking.logging.Logger;
import com.ai.cloud.skywalking.plugin.interceptor.EnhancedClassInstanceContext;

public class ClassConstructorInterceptor {
    private static Logger logger = LogManager.getLogger(ClassConstructorInterceptor.class);

    private InstanceMethodsAroundInterceptor interceptor;

    public ClassConstructorInterceptor(InstanceMethodsAroundInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    public void intercept(Object obj, Object instanceContext, Object[] allArguments) {
        try {
            EnhancedClassInstanceContext context = new EnhancedClassInstanceContext();
            //			accessor.setValue(context);
            instanceContext = context;
            ConstructorInvokeContext interceptorContext = new ConstructorInvokeContext(obj, allArguments);
            interceptor.onConstruct(context, interceptorContext);
        } catch (Throwable t) {
            logger.error("ClassConstructorInterceptor failue.", t);
        }

    }
}
