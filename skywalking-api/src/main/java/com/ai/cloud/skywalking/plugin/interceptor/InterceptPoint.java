package com.ai.cloud.skywalking.plugin.interceptor;


public class InterceptPoint {
	private String methodName;
	
	private int argNum = -1;
	
	private Class<?>[] argTypeArray;

	public InterceptPoint(String methodName) {
		super();
		this.methodName = methodName;
	}
	
	public InterceptPoint(String methodName, int argNum) {
		this(methodName);
		this.argNum = argNum;
	}
	
	public InterceptPoint(String methodName, Class<?>... args) {
		this(methodName);
		this.argTypeArray = args;
	}

	public String getMethodName() {
		return methodName;
	}

	public int getArgNum() {
		return argNum;
	}

	public Class<?>[] getArgTypeArray() {
		return argTypeArray;
	}

	public void setArgNum(int argNum) {
		this.argNum = argNum;
	}
	
	public void setArgTypeList(Class<?>... args){
		argTypeArray = args;
	}
}
