package com.a.eye.skywalking.collector.worker.config;

import com.a.eye.skywalking.api.util.StringUtil;
import com.a.eye.skywalking.collector.config.ConfigProvider;

/**
 * @author pengys5
 */
public class HttpConfigProvider implements ConfigProvider {

    @Override
    public Class configClass() {
        return HttpConfig.class;
    }

    @Override
    public void cliArgs() {
        if (!StringUtil.isEmpty(System.getProperty("http.hostname"))) {
            HttpConfig.Http.hostname = System.getProperty("http.hostname");
        }
        if (!StringUtil.isEmpty(System.getProperty("http.port"))) {
            HttpConfig.Http.port = System.getProperty("http.port");
        }
        if (!StringUtil.isEmpty(System.getProperty("http.contextPath"))) {
            HttpConfig.Http.contextPath = System.getProperty("http.contextPath");
        }
    }
}
