package test.ai.cloud.plugin;

import com.ai.cloud.skywalking.plugin.interceptor.IAroundInterceptor;
import com.ai.cloud.skywalking.plugin.interceptor.InterceptPoint;
import com.ai.cloud.skywalking.plugin.interceptor.InterceptorDefine;
import com.ai.cloud.skywalking.plugin.interceptor.MethodNameMatcher;
import com.ai.cloud.skywalking.plugin.interceptor.matcher.FullNameMatcher;

public class TestInterceptorDefine implements InterceptorDefine {

	@Override
	public String getBeInterceptedClassName() {
		return "test.ai.cloud.plugin.BeInterceptedClass";
	}

	@Override
	public MethodNameMatcher[] getBeInterceptedMethods() {
		return new MethodNameMatcher[] { new FullNameMatcher("printabc") };
	}

	@Override
	public IAroundInterceptor instance() {
		return new TestAroundInterceptor();
	}

}
