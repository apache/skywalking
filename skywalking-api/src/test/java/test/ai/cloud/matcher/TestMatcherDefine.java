package test.ai.cloud.matcher;

import com.ai.cloud.skywalking.plugin.interceptor.IAroundInterceptor;
import com.ai.cloud.skywalking.plugin.interceptor.InterceptorDefine;
import com.ai.cloud.skywalking.plugin.interceptor.MethodMatcher;
import com.ai.cloud.skywalking.plugin.interceptor.matcher.MethodsExclusiveMatcher;
import com.ai.cloud.skywalking.plugin.interceptor.matcher.PrivateMethodMatcher;
import com.ai.cloud.skywalking.plugin.interceptor.matcher.SimpleMethodMatcher;

/**
 * Created by xin on 16-6-8.
 */
public class TestMatcherDefine implements InterceptorDefine {
    @Override
    public String getBeInterceptedClassName() {
        return "test.ai.cloud.matcher.TestMatcherClass";
    }

    @Override
    public MethodMatcher[] getBeInterceptedMethodsMatchers() {
        return new MethodMatcher[]{
                new PrivateMethodMatcher(),
                new MethodsExclusiveMatcher(new SimpleMethodMatcher("set")),
                new SimpleMethodMatcher(MethodMatcher.Modifier.Private, "set", 1)
        };
        //return new MethodMatcher[] { new SimpleMethodMatcher(Modifier.Public, "printabc", new Class[]{String.class, String.class}) };
        //return new MethodMatcher[] { new PrivateMethodMatcher()};
        //return new MethodMatcher[]{new AnyMethodsMatcher()};
        //return new MethodMatcher[]{new MethodsExclusiveMatcher(new SimpleMethodMatcher("set"), new SimpleMethodMatcher(MethodMatcher.Modifier.Public,"get"))};
    }

    @Override
    public IAroundInterceptor instance() {
        return new TestAroundInterceptor();
    }
}
