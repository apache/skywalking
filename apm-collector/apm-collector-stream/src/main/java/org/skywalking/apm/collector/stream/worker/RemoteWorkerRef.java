package org.skywalking.apm.collector.stream.worker;

import org.skywalking.apm.collector.client.grpc.GRPCClient;
import org.skywalking.apm.collector.remote.grpc.proto.RemoteCommonServiceGrpc;
import org.skywalking.apm.collector.remote.grpc.proto.RemoteData;
import org.skywalking.apm.collector.remote.grpc.proto.RemoteMessage;

/**
 * @author pengys5
 */
public class RemoteWorkerRef extends WorkerRef {

    private final Boolean acrossJVM;
    private final RemoteCommonServiceGrpc.RemoteCommonServiceBlockingStub stub;
    private final AbstractRemoteWorker remoteWorker;

    public RemoteWorkerRef(Role role, AbstractRemoteWorker remoteWorker) {
        super(role);
        this.remoteWorker = remoteWorker;
        this.acrossJVM = false;
        this.stub = null;
    }

    public RemoteWorkerRef(Role role, GRPCClient client) {
        super(role);
        this.remoteWorker = null;
        this.acrossJVM = true;
        this.stub = RemoteCommonServiceGrpc.newBlockingStub(client.getChannel());
    }

    @Override
    public void tell(Object message) throws WorkerInvokeException {
        if (acrossJVM) {
            RemoteData remoteData = getRole().dataDefine().serialize(message);

            RemoteMessage.Builder builder = RemoteMessage.newBuilder();
            builder.setWorkerRole(getRole().roleName());
            builder.setRemoteData(remoteData);
            stub.call(builder.build());
        } else {
            remoteWorker.allocateJob(message);
        }
    }

    public Boolean isAcrossJVM() {
        return acrossJVM;
    }
}
