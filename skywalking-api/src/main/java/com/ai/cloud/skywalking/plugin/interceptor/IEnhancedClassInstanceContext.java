package com.ai.cloud.skywalking.plugin.interceptor;

/**
 * 被增强的类,会实现此接口，用于快速获取实例级属性上下文扩展<br/>
 * @see com.ai.cloud.skywalking.plugin.interceptor.EnhanceClazz4Interceptor.enhance0()
 * 
 * @author wusheng
 *
 */
public interface IEnhancedClassInstanceContext {
	public EnhancedClassInstanceContext getEnhancedClassInstanceContext();
}
