package com.ai.cloud.skywalking.plugin.interceptor.enhance;

public class MethodInterceptResult {
	private boolean isContinue = true;
	
	private Object _ret = null;
	
	public void defineReturnValue(Object ret){
		this.isContinue = false;
		this._ret = ret;
	}

	public boolean isContinue() {
		return isContinue;
	}
	
	Object _ret(){
		return _ret;
	}
}
