package org.skywalking.apm.collector.agentstream.mock.grpc;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Test;
import org.skywalking.apm.collector.core.util.TimeBucketUtils;
import org.skywalking.apm.network.proto.Application;
import org.skywalking.apm.network.proto.ApplicationInstance;
import org.skywalking.apm.network.proto.ApplicationInstanceMapping;
import org.skywalking.apm.network.proto.ApplicationMapping;
import org.skywalking.apm.network.proto.ApplicationRegisterServiceGrpc;
import org.skywalking.apm.network.proto.Downstream;
import org.skywalking.apm.network.proto.InstanceDiscoveryServiceGrpc;
import org.skywalking.apm.network.proto.KeyWithStringValue;
import org.skywalking.apm.network.proto.LogMessage;
import org.skywalking.apm.network.proto.OSInfo;
import org.skywalking.apm.network.proto.RefType;
import org.skywalking.apm.network.proto.ServiceNameCollection;
import org.skywalking.apm.network.proto.ServiceNameDiscoveryServiceGrpc;
import org.skywalking.apm.network.proto.ServiceNameElement;
import org.skywalking.apm.network.proto.ServiceNameMappingCollection;
import org.skywalking.apm.network.proto.SpanLayer;
import org.skywalking.apm.network.proto.SpanObject;
import org.skywalking.apm.network.proto.SpanType;
import org.skywalking.apm.network.proto.TraceSegmentObject;
import org.skywalking.apm.network.proto.TraceSegmentReference;
import org.skywalking.apm.network.proto.TraceSegmentServiceGrpc;
import org.skywalking.apm.network.proto.UniqueId;
import org.skywalking.apm.network.proto.UpstreamSegment;
import org.skywalking.apm.network.trace.component.ComponentsDefine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pengys5
 */
public class GrpcSegmentPost {

    private final Logger logger = LoggerFactory.getLogger(GrpcSegmentPost.class);

    private AtomicLong sequence = new AtomicLong(1);

    @Test
    public void init() {
        ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 11800).maxInboundMessageSize(1024 * 1024 * 50).usePlaintext(true).build();

        int consumerApplicationId = 0;
        int providerApplicationId = 0;
        int consumerInstanceId = 0;
        int providerInstanceId = 0;
        int consumerEntryServiceId = 0;
        int consumerExitServiceId = 0;
        int consumerExitApplicationId = 0;
        int providerEntryServiceId = 0;

        while (consumerApplicationId == 0) {
            consumerApplicationId = registerApplication(channel, "consumer");
        }
        while (consumerExitApplicationId == 0) {
            consumerExitApplicationId = registerApplication(channel, "172.25.0.4:20880");
        }
        while (providerApplicationId == 0) {
            providerApplicationId = registerApplication(channel, "provider");
        }
        while (consumerInstanceId == 0) {
            consumerInstanceId = registerInstanceId(channel, "ConsumerUUID", consumerApplicationId, "consumer_host_name", 1);
        }
        while (providerInstanceId == 0) {
            providerInstanceId = registerInstanceId(channel, "ProviderUUID", providerApplicationId, "provider_host_name", 2);
        }
        while (consumerEntryServiceId == 0) {
            consumerEntryServiceId = registerServiceId(channel, consumerApplicationId, "/dubbox-case/case/dubbox-rest");
        }
        while (consumerExitServiceId == 0) {
            consumerExitServiceId = registerServiceId(channel, consumerApplicationId, "org.skywaking.apm.testcase.dubbo.services.GreetService.doBusiness()");
        }
        while (providerEntryServiceId == 0) {
            providerEntryServiceId = registerServiceId(channel, providerApplicationId, "org.skywaking.apm.testcase.dubbo.services.GreetService.doBusiness()");
        }

        Ids ids = new Ids();
        ids.setConsumerApplicationId(consumerApplicationId);
        ids.setProviderApplicationId(providerApplicationId);
        ids.setConsumerInstanceId(consumerInstanceId);
        ids.setProviderInstanceId(providerInstanceId);
        ids.setConsumerEntryServiceId(consumerEntryServiceId);
        ids.setConsumerExitServiceId(consumerExitServiceId);
        ids.setConsumerExitApplicationId(consumerExitApplicationId);

        long startTime = TimeBucketUtils.INSTANCE.getSecondTimeBucket(System.currentTimeMillis());
        logger.info("start time: {}", startTime);

        int count = 10;
        ThreadCount threadCount = new ThreadCount(count);
        for (int i = 0; i < count; i++) {
            Status status = new Status();
            BuildNewSegment buildNewSegment = new BuildNewSegment(channel, ids, threadCount, i, status);
            Executors.newSingleThreadExecutor().execute(buildNewSegment);
        }

        while (threadCount.getCount() != 0) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
            }
        }
        long endTime = TimeBucketUtils.INSTANCE.getSecondTimeBucket(System.currentTimeMillis());
        logger.info("end time: {}", endTime);

        channel.shutdownNow();
        while (!channel.isTerminated()) {
            try {
                channel.awaitTermination(100, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                logger.error(e.getMessage(), e);
            }
        }
    }

    private int registerApplication(ManagedChannel channel, String applicationCode) {
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

    private int registerInstanceId(ManagedChannel channel, String agentUUId, Integer applicationId,
        String hostName, int processNo) {
        InstanceDiscoveryServiceGrpc.InstanceDiscoveryServiceBlockingStub stub = InstanceDiscoveryServiceGrpc.newBlockingStub(channel);
        ApplicationInstance.Builder instance = ApplicationInstance.newBuilder();
        instance.setApplicationId(applicationId);
        instance.setRegisterTime(System.currentTimeMillis());
        instance.setAgentUUID(agentUUId);

        OSInfo.Builder osInfo = OSInfo.newBuilder();
        osInfo.setHostname(hostName);
        osInfo.setOsName("Linux");
        osInfo.setProcessNo(processNo);
        osInfo.addIpv4S("10.0.0.1");
        osInfo.addIpv4S("10.0.0.2");
        instance.setOsinfo(osInfo.build());

        ApplicationInstanceMapping mapping = stub.register(instance.build());
        int instanceId = mapping.getApplicationInstanceId();

        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
        }
        return instanceId;
    }

    private int registerServiceId(ManagedChannel channel, int applicationId, String serviceName) {
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

    class BuildNewSegment implements Runnable {
        private final ManagedChannel segmentChannel;
        private final Ids ids;
        private final ThreadCount threadCount;
        private final int procNo;
        private final Status status;
        private StreamObserver<UpstreamSegment> streamObserver;

        public BuildNewSegment(ManagedChannel segmentChannel,
            Ids ids, ThreadCount threadCount, int procNo,
            Status status) {
            this.segmentChannel = segmentChannel;
            this.ids = ids;
            this.threadCount = threadCount;
            this.procNo = procNo;
            this.status = status;
        }

        @Override public void run() {
            statusChange();
            int i = 0;
            while (i < 50000) {
                send(streamObserver, ids);

                i++;
                if (i % 10000 == 0) {
                    logger.info("process no: {}, send segment count: {}", procNo, i);
                    streamObserver.onCompleted();
                    while (!status.isFinish) {
                        try {
                            Thread.sleep(10);
                        } catch (InterruptedException e) {
                        }
                    }
                    status.setFinish(false);
                    statusChange();
                }
            }
            this.threadCount.finishOne();
        }

        private void statusChange() {
            TraceSegmentServiceGrpc.TraceSegmentServiceStub stub = TraceSegmentServiceGrpc.newStub(segmentChannel);
            streamObserver = stub.collect(new StreamObserver<Downstream>() {
                @Override public void onNext(Downstream downstream) {
                }

                @Override public void onError(Throwable throwable) {
                    logger.error(throwable.getMessage(), throwable);
                }

                @Override public void onCompleted() {
                    status.setFinish(true);
                    logger.info("process no: {}, server completed", procNo);
                }
            });
        }
    }

    public void send(StreamObserver<UpstreamSegment> streamObserver, Ids ids) {
        long now = System.currentTimeMillis();
        UniqueId consumerSegmentId = createSegmentId();
        UniqueId providerSegmentId = createSegmentId();

        streamObserver.onNext(createConsumerSegment(consumerSegmentId, ids, now));
        streamObserver.onNext(createProviderSegment(consumerSegmentId, providerSegmentId, ids, now));
    }

    private UpstreamSegment createConsumerSegment(UniqueId segmentId, Ids ids, long timestamp) {
        UpstreamSegment.Builder upstream = UpstreamSegment.newBuilder();
        upstream.addGlobalTraceIds(segmentId);

        TraceSegmentObject.Builder segmentBuilder = TraceSegmentObject.newBuilder();
        segmentBuilder.setApplicationId(ids.consumerApplicationId);
        segmentBuilder.setApplicationInstanceId(ids.consumerInstanceId);
        segmentBuilder.setTraceSegmentId(segmentId);

        SpanObject.Builder entrySpan = SpanObject.newBuilder();
        entrySpan.setSpanId(0);
        entrySpan.setSpanType(SpanType.Entry);
        entrySpan.setSpanLayer(SpanLayer.Http);
        entrySpan.setParentSpanId(-1);
        entrySpan.setStartTime(timestamp);
        entrySpan.setEndTime(timestamp + 3000);
        entrySpan.setComponentId(ComponentsDefine.TOMCAT.getId());
        entrySpan.setOperationNameId(ids.getConsumerEntryServiceId());
        entrySpan.setIsError(false);

        LogMessage.Builder entryLogMessage = LogMessage.newBuilder();
        entryLogMessage.setTime(timestamp);

        KeyWithStringValue.Builder data_1 = KeyWithStringValue.newBuilder();
        data_1.setKey("url");
        data_1.setValue("http://localhost:18080/dubbox-case/case/dubbox-rest");
        entryLogMessage.addData(data_1);

        KeyWithStringValue.Builder data_2 = KeyWithStringValue.newBuilder();
        data_2.setKey("http.method");
        data_2.setValue("GET");
        entryLogMessage.addData(data_2);
        entrySpan.addLogs(entryLogMessage);
        segmentBuilder.addSpans(entrySpan);

        SpanObject.Builder exitSpan = SpanObject.newBuilder();
        exitSpan.setSpanId(1);
        exitSpan.setSpanType(SpanType.Exit);
        exitSpan.setSpanLayer(SpanLayer.RPCFramework);
        exitSpan.setParentSpanId(0);
        exitSpan.setStartTime(timestamp + 500);
        exitSpan.setEndTime(timestamp + 2500);
        exitSpan.setComponentId(ComponentsDefine.TOMCAT.getId());
        exitSpan.setOperationNameId(ids.getConsumerExitServiceId());
        exitSpan.setPeerId(ids.consumerExitApplicationId);
        exitSpan.setIsError(false);

        LogMessage.Builder exitLogMessage = LogMessage.newBuilder();
        exitLogMessage.setTime(timestamp);

        KeyWithStringValue.Builder data = KeyWithStringValue.newBuilder();
        data.setKey("url");
        data.setValue("rest://172.25.0.4:20880/org.skywaking.apm.testcase.dubbo.services.GreetService.doBusiness()");
        exitLogMessage.addData(data);
        exitSpan.addLogs(exitLogMessage);
        segmentBuilder.addSpans(exitSpan);

        upstream.setSegment(segmentBuilder.build().toByteString());
        return upstream.build();
    }

    private UpstreamSegment createProviderSegment(UniqueId consumerSegmentId, UniqueId providerSegmentId, Ids ids,
        long timestamp) {
        UpstreamSegment.Builder upstream = UpstreamSegment.newBuilder();
        upstream.addGlobalTraceIds(consumerSegmentId);

        TraceSegmentObject.Builder segmentBuilder = TraceSegmentObject.newBuilder();
        segmentBuilder.setApplicationId(ids.providerApplicationId);
        segmentBuilder.setApplicationInstanceId(ids.providerInstanceId);
        segmentBuilder.setTraceSegmentId(providerSegmentId);

        TraceSegmentReference.Builder referenceBuilder = TraceSegmentReference.newBuilder();
        referenceBuilder.setParentTraceSegmentId(consumerSegmentId);
        referenceBuilder.setParentApplicationInstanceId(ids.getConsumerInstanceId());
        referenceBuilder.setParentSpanId(1);
        referenceBuilder.setParentServiceId(ids.getConsumerExitServiceId());
        referenceBuilder.setEntryApplicationInstanceId(ids.getConsumerInstanceId());
        referenceBuilder.setEntryServiceId(ids.getConsumerEntryServiceId());
        referenceBuilder.setNetworkAddressId(ids.consumerExitApplicationId);
        referenceBuilder.setRefType(RefType.CrossProcess);
        segmentBuilder.addRefs(referenceBuilder);

        SpanObject.Builder entrySpan = SpanObject.newBuilder();
        entrySpan.setSpanId(0);
        entrySpan.setSpanType(SpanType.Entry);
        entrySpan.setSpanLayer(SpanLayer.RPCFramework);
        entrySpan.setParentSpanId(-1);
        entrySpan.setStartTime(timestamp + 1000);
        entrySpan.setEndTime(timestamp + 2000);
        entrySpan.setComponentId(ComponentsDefine.TOMCAT.getId());
        entrySpan.setOperationNameId(ids.getProviderEntryServiceId());
        entrySpan.setIsError(false);

        LogMessage.Builder entryLogMessage = LogMessage.newBuilder();
        entryLogMessage.setTime(timestamp);

        KeyWithStringValue.Builder data_1 = KeyWithStringValue.newBuilder();
        data_1.setKey("url");
        data_1.setValue("rest://172.25.0.4:20880/org.skywaking.apm.testcase.dubbo.services.GreetService.doBusiness()");
        entryLogMessage.addData(data_1);

        KeyWithStringValue.Builder data_2 = KeyWithStringValue.newBuilder();
        data_2.setKey("http.method");
        data_2.setValue("GET");
        entryLogMessage.addData(data_2);
        entrySpan.addLogs(entryLogMessage);
        segmentBuilder.addSpans(entrySpan);

        upstream.setSegment(segmentBuilder.build().toByteString());
        return upstream.build();
    }

    private UniqueId createSegmentId() {
        long id = sequence.getAndIncrement();
        UniqueId.Builder builder = UniqueId.newBuilder();
        builder.addIdParts(id);
        builder.addIdParts(id);
        builder.addIdParts(id);
        return builder.build();
    }

    class Ids {
        private int consumerApplicationId = 0;
        private int providerApplicationId = 0;
        private int consumerInstanceId = 0;
        private int providerInstanceId = 0;
        private int consumerEntryServiceId = 0;
        private int consumerExitServiceId = 0;
        private int consumerExitApplicationId = 0;
        private int providerEntryServiceId = 0;

        public int getConsumerApplicationId() {
            return consumerApplicationId;
        }

        public void setConsumerApplicationId(int consumerApplicationId) {
            this.consumerApplicationId = consumerApplicationId;
        }

        public int getProviderApplicationId() {
            return providerApplicationId;
        }

        public void setProviderApplicationId(int providerApplicationId) {
            this.providerApplicationId = providerApplicationId;
        }

        public int getConsumerInstanceId() {
            return consumerInstanceId;
        }

        public void setConsumerInstanceId(int consumerInstanceId) {
            this.consumerInstanceId = consumerInstanceId;
        }

        public int getProviderInstanceId() {
            return providerInstanceId;
        }

        public void setProviderInstanceId(int providerInstanceId) {
            this.providerInstanceId = providerInstanceId;
        }

        public int getConsumerEntryServiceId() {
            return consumerEntryServiceId;
        }

        public void setConsumerEntryServiceId(int consumerEntryServiceId) {
            this.consumerEntryServiceId = consumerEntryServiceId;
        }

        public int getConsumerExitServiceId() {
            return consumerExitServiceId;
        }

        public void setConsumerExitServiceId(int consumerExitServiceId) {
            this.consumerExitServiceId = consumerExitServiceId;
        }

        public int getConsumerExitApplicationId() {
            return consumerExitApplicationId;
        }

        public void setConsumerExitApplicationId(int consumerExitApplicationId) {
            this.consumerExitApplicationId = consumerExitApplicationId;
        }

        public int getProviderEntryServiceId() {
            return providerEntryServiceId;
        }

        public void setProviderEntryServiceId(int providerEntryServiceId) {
            this.providerEntryServiceId = providerEntryServiceId;
        }
    }

    class ThreadCount {
        private int count;

        public ThreadCount(int count) {
            this.count = count;
        }

        public void finishOne() {
            count--;
        }

        public int getCount() {
            return count;
        }
    }

    class Status {
        private boolean isFinish = false;

        public boolean isFinish() {
            return isFinish;
        }

        public void setFinish(boolean finish) {
            isFinish = finish;
        }
    }
}
