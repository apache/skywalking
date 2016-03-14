package test.ai.cloud.plugin;

import com.ai.cloud.skywalking.plugin.interceptor.IAroundInterceptor;
import com.ai.cloud.skywalking.plugin.interceptor.InterceptorDefine;

public class TestInterceptorDefine implements InterceptorDefine {

	@Override
	public String getBeInterceptedClassName() {
		return "test.ai.cloud.plugin.BeInterceptedClass";
	}

	@Override
	public String[] getBeInterceptedMethods() {
		return new String[] { "printabc" };
	}

	@Override
	public IAroundInterceptor instance() {
		return new TestAroundInterceptor();
	}

}
