package org.skywalking.httpClient.v4.plugin.define;

import com.ai.cloud.skywalking.plugin.interceptor.MethodNameMatcher;
import com.ai.cloud.skywalking.plugin.interceptor.matcher.FullNameMatcher;

public class MinimalHttpClientPluginDefine extends HttpClientPluginDefine {
    @Override
    public MethodNameMatcher[] getBeInterceptedMethods() {
        return new MethodNameMatcher[]{new FullNameMatcher("doExecute")};
    }

    @Override
    public String getBeInterceptedClassName() {
        return "org.apache.http.impl.client.MinimalHttpClient";
    }

}
