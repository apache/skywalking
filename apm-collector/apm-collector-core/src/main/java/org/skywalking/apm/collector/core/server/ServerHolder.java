package org.skywalking.apm.collector.core.server;

import java.util.LinkedList;
import java.util.List;
import org.skywalking.apm.collector.core.framework.Handler;
import org.skywalking.apm.collector.core.util.CollectionUtils;

/**
 * @author pengys5
 */
public class ServerHolder {

    private List<Server> servers;

    public ServerHolder() {
        servers = new LinkedList<>();
    }

    public void holdServer(Server newServer, List<Handler> handlers) throws ServerException {
        boolean isNewServer = true;
        for (Server server : servers) {
            if (server.hostPort().equals(newServer.hostPort()) && server.serverClassify().equals(newServer.serverClassify())) {
                isNewServer = false;
                addHandler(handlers, server);
            }
        }
        if (isNewServer) {
            newServer.initialize();
            servers.add(newServer);
            addHandler(handlers, newServer);
        }
    }

    private void addHandler(List<Handler> handlers, Server server) {
        if (CollectionUtils.isNotEmpty(handlers)) {
            handlers.forEach(handler -> server.addHandler(handler));
        }
    }

    public List<Server> getServers() {
        return servers;
    }
}
