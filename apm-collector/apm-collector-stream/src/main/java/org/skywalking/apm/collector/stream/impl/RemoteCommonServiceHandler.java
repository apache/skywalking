package org.skywalking.apm.collector.stream.impl;

import com.google.protobuf.ByteString;
import io.grpc.stub.StreamObserver;
import org.skywalking.apm.collector.remote.grpc.proto.Empty;
import org.skywalking.apm.collector.remote.grpc.proto.Message;
import org.skywalking.apm.collector.remote.grpc.proto.RemoteCommonServiceGrpc;
import org.skywalking.apm.collector.server.grpc.GRPCHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pengys5
 */
public class RemoteCommonServiceHandler extends RemoteCommonServiceGrpc.RemoteCommonServiceImplBase implements GRPCHandler {

    private final Logger logger = LoggerFactory.getLogger(RemoteCommonServiceHandler.class);

    @Override public void call(Message request, StreamObserver<Empty> responseObserver) {
        String workerRole = request.getWorkerRole();
        int dataDefineId = request.getDataDefineId();
        ByteString bytesData = request.getDataBytes();
    }
}
