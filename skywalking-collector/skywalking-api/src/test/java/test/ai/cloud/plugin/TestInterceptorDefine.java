package test.ai.cloud.plugin;

import com.ai.cloud.skywalking.plugin.interceptor.MethodMatcher;
import com.ai.cloud.skywalking.plugin.interceptor.enhance.ClassEnhancePluginDefine;
import com.ai.cloud.skywalking.plugin.interceptor.enhance.StaticMethodsAroundInterceptor;
import com.ai.cloud.skywalking.plugin.interceptor.matcher.SimpleMethodMatcher;

public class TestInterceptorDefine extends ClassEnhancePluginDefine {

	@Override
	public String enhanceClassName() {
		return "test.ai.cloud.plugin.BeInterceptedClass";
	}

	@Override
	public MethodMatcher[] getInstanceMethodsMatchers() {
		return new MethodMatcher[] { new SimpleMethodMatcher("printabc") };
	}

	@Override
	public String getInstanceMethodsInterceptor() {
		return "test.ai.cloud.plugin.TestAroundInterceptor";
	}

	@Override
	protected MethodMatcher[] getStaticMethodsMatchers() {
		return new MethodMatcher[] { new SimpleMethodMatcher("call") };
	}

	@Override
	protected String getStaticMethodsInterceptor() {
		return "test.ai.cloud.plugin.TestStaticAroundInterceptor";
	}

}
