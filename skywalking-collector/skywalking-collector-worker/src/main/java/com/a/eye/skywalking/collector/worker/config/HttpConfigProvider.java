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
        if (!StringUtil.isEmpty(System.getProperty("http.HOSTNAME"))) {
            HttpConfig.Http.HOSTNAME = System.getProperty("http.HOSTNAME");
        }
        if (!StringUtil.isEmpty(System.getProperty("http.PORT"))) {
            HttpConfig.Http.PORT = System.getProperty("http.PORT");
        }
        if (!StringUtil.isEmpty(System.getProperty("http.CONTEXTPATH"))) {
            HttpConfig.Http.CONTEXTPATH = System.getProperty("http.CONTEXTPATH");
        }
    }
}
