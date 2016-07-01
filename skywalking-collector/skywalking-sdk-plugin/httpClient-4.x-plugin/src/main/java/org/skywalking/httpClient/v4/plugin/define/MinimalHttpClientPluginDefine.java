package org.skywalking.httpClient.v4.plugin.define;

import com.ai.cloud.skywalking.plugin.interceptor.MethodMatcher;
import com.ai.cloud.skywalking.plugin.interceptor.matcher.SimpleMethodMatcher;

public class MinimalHttpClientPluginDefine extends HttpClientPluginDefine {
    @Override
    public MethodMatcher[] getInstanceMethodsMatchers() {
        return new MethodMatcher[]{new SimpleMethodMatcher("doExecute")};
    }

    @Override
    public String enhanceClassName() {
        return "org.apache.http.impl.client.MinimalHttpClient";
    }

}
