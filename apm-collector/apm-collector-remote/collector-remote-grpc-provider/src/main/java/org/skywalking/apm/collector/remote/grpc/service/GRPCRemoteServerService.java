package org.skywalking.apm.collector.remote.grpc.service;

import org.skywalking.apm.collector.remote.service.DataReceiver;
import org.skywalking.apm.collector.remote.service.DataReceiverRegisterListener;
import org.skywalking.apm.collector.remote.service.RemoteServerService;

/**
 * @author peng-yongsheng
 */
public class GRPCRemoteServerService implements RemoteServerService {

    private DataReceiverRegisterListener listener;

    public GRPCRemoteServerService(DataReceiverRegisterListener listener) {
        this.listener = listener;
    }

    @Override public void registerReceiver(DataReceiver receiver) {
        listener.setDataReceiver(receiver);
    }
}
