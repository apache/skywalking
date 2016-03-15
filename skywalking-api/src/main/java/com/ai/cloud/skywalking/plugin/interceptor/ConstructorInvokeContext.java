package com.ai.cloud.skywalking.plugin.interceptor;

public class ConstructorInvokeContext {
	/**
	 * 代理对象实例
	 */
	private Object objInst;
	/**
	 * 构造函数参数
	 */
	private Object[] allArguments;
	
	ConstructorInvokeContext(Object objInst, Object[] allArguments) {
		this.objInst = objInst;
		this.allArguments = allArguments;
	}
	
	public Object inst(){
		return objInst;
	}

	public Object[] allArguments(){
		return this.allArguments;
	}
}
