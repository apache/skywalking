package org.skywalking.apm.collector.stream.grpc.handler;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import io.grpc.stub.StreamObserver;
import org.skywalking.apm.collector.cluster.ClusterModuleGroupDefine;
import org.skywalking.apm.collector.core.framework.CollectorContextHelper;
import org.skywalking.apm.collector.remote.grpc.proto.Empty;
import org.skywalking.apm.collector.remote.grpc.proto.Message;
import org.skywalking.apm.collector.remote.grpc.proto.RemoteCommonServiceGrpc;
import org.skywalking.apm.collector.server.grpc.GRPCHandler;
import org.skywalking.apm.collector.stream.StreamModuleContext;
import org.skywalking.apm.collector.stream.worker.WorkerInvokeException;
import org.skywalking.apm.collector.stream.worker.WorkerNotFoundException;
import org.skywalking.apm.collector.stream.worker.impl.data.Data;
import org.skywalking.apm.collector.stream.worker.impl.data.DataDefine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pengys5
 */
public class RemoteCommonServiceHandler extends RemoteCommonServiceGrpc.RemoteCommonServiceImplBase implements GRPCHandler {

    private final Logger logger = LoggerFactory.getLogger(RemoteCommonServiceHandler.class);

    @Override public void call(Message request, StreamObserver<Empty> responseObserver) {
        String roleName = request.getWorkerRole();
        int dataDefineId = request.getDataDefineId();
        ByteString bytesData = request.getDataBytes();

        StreamModuleContext context = (StreamModuleContext)CollectorContextHelper.INSTANCE.getContext(ClusterModuleGroupDefine.GROUP_NAME);
        DataDefine dataDefine = context.getDataDefine(dataDefineId);

        try {
            Data data = dataDefine.parseFrom(bytesData);
            context.getClusterWorkerContext().lookup(context.getClusterWorkerContext().getRole(roleName)).tell(data);
        } catch (InvalidProtocolBufferException | WorkerNotFoundException | WorkerInvokeException e) {
            logger.error(e.getMessage(), e);
        }
    }
}
