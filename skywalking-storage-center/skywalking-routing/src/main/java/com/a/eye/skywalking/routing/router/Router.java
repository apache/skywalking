package com.a.eye.skywalking.routing.router;

import com.a.eye.skywalking.logging.api.ILog;
import com.a.eye.skywalking.logging.api.LogManager;
import com.a.eye.skywalking.network.grpc.AckSpan;
import com.a.eye.skywalking.network.grpc.RequestSpan;
import com.a.eye.skywalking.routing.disruptor.NoopSpanDisruptor;
import com.a.eye.skywalking.routing.disruptor.SpanDisruptor;
import com.a.eye.skywalking.routing.storage.listener.NodeChangesListener;
import com.a.eye.skywalking.routing.storage.listener.NotifyListenerImpl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

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

        while(true){
            int index = routKey % disruptors.length;
            try {
                return disruptors[index];
            }catch (ArrayIndexOutOfBoundsException e){
            }
        }
    }

    @Override
    public void notify(List<String> connectionURL, NotifyListenerImpl.ChangeType changeType) {
        List<SpanDisruptor> newDisruptors = null;
        if (changeType == NotifyListenerImpl.ChangeType.Add) {
            newDisruptors = new ArrayList<>(this.disruptors.length + connectionURL.size());
            for (String url : connectionURL) {
                newDisruptors.add(new SpanDisruptor(url));
            }
        }

        if (changeType == NotifyListenerImpl.ChangeType.Removed) {
            newDisruptors = new ArrayList<SpanDisruptor>(disruptors.length - connectionURL.size());
            for (SpanDisruptor disruptor : disruptors) {
                if (!connectionURL.contains(disruptor.getConnectionURL())) {
                    newDisruptors.add(disruptor);
                }
            }
        }

        Collections.sort(newDisruptors, new Comparator<SpanDisruptor>() {
            @Override
            public int compare(SpanDisruptor o1, SpanDisruptor o2) {
                long o1Key = Long.parseLong(o1.getConnectionURL().replace(".", "").replace(":", ""));
                long o2Key = Long.parseLong(o2.getConnectionURL().replace(".", "").replace(":", ""));

                if (o1Key == o2Key) {
                    return 0;
                } else if (o1Key > o2Key) {
                    return 1;
                } else {
                    return -1;
                }
            }
        });

        disruptors = newDisruptors.toArray(new SpanDisruptor[newDisruptors.size()]);
    }

    public void stop() {
        logger.info("Stopping routing service.");
        for (SpanDisruptor disruptor : disruptors) {

        }
    }
}
