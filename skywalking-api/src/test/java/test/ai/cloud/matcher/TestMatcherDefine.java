package test.ai.cloud.matcher;

import com.ai.cloud.skywalking.plugin.interceptor.IAroundInterceptor;
import com.ai.cloud.skywalking.plugin.interceptor.InterceptorDefine;
import com.ai.cloud.skywalking.plugin.interceptor.MethodMatcher;
import com.ai.cloud.skywalking.plugin.interceptor.matcher.MethodsExclusiveMatcher;
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
                new MethodsExclusiveMatcher("set", "get"),
                new SimpleMethodMatcher(MethodMatcher.Modifier.Private, "set")
        };
    }

    @Override
    public IAroundInterceptor instance() {
        return new TestAroundInterceptor();
    }
}
