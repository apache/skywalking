package org.skywalking.apm.collector.agentregister.grpc.handler;

import com.google.protobuf.ProtocolStringList;
import io.grpc.stub.StreamObserver;
import org.skywalking.apm.collector.agentregister.application.ApplicationIDService;
import org.skywalking.apm.collector.server.grpc.GRPCHandler;
import org.skywalking.apm.network.proto.Application;
import org.skywalking.apm.network.proto.ApplicationMapping;
import org.skywalking.apm.network.proto.ApplicationRegisterServiceGrpc;
import org.skywalking.apm.network.proto.KeyWithIntegerValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pengys5
 */
public class ApplicationRegisterServiceHandler extends ApplicationRegisterServiceGrpc.ApplicationRegisterServiceImplBase implements GRPCHandler {

    private final Logger logger = LoggerFactory.getLogger(ApplicationRegisterServiceHandler.class);

    private ApplicationIDService applicationIDService = new ApplicationIDService();

    @Override public void register(Application request, StreamObserver<ApplicationMapping> responseObserver) {
        logger.debug("register application");
        ProtocolStringList applicationCodes = request.getApplicationCodeList();

        ApplicationMapping.Builder builder = ApplicationMapping.newBuilder();
        for (int i = 0; i < applicationCodes.size(); i++) {
            String applicationCode = applicationCodes.get(i);
            int applicationId = applicationIDService.getOrCreate(applicationCode);

            if (applicationId != 0) {
                KeyWithIntegerValue value = KeyWithIntegerValue.newBuilder().setKey(applicationCode).setValue(applicationId).build();
                builder.addApplication(value);
            }
        }
        responseObserver.onNext(builder.build());
        responseObserver.onCompleted();
    }
}
