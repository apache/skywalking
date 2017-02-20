package com.a.eye.skywalking.api.plugin.interceptor;

import com.a.eye.skywalking.api.plugin.AbstractClassEnhancePluginDefine;
import com.a.eye.skywalking.api.plugin.interceptor.enhance.ClassEnhancePluginDefine;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enhanced instance field type.
 *
 * Any plugins({@link AbstractClassEnhancePluginDefine}'s subclass) override
 * {@link ClassEnhancePluginDefine#getConstructorsInterceptPoints}
 * or
 * {@link ClassEnhancePluginDefine#getInstanceMethodsInterceptPoints}
 * will add a field with this type.
 * 
 * @author wusheng
 *
 */
public class EnhancedClassInstanceContext {
	/**
	 * extend field, can store any instance as you want.
	 */
	private Map<Object, Object> context = new ConcurrentHashMap<Object, Object>();

	/**
	 * store a new instance or override it.
	 * @param key
	 * @param value
	 */
	public void set(Object key, Object value){
		context.put(key, value);
	}

	/**
	 * get an stored instance, if it is existed.
	 * @param key
	 * @return null or stored instance.
	 */
	public Object get(Object key){
		return context.get(key);
	}

	/**
	 * judge whether stores by the key.
	 * @param key
	 * @return true, if stored a instance by the key.
	 */
	public boolean isContain(Object key){
		return context.containsKey(key);
	}

	/**
	 * get an stored instance, if it is existed.
	 * @param key
	 * @param type
	 * @param <T> expected stored instance's type
	 * @return null or stored instance.
	 */
	public <T> T get(Object key, Class<T> type){
		return (T)this.get(key);
	}
}
