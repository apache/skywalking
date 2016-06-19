package test.ai.cloud.matcher;

import com.ai.cloud.skywalking.plugin.interceptor.MethodMatcher;
import com.ai.cloud.skywalking.plugin.interceptor.enhance.ClassInstanceMethodsEnhancePluginDefine;
import com.ai.cloud.skywalking.plugin.interceptor.enhance.IntanceMethodsAroundInterceptor;
import com.ai.cloud.skywalking.plugin.interceptor.matcher.PrivateMethodMatcher;

/**
 * Created by xin on 16-6-8.
 */
public class TestMatcherDefine extends ClassInstanceMethodsEnhancePluginDefine {
    @Override
    public String getBeInterceptedClassName() {
        return "test.ai.cloud.matcher.TestMatcherClass";
    }

    @Override
    protected MethodMatcher[] getInstanceMethodsMatchers() {
//        return new MethodMatcher[]{
//                new PrivateMethodMatcher(),
//                new MethodsExclusiveMatcher(new SimpleMethodMatcher("set")),
//                new SimpleMethodMatcher(MethodMatcher.Modifier.Private, "set", 1)
//        };
//        return new MethodMatcher[] { new SimpleMethodMatcher(Modifier.Public, "printabc", new Class[]{String.class, String.class}) };
        return new MethodMatcher[] { new PrivateMethodMatcher()};
        //return new MethodMatcher[]{new AnyMethodsMatcher()};
        //return new MethodMatcher[]{new MethodsExclusiveMatcher(new SimpleMethodMatcher("set"), new SimpleMethodMatcher(MethodMatcher.Modifier.Public,"get"))};
    }

    @Override
    protected IntanceMethodsAroundInterceptor getInstanceMethodsInterceptor() {
        return new TestAroundInterceptor();
    }
}
