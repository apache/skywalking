package org.skywalking.apm.collector.agentstream.mock.grpc;

import io.grpc.ManagedChannel;
import org.skywalking.apm.network.proto.ServiceNameCollection;
import org.skywalking.apm.network.proto.ServiceNameDiscoveryServiceGrpc;
import org.skywalking.apm.network.proto.ServiceNameElement;
import org.skywalking.apm.network.proto.ServiceNameMappingCollection;

/**
 * @author pengys5
 */
public class ServiceRegister {

    public static int register(ManagedChannel channel, int applicationId, String serviceName) {
        ServiceNameDiscoveryServiceGrpc.ServiceNameDiscoveryServiceBlockingStub stub = ServiceNameDiscoveryServiceGrpc.newBlockingStub(channel);
        ServiceNameCollection.Builder collection = ServiceNameCollection.newBuilder();

        ServiceNameElement.Builder element = ServiceNameElement.newBuilder();
        element.setApplicationId(applicationId);
        element.setServiceName(serviceName);
        collection.addElements(element);

        ServiceNameMappingCollection mappingCollection = stub.discovery(collection.build());
        int serviceId = mappingCollection.getElements(0).getServiceId();

        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
        }
        return serviceId;
    }
}
