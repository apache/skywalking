package test.a.eye.cloud.matcher;

import com.a.eye.skywalking.plugin.interceptor.ConstructorInterceptPoint;
import com.a.eye.skywalking.plugin.interceptor.InstanceMethodsInterceptPoint;
import com.a.eye.skywalking.plugin.interceptor.MethodMatcher;
import com.a.eye.skywalking.plugin.interceptor.enhance.ClassInstanceMethodsEnhancePluginDefine;
import com.a.eye.skywalking.plugin.interceptor.matcher.PrivateMethodMatcher;

/**
 * Created by xin on 16-6-8.
 */
public class TestMatcherDefine extends ClassInstanceMethodsEnhancePluginDefine {
    @Override
    public String enhanceClassName() {
        return "test.a.eye.cloud.matcher.TestMatcherClass";
    }

    @Override
    protected ConstructorInterceptPoint getConstructorsInterceptPoint() {
        return null;
    }

    @Override
    protected InstanceMethodsInterceptPoint[] getInstanceMethodsInterceptPoints() {
        return new InstanceMethodsInterceptPoint[] {new InstanceMethodsInterceptPoint() {
            @Override
            public MethodMatcher[] getMethodsMatchers() {
                //        return new MethodMatcher[]{
                //                new PrivateMethodMatcher(),
                //                new MethodsExclusiveMatcher(new SimpleMethodMatcher("set")),
                //                new SimpleMethodMatcher(MethodMatcher.Modifier.Private, "set", 1)
                //        };
                //        return new MethodMatcher[] { new SimpleMethodMatcher(Modifier.Public, "printabc", new Class[]{String.class, String.class}) };
                return new MethodMatcher[] {new PrivateMethodMatcher()};
                //return new MethodMatcher[]{new AnyMethodsMatcher()};
                //return new MethodMatcher[]{new MethodsExclusiveMatcher(new SimpleMethodMatcher("set"), new SimpleMethodMatcher(MethodMatcher.Modifier.Public,"get"))};
            }

            @Override
            public String getMethodsInterceptor() {
                return "test.a.eye.cloud.matcher.TestAroundInterceptor";
            }
        }};
    }
}
