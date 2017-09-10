package org.skywalking.apm.collector.core.server;

import java.util.LinkedList;
import java.util.List;
import org.skywalking.apm.collector.core.framework.Handler;
import org.skywalking.apm.collector.core.util.CollectionUtils;
import org.skywalking.apm.collector.core.util.ObjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pengys5
 */
public class ServerHolder {

    private final Logger logger = LoggerFactory.getLogger(ServerHolder.class);

    private List<Server> servers;

    public ServerHolder() {
        servers = new LinkedList<>();
    }

    public void holdServer(Server newServer, List<Handler> handlers) throws ServerException {
        if (ObjectUtils.isEmpty(newServer) || CollectionUtils.isEmpty(handlers)) {
            return;
        }

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
            handlers.forEach(handler -> {
                server.addHandler(handler);
                logger.debug("add handler into server: {}, handler name: {}", server.hostPort(), handler.getClass().getName());
            });
        }
    }

    public List<Server> getServers() {
        return servers;
    }
}
