package org.skywalking.apm.collector.agentregister.grpc.handler;

import com.google.protobuf.ProtocolStringList;
import io.grpc.stub.StreamObserver;
import org.skywalking.apm.collector.agentregister.application.ApplicationIDGetOrCreate;
import org.skywalking.apm.collector.server.grpc.GRPCHandler;
import org.skywalking.apm.network.proto.Application;
import org.skywalking.apm.network.proto.ApplicationMapping;
import org.skywalking.apm.network.proto.ApplicationRegisterServiceGrpc;
import org.skywalking.apm.network.proto.KeyWithIntegerValue;

/**
 * @author pengys5
 */
public class ApplicationRegisterServiceHandler extends ApplicationRegisterServiceGrpc.ApplicationRegisterServiceImplBase implements GRPCHandler {

    private ApplicationIDGetOrCreate applicationIDGetOrCreate = new ApplicationIDGetOrCreate();

    @Override public void register(Application request, StreamObserver<ApplicationMapping> responseObserver) {
        ProtocolStringList applicationCodes = request.getApplicationCodeList();
        for (int i = 0; i < applicationCodes.size(); i++) {
            String applicationCode = applicationCodes.get(i);
            int applicationId = applicationIDGetOrCreate.getOrCreate(applicationCode);

            KeyWithIntegerValue value = KeyWithIntegerValue.newBuilder().setKey(applicationCode).setValue(applicationId).build();
            ApplicationMapping mapping = ApplicationMapping.newBuilder().setApplication(i, value).build();
            responseObserver.onNext(mapping);
        }
        responseObserver.onCompleted();
    }
}
