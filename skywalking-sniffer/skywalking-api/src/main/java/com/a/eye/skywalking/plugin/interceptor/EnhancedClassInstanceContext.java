package com.a.eye.skywalking.plugin.interceptor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 被增强的类实例，需扩展的context属性，用于在不同的方法，或者构造函数间保存实例
 * 
 * @author wusheng
 *
 */
public class EnhancedClassInstanceContext {
	private Map<Object, Object> context = new ConcurrentHashMap<Object, Object>();
	
	public void set(Object key, Object value){
		context.put(key, value);
	}
	
	public Object get(Object key){
		return context.get(key);
	}

	public boolean isContain(Object key){
		return context.containsKey(key);
	}
	
	public <T> T get(Object key, Class<T> type){
		return (T)this.get(key);
	}
}
