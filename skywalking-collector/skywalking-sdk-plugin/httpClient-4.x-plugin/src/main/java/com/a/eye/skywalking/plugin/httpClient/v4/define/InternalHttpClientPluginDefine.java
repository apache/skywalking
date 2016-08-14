package com.a.eye.skywalking.plugin.httpClient.v4.define;

import com.a.eye.skywalking.plugin.interceptor.matcher.SimpleMethodMatcher;
import com.a.eye.skywalking.plugin.interceptor.MethodMatcher;

public class InternalHttpClientPluginDefine extends HttpClientPluginDefine {
    @Override
    public MethodMatcher[] getInstanceMethodsMatchers() {
        return new MethodMatcher[]{new SimpleMethodMatcher("doExecute")};
    }

    @Override
    public String enhanceClassName() {
        return "org.apache.http.impl.client.InternalHttpClient";
    }

}
