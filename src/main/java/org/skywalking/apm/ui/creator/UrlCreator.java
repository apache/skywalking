package org.skywalking.apm.ui.creator;

import java.util.ArrayList;
import java.util.List;
import org.skywalking.apm.ui.tools.ServerSelector;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @author pengys5
 */
@Component
public class UrlCreator {

    private List<String> servers;
    private boolean lock = false;

    public UrlCreator() {
        servers = new ArrayList<>();
    }

    @Autowired
    private ServerSelector serverSelector;

    public String compound(String urlSuffix) {
        while (lock) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
            }
        }
        String server = serverSelector.select(servers);
        return "http://" + server + urlSuffix;
    }

    public void addServers(List<String> servers) {
        try {
            this.servers.clear();
            this.servers.addAll(servers);
        } finally {
            lock = false;
        }
    }
}
