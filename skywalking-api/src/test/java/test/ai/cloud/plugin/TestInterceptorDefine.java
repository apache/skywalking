package test.ai.cloud.plugin;

import com.ai.cloud.skywalking.plugin.interceptor.IAroundInterceptor;
import com.ai.cloud.skywalking.plugin.interceptor.InterceptorDefine;
import com.ai.cloud.skywalking.plugin.interceptor.MethodMatcher;
import com.ai.cloud.skywalking.plugin.interceptor.matcher.SimpleMethodMatcher;

public class TestInterceptorDefine implements InterceptorDefine {

	@Override
	public String getBeInterceptedClassName() {
		return "test.ai.cloud.plugin.BeInterceptedClass";
	}

	@Override
	public MethodMatcher[] getBeInterceptedMethodsMatchers() {
		return new MethodMatcher[] { new SimpleMethodMatcher("printabc") };
	}

	@Override
	public IAroundInterceptor instance() {
		return new TestAroundInterceptor();
	}

}
