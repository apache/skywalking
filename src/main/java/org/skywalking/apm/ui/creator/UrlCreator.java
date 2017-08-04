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

    private volatile boolean inited = false;

    private Object waiter = new Object();

    public UrlCreator() {
        servers = new ArrayList<>();
    }

    @Autowired
    private ServerSelector serverSelector;

    public String compound(String urlSuffix) {
        if (!inited) {
            synchronized (waiter) {
                try {
                    waiter.wait(100);
                } catch (InterruptedException e) {
                }
            }
        }
        String server = serverSelector.select(servers);
        return "http://" + server + urlSuffix;
    }

    public void updateServerList(List<String> servers) {
        if (null == servers || servers.isEmpty()) {
            return;
        }
        try {
            this.servers.clear();
            this.servers.addAll(servers);
        } finally {
            inited = true;
            synchronized (waiter) {
                waiter.notifyAll();
            }
        }
    }
}
