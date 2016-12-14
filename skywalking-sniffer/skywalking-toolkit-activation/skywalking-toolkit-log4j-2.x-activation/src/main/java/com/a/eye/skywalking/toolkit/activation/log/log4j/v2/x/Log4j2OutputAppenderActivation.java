package com.a.eye.skywalking.toolkit.activation.log.log4j.v2.x;

import com.a.eye.skywalking.plugin.interceptor.MethodMatcher;
import com.a.eye.skywalking.plugin.interceptor.StaticMethodsInterceptPoint;
import com.a.eye.skywalking.plugin.interceptor.enhance.ClassStaticMethodsEnhancePluginDefine;
import com.a.eye.skywalking.plugin.interceptor.matcher.SimpleMethodMatcher;

/**
 * Created by wusheng on 2016/12/7.
 */
public class Log4j2OutputAppenderActivation extends ClassStaticMethodsEnhancePluginDefine {
    @Override
    protected String enhanceClassName() {
        return "com.a.eye.skywalking.toolkit.log.log4j.v2.x.Log4j2OutputAppender";
    }

    @Override
    protected StaticMethodsInterceptPoint[] getStaticMethodsInterceptPoints() {
        return new StaticMethodsInterceptPoint[]{new StaticMethodsInterceptPoint() {
            @Override
            public MethodMatcher[] getMethodsMatchers() {
                return new MethodMatcher[]{new SimpleMethodMatcher("append")};
            }

            @Override
            public String getMethodsInterceptor() {
                return "PrintTraceIdInterceptor";
            }
        }};
    }
}
