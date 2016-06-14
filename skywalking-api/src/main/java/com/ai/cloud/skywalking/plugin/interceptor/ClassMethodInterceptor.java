package com.ai.cloud.skywalking.plugin.interceptor;

import java.lang.reflect.Method;
import java.util.concurrent.Callable;

import net.bytebuddy.implementation.bind.annotation.AllArguments;
import net.bytebuddy.implementation.bind.annotation.FieldValue;
import net.bytebuddy.implementation.bind.annotation.Origin;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.SuperCall;
import net.bytebuddy.implementation.bind.annotation.This;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 类方法拦截、控制器
 * 
 * @author wusheng
 *
 */
public class ClassMethodInterceptor {
	private static Logger logger = LogManager
			.getLogger(ClassMethodInterceptor.class);

	private IAroundInterceptor interceptor;

	public ClassMethodInterceptor(IAroundInterceptor interceptor) {
		this.interceptor = interceptor;
	}

	@RuntimeType
	public Object intercept(
			@This Object obj,
			@AllArguments Object[] allArguments,
			@Origin Method method,
			@SuperCall Callable<?> zuper,
			@FieldValue(InterceptorPluginDefine.contextAttrName) EnhancedClassInstanceContext instanceContext)
			throws Exception {
		MethodInvokeContext interceptorContext = new MethodInvokeContext(obj,
				method.getName(), allArguments);
		try {
			interceptor.beforeMethod(instanceContext, interceptorContext);
		} catch (Throwable t) {
			logger.error("class[{}] before method[{}] intercept failue:{}",
					obj.getClass(), method.getName(), t.getMessage(), t);
		}
		Object ret = null;
		try {
			ret =  zuper.call();
		} catch(Throwable t){
			try {
				interceptor.handleMethodException(t, instanceContext, interceptorContext, ret);
			} catch (Throwable t2) {
				logger.error("class[{}] handle method[{}] exception failue:{}",
						obj.getClass(), method.getName(), t2.getMessage(), t2);
			}
			throw t;
		}finally {
			try {
				ret = interceptor.afterMethod(instanceContext, interceptorContext, ret);
			} catch (Throwable t) {
				logger.error("class[{}] after method[{}] intercept failue:{}",
						obj.getClass(), method.getName(), t.getMessage(), t);
			}
		}
		return ret;
	}
}
