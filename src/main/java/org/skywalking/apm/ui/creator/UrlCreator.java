package org.skywalking.apm.ui.creator;

import org.skywalking.apm.ui.config.UIConfig;
import org.skywalking.apm.ui.tools.ServerSelector;
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
