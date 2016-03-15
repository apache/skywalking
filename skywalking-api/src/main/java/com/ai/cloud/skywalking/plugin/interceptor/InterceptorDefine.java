package com.ai.cloud.skywalking.plugin.interceptor;

public interface InterceptorDefine {
	/**
	 * 返回要被增强的类，应当返回类全名
	 * 
	 * @return
	 */
	public String getBeInterceptedClassName();
	
	/**
	 * 返回需要被增强的方法列表
	 * 
	 * @return
	 */
	public InterceptPoint[] getBeInterceptedMethods();
	
	/**
	 * 返回增强拦截器的实现<br/>
	 * 每个拦截器在同一个被增强类的内部，保持单例
	 * 
	 * @return
	 */
	public IAroundInterceptor instance();
}
