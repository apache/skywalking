package test.ai.cloud.plugin;

import com.ai.cloud.skywalking.plugin.interceptor.MethodMatcher;
import com.ai.cloud.skywalking.plugin.interceptor.enhance.ClassEnhancePluginDefine;
import com.ai.cloud.skywalking.plugin.interceptor.enhance.IntanceMethodsAroundInterceptor;
import com.ai.cloud.skywalking.plugin.interceptor.enhance.StaticMethodsAroundInterceptor;
import com.ai.cloud.skywalking.plugin.interceptor.matcher.SimpleMethodMatcher;

public class TestInterceptorDefine extends ClassEnhancePluginDefine {

	@Override
	public String getBeInterceptedClassName() {
		return "test.ai.cloud.plugin.BeInterceptedClass";
	}

	@Override
	public MethodMatcher[] getInstanceMethodsMatchers() {
		return new MethodMatcher[] { new SimpleMethodMatcher("printabc") };
	}

	@Override
	public IntanceMethodsAroundInterceptor getInstanceMethodsInterceptor() {
		return new TestAroundInterceptor();
	}

	@Override
	protected MethodMatcher[] getStaticMethodsMatchers() {
		return new MethodMatcher[] { new SimpleMethodMatcher("call") };
	}

	@Override
	protected StaticMethodsAroundInterceptor getStaticMethodsInterceptor() {
		return new TestStaticAroundInterceptor();
	}

}
