package org.skywalking.apm.collector.agentregister.grpc.handler;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.junit.Test;
import org.skywalking.apm.network.proto.Application;
import org.skywalking.apm.network.proto.ApplicationMapping;
import org.skywalking.apm.network.proto.ApplicationRegisterServiceGrpc;

/**
 * @author pengys5
 */
public class ApplicationRegisterServiceHandlerTestCase {

    private ApplicationRegisterServiceGrpc.ApplicationRegisterServiceBlockingStub stub;

    @Test
    public void testRegister() {
        ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 11800).usePlaintext(true).build();
        stub = ApplicationRegisterServiceGrpc.newBlockingStub(channel);

        Application application = Application.newBuilder().addApplicationCode("test141").build();
        ApplicationMapping mapping = stub.register(application);
        System.out.println(mapping.getApplication(0).getKey() + ", " + mapping.getApplication(0).getValue());
    }
}
