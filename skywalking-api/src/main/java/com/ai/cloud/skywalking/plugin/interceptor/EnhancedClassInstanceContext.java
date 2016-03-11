package com.ai.cloud.skywalking.plugin.interceptor;

import java.util.HashMap;
import java.util.Map;

/**
 * 被增强的类实例，需扩展的context属性，用于在不同的方法，或者构造函数间保存实例
 * 
 * @author wusheng
 *
 */
public class EnhancedClassInstanceContext {
	public static final String FIELD_NAME = "_$EnhancedClassInstanceContext";
	
	private Map<Object, Object> context = new HashMap<Object, Object>();
	
	
}
