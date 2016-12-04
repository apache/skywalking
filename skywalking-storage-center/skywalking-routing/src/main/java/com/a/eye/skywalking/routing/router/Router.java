package com.a.eye.skywalking.routing.router;

import com.a.eye.skywalking.logging.api.ILog;
import com.a.eye.skywalking.logging.api.LogManager;
import com.a.eye.skywalking.network.grpc.AckSpan;
import com.a.eye.skywalking.network.grpc.RequestSpan;
import com.a.eye.skywalking.registry.api.RegistryNode;
import com.a.eye.skywalking.routing.client.StorageClientCachePool;
import com.a.eye.skywalking.routing.disruptor.NoopSpanDisruptor;
import com.a.eye.skywalking.routing.disruptor.SpanDisruptor;
import com.a.eye.skywalking.routing.storage.listener.NodeChangesListener;

import java.util.*;

public class Router implements NodeChangesListener {
    private static ILog logger = LogManager.getLogger(Router.class);

    private SpanDisruptor[] disruptors = new SpanDisruptor[0];
    private NoopSpanDisruptor noopSpanPool = new NoopSpanDisruptor();

    public SpanDisruptor lookup(RequestSpan requestSpan) {
        return getSpanDisruptor(requestSpan.getRouteKey());
    }

    public SpanDisruptor lookup(AckSpan ackSpan) {
        return getSpanDisruptor(ackSpan.getRouteKey());
    }

    private SpanDisruptor getSpanDisruptor(int routKey) {
        if (disruptors.length == 0) {
            return noopSpanPool;
        }

        while (true) {
            int index = routKey % disruptors.length;
            try {
                return disruptors[index];
            } catch (ArrayIndexOutOfBoundsException e) {
            }
        }
    }

    @Override
    public void notify(List<RegistryNode> registryNodes) {
        List<SpanDisruptor> newDisruptors = new ArrayList<SpanDisruptor>(Arrays.asList(disruptors));
        List<SpanDisruptor> removedDisruptors = new ArrayList<SpanDisruptor>();

        for (RegistryNode node : registryNodes) {
            if (node.getChangeType() == RegistryNode.ChangeType.ADDED) {
                newDisruptors.add(new SpanDisruptor(node.getNode()));
            } else {
                removedDisruptors.add(getAndRemoveSpanDistruptor(newDisruptors, node.getNode()));
            }
        }

        Collections.sort(newDisruptors, (o1, o2) -> {
            long o1Key = Long.parseLong(o1.getConnectionURL().replace(".", "").replace(":", ""));
            long o2Key = Long.parseLong(o2.getConnectionURL().replace(".", "").replace(":", ""));

            if (o1Key == o2Key) {
                return 0;
            } else if (o1Key > o2Key) {
                return 1;
            } else {
                return -1;
            }
        });

        //先停止往里面存放数据
        disruptors = newDisruptors.toArray(new SpanDisruptor[newDisruptors.size()]);

        // 而后stop
        for (SpanDisruptor removedDisruptor : removedDisruptors) {
            removedDisruptor.shutdown();
            StorageClientCachePool.INSTANCE.shutdown(removedDisruptor.getConnectionURL());
        }
    }

    private SpanDisruptor getAndRemoveSpanDistruptor(List<SpanDisruptor> newDisruptors, String connectionURL) {
        return newDisruptors.remove(newDisruptors.indexOf(new SpanDisruptor(connectionURL)));
    }

    public void stop() {
        logger.info("Stopping routing service.");
        for (SpanDisruptor disruptor : disruptors) {

        }
    }
}
