package test.a.eye.cloud.plugin;

import com.a.eye.skywalking.plugin.interceptor.ConstructorInterceptPoint;
import com.a.eye.skywalking.plugin.interceptor.InstanceMethodsInterceptPoint;
import com.a.eye.skywalking.plugin.interceptor.MethodMatcher;
import com.a.eye.skywalking.plugin.interceptor.StaticMethodsInterceptPoint;
import com.a.eye.skywalking.plugin.interceptor.enhance.ClassEnhancePluginDefine;
import com.a.eye.skywalking.plugin.interceptor.matcher.SimpleMethodMatcher;

public class TestInterceptorDefine extends ClassEnhancePluginDefine {

	@Override
	public String enhanceClassName() {
		return "BeInterceptedClass";
	}

	@Override
	protected ConstructorInterceptPoint[] getConstructorsInterceptPoints() {
		return null;
	}

	@Override
	protected InstanceMethodsInterceptPoint[] getInstanceMethodsInterceptPoints() {
		return new InstanceMethodsInterceptPoint[]{new InstanceMethodsInterceptPoint() {
			@Override
			public MethodMatcher[] getMethodsMatchers() {
				return new MethodMatcher[]{new SimpleMethodMatcher("printabc")};
			}

			@Override
			public String getMethodsInterceptor() {
			return "TestAroundInterceptor";
			}
		}};
	}

	@Override
	protected StaticMethodsInterceptPoint[] getStaticMethodsInterceptPoints() {
		return new StaticMethodsInterceptPoint[]{new StaticMethodsInterceptPoint() {
			@Override
			public MethodMatcher[] getMethodsMatchers() {
				return new MethodMatcher[]{new SimpleMethodMatcher("call")};
			}

			@Override
			public String getMethodsInterceptor() {
				return "TestStaticAroundInterceptor";
			}
		}};
	}
}
