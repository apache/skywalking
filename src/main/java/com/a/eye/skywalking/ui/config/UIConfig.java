package com.a.eye.skywalking.ui.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * @author pengys5
 */
@ConfigurationProperties(prefix = "collector")
@PropertySource("classpath:collector_config.properties")
@Component
public class UIConfig {

    private List<String> servers = new ArrayList<>();

    public List<String> getServers() {
        return servers;
    }

    public void setServers(List<String> servers) {
        this.servers = servers;
    }
}
