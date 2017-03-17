package com.a.eye.skywalking.ui.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * @author pengys5
 */
@ConfigurationProperties(prefix = "node")
@PropertySource("classpath:config/imagebase64config.properties")
@Component
public class ImageBase64Config {

    private Map<String, String> image = new HashMap();

    public Map<String, String> getImage() {
        return image;
    }

    public void setImage(Map<String, String> image) {
        this.image = image;
    }
}
