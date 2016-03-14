package com.ai.cloud.skywalking.plugin.interceptor;

public class ConstructorContext {
	private Object[] allArguments;
	
	ConstructorContext(Object[] allArguments) {
		this.allArguments = allArguments;
	}

	public Object[] allArguments(){
		return this.allArguments;
	}
}
