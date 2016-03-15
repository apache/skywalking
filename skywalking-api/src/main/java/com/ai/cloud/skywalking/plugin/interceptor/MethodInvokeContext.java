package com.ai.cloud.skywalking.plugin.interceptor;

/**
 * 方法执行拦截上下文
 * 
 * @author wusheng
 *
 */
public class MethodInvokeContext {
	/**
	 * 代理类实例
	 */
	private Object objInst;
	/**
	 * 方法名称
	 */
	private String methodName;
	/**
	 * 方法参数
	 */
	private Object[] allArguments;
	
	MethodInvokeContext(Object objInst, String methodName, Object[] allArguments) {
		this.objInst = objInst;
		this.methodName = methodName;
		this.allArguments = allArguments;
	}
	
	public Object[] allArguments(){
		return this.allArguments;
	}
	
	public String methodName(){
		return methodName;
	}
	
	public Object inst(){
		return objInst;
	}
}
