package org.skywalking.apm.agent.core.collector;

public interface Sender<V> {
    void send(V data) throws Exception;
}
