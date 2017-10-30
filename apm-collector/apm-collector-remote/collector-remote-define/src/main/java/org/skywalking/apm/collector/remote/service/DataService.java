package org.skywalking.apm.collector.remote.service;

import org.skywalking.apm.collector.core.module.Service;

/**
 * @author peng-yongsheng
 */
public interface DataService extends Service {
    void send(Data data);

    void registerReceiver(DataReceiver receiver);
}
