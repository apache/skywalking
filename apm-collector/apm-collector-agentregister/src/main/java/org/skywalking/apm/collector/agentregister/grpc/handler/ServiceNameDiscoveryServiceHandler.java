package org.skywalking.apm.collector.agentregister.grpc.handler;

import io.grpc.stub.StreamObserver;
import java.util.List;
import org.skywalking.apm.collector.agentregister.servicename.ServiceNameService;
import org.skywalking.apm.collector.server.grpc.GRPCHandler;
import org.skywalking.apm.network.proto.ServiceNameCollection;
import org.skywalking.apm.network.proto.ServiceNameDiscoveryServiceGrpc;
import org.skywalking.apm.network.proto.ServiceNameElement;
import org.skywalking.apm.network.proto.ServiceNameMappingCollection;
import org.skywalking.apm.network.proto.ServiceNameMappingElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pengys5
 */
public class ServiceNameDiscoveryServiceHandler extends ServiceNameDiscoveryServiceGrpc.ServiceNameDiscoveryServiceImplBase implements GRPCHandler {

    private final Logger logger = LoggerFactory.getLogger(ServiceNameDiscoveryServiceHandler.class);

    private ServiceNameService serviceNameService = new ServiceNameService();

    @Override public void discovery(ServiceNameCollection request,
        StreamObserver<ServiceNameMappingCollection> responseObserver) {
        List<ServiceNameElement> serviceNameElementList = request.getElementsList();

        ServiceNameMappingCollection.Builder builder = ServiceNameMappingCollection.newBuilder();
        for (ServiceNameElement serviceNameElement : serviceNameElementList) {
            int applicationId = serviceNameElement.getApplicationId();
            String serviceName = serviceNameElement.getServiceName();
            int serviceId = serviceNameService.getOrCreate(applicationId, serviceName);

            if (serviceId != 0) {
                ServiceNameMappingElement.Builder mappingElement = ServiceNameMappingElement.newBuilder();
                mappingElement.setServiceId(serviceId);
                mappingElement.setElement(serviceNameElement);
                builder.addElements(mappingElement);
            }
        }

        responseObserver.onNext(builder.build());
        responseObserver.onCompleted();
    }
}
