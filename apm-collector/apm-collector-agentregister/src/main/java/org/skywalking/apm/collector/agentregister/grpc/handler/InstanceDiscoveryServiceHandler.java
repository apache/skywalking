package org.skywalking.apm.collector.agentregister.grpc.handler;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.grpc.stub.StreamObserver;
import org.skywalking.apm.collector.agentregister.instance.InstanceIDService;
import org.skywalking.apm.collector.server.grpc.GRPCHandler;
import org.skywalking.apm.network.proto.ApplicationInstance;
import org.skywalking.apm.network.proto.ApplicationInstanceHeartbeat;
import org.skywalking.apm.network.proto.ApplicationInstanceMapping;
import org.skywalking.apm.network.proto.ApplicationInstanceRecover;
import org.skywalking.apm.network.proto.Downstream;
import org.skywalking.apm.network.proto.InstanceDiscoveryServiceGrpc;
import org.skywalking.apm.network.proto.OSInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pengys5
 */
public class InstanceDiscoveryServiceHandler extends InstanceDiscoveryServiceGrpc.InstanceDiscoveryServiceImplBase implements GRPCHandler {

    private final Logger logger = LoggerFactory.getLogger(InstanceDiscoveryServiceHandler.class);

    private InstanceIDService instanceIDService = new InstanceIDService();

    @Override
    public void register(ApplicationInstance request, StreamObserver<ApplicationInstanceMapping> responseObserver) {
        int instanceId = instanceIDService.getOrCreate(request.getApplicationId(), request.getAgentUUID(), request.getRegisterTime(), buildOsInfo(request.getOsinfo()));
        ApplicationInstanceMapping.Builder builder = ApplicationInstanceMapping.newBuilder();
        builder.setApplicationId(request.getApplicationId());
        builder.setApplicationInstanceId(instanceId);
        responseObserver.onNext(builder.build());
        responseObserver.onCompleted();
    }

    @Override public void heartbeat(ApplicationInstanceHeartbeat request, StreamObserver<Downstream> responseObserver) {
        instanceIDService.heartBeat(request.getApplicationInstanceId(), request.getHeartbeatTime());
        responseObserver.onNext(Downstream.newBuilder().build());
        responseObserver.onCompleted();
    }

    @Override
    public void registerRecover(ApplicationInstanceRecover request, StreamObserver<Downstream> responseObserver) {
        instanceIDService.recover(request.getApplicationInstanceId(), request.getApplicationId(), request.getRegisterTime(), buildOsInfo(request.getOsinfo()));
        responseObserver.onNext(Downstream.newBuilder().build());
        responseObserver.onCompleted();
    }

    private String buildOsInfo(OSInfo osinfo) {
        JsonObject osInfoJson = new JsonObject();
        osInfoJson.addProperty("osName", osinfo.getOsName());
        osInfoJson.addProperty("hostName", osinfo.getHostname());
        osInfoJson.addProperty("processId", osinfo.getProcessNo());

        JsonArray ipv4Array = new JsonArray();
        osinfo.getIpv4SList().forEach(ipv4 -> {
            ipv4Array.add(ipv4);
        });
        osInfoJson.add("ipv4s", ipv4Array);
        return osInfoJson.toString();
    }
}
