package test.a.eye.cloud.plugin;

import com.a.eye.skywalking.plugin.interceptor.MethodMatcher;
import com.a.eye.skywalking.plugin.interceptor.enhance.ClassEnhancePluginDefine;
import com.a.eye.skywalking.plugin.interceptor.matcher.SimpleMethodMatcher;

public class TestInterceptorDefine extends ClassEnhancePluginDefine {

	@Override
	public String enhanceClassName() {
		return "BeInterceptedClass";
	}

	@Override
	public MethodMatcher[] getInstanceMethodsMatchers() {
		return new MethodMatcher[] { new SimpleMethodMatcher("printabc") };
	}

	@Override
	public String getInstanceMethodsInterceptor() {
		return "TestAroundInterceptor";
	}

	@Override
	protected MethodMatcher[] getStaticMethodsMatchers() {
		return new MethodMatcher[] { new SimpleMethodMatcher("call") };
	}

	@Override
	protected String getStaticMethodsInterceptor() {
		return "TestStaticAroundInterceptor";
	}

}
