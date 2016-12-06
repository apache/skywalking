package com.a.eye.skywalking.web.client.routing;

import com.a.eye.skywalking.network.Client;
import com.a.eye.skywalking.network.grpc.client.TraceSearchClient;
import com.a.eye.skywalking.registry.api.NotifyListener;
import com.a.eye.skywalking.registry.api.RegistryNode;

import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by wusheng on 2016/12/6.
 */
public class RoutingServerWatcher implements NotifyListener {
    private ReentrantLock lock = new ReentrantLock();

    private static Map<String, TraceSearchClient> allSearchClients = new HashMap();
    private static Map<String, Client>            allClient        = new HashMap<>();


    public static TraceSearchClient[] getAllSearchClient(){
        return allSearchClients.values().toArray(new TraceSearchClient[0]);
    }

    @Override
    public void notify(List<RegistryNode> registryNodes) {
        lock.lock();
        try {
            registryNodes.forEach((each) -> {
                if (RegistryNode.ChangeType.ADDED.equals(each.getChangeType())) {
                    String connectionURL = each.getNode();
                    String[] urlSegment = connectionURL.split(":");
                    if (urlSegment.length != 2) {
                        throw new IllegalArgumentException();
                    }
                    Client client = new Client(urlSegment[0], Integer.valueOf(urlSegment[1]));
                    TraceSearchClient traceSearchClient = client.newTraceSearchClient();
                    allClient.put(connectionURL, client);
                    allSearchClients.put(connectionURL, traceSearchClient);
                } else {
                    allSearchClients.remove(each.getNode());
                    allClient.remove(each.getNode()).shutdown();
                }
            });
        } finally {
            lock.unlock();
        }
    }
}
