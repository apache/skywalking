package com.ai.cloud.skywalking.plugin.interceptor;


public class InterceptorContext {
	private Object objInst;
	private String methodName;
	private Object[] allArguments;
	
	InterceptorContext(Object objInst, String methodName, Object[] allArguments) {
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
