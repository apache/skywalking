package com.a.eye.skywalking.ui.creator;

import com.a.eye.skywalking.ui.config.UIConfig;
import com.a.eye.skywalking.ui.tools.ServerSelector;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @author pengys5
 */
@Component
public class UrlCreator {

    @Autowired
    private UIConfig uiConfig;

    @Autowired
    private ServerSelector serverSelector;

    public String compound(String urlSuffix) {
        String server = serverSelector.select(uiConfig.getServers());
        return "http://" + server + urlSuffix;
    }
}
