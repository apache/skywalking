package org.skywalking.apm.collector.remote.service;

/**
 * @author peng-yongsheng
 */
public interface DataReceiver {
    void receive(Data data);
}
