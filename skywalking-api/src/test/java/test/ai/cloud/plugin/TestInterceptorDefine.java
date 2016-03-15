package test.ai.cloud.plugin;

import com.ai.cloud.skywalking.plugin.interceptor.IAroundInterceptor;
import com.ai.cloud.skywalking.plugin.interceptor.InterceptPoint;
import com.ai.cloud.skywalking.plugin.interceptor.InterceptorDefine;

public class TestInterceptorDefine implements InterceptorDefine {

	@Override
	public String getBeInterceptedClassName() {
		return "test.ai.cloud.plugin.BeInterceptedClass";
	}

	@Override
	public InterceptPoint[] getBeInterceptedMethods() {
		return new InterceptPoint[] { new InterceptPoint("printabc") };
	}

	@Override
	public IAroundInterceptor instance() {
		return new TestAroundInterceptor();
	}

}
