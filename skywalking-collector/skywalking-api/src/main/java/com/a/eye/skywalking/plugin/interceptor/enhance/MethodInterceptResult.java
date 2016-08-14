package com.a.eye.skywalking.plugin.interceptor.enhance;

/**
 * 通过拦截器的before方法,指定被拦截方法的返回值,不再调用原始方法取得返回值
 */
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
