package org.skywalking.apm.collector.agentstream.mock.grpc;

import io.grpc.ManagedChannel;
import org.skywalking.apm.network.proto.Application;
import org.skywalking.apm.network.proto.ApplicationMapping;
import org.skywalking.apm.network.proto.ApplicationRegisterServiceGrpc;

/**
 * @author pengys5
 */
public class ApplicationRegister {

    public static int register(ManagedChannel channel, String applicationCode) {
        ApplicationRegisterServiceGrpc.ApplicationRegisterServiceBlockingStub stub = ApplicationRegisterServiceGrpc.newBlockingStub(channel);
        Application application = Application.newBuilder().addApplicationCode(applicationCode).build();
        ApplicationMapping mapping = stub.register(application);
        int applicationId = mapping.getApplication(0).getValue();

        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
        }
        return applicationId;
    }
}
