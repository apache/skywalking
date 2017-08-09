package org.skywalking.apm.collector.agentstream.grpc.handler;

import io.grpc.stub.StreamObserver;
import org.skywalking.apm.collector.agentstream.worker.Const;
import org.skywalking.apm.collector.agentstream.worker.jvmmetric.cpu.CpuMetricPersistenceWorker;
import org.skywalking.apm.collector.agentstream.worker.jvmmetric.cpu.define.CpuMetricDataDefine;
import org.skywalking.apm.collector.agentstream.worker.util.TimeBucketUtils;
import org.skywalking.apm.collector.core.framework.CollectorContextHelper;
import org.skywalking.apm.collector.server.grpc.GRPCHandler;
import org.skywalking.apm.collector.stream.StreamModuleContext;
import org.skywalking.apm.collector.stream.StreamModuleGroupDefine;
import org.skywalking.apm.collector.stream.worker.WorkerInvokeException;
import org.skywalking.apm.collector.stream.worker.WorkerNotFoundException;
import org.skywalking.apm.network.proto.CPU;
import org.skywalking.apm.network.proto.Downstream;
import org.skywalking.apm.network.proto.JVMMetrics;
import org.skywalking.apm.network.proto.JVMMetricsServiceGrpc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pengys5
 */
public class JVMMetricsServiceHandler extends JVMMetricsServiceGrpc.JVMMetricsServiceImplBase implements GRPCHandler {

    private final Logger logger = LoggerFactory.getLogger(JVMMetricsServiceHandler.class);

    @Override public void collect(JVMMetrics request, StreamObserver<Downstream> responseObserver) {
        int applicationInstanceId = request.getApplicationInstanceId();
        logger.debug("receive the jvm metric from application instance, id: {}", applicationInstanceId);

        StreamModuleContext context = (StreamModuleContext)CollectorContextHelper.INSTANCE.getContext(StreamModuleGroupDefine.GROUP_NAME);
        request.getMetricsList().forEach(metric -> {
            long time = TimeBucketUtils.INSTANCE.getSecondTimeBucket(metric.getTime());
            sendToCpuMetricPersistenceWorker(context, applicationInstanceId, time, metric.getCpu());
        });

        responseObserver.onNext(Downstream.newBuilder().build());
        responseObserver.onCompleted();
    }

    private void sendToCpuMetricPersistenceWorker(StreamModuleContext context, int applicationInstanceId,
        long timeBucket, CPU cpu) {
        CpuMetricDataDefine.CpuMetric cpuMetric = new CpuMetricDataDefine.CpuMetric();
        cpuMetric.setId(timeBucket + Const.ID_SPLIT + applicationInstanceId);
        cpuMetric.setApplicationInstanceId(applicationInstanceId);
        cpuMetric.setUsagePercent(cpu.getUsagePercent());
        cpuMetric.setTimeBucket(timeBucket);
        try {
            logger.debug("send to cpu metric persistence worker, id: {}", cpuMetric.getId());
            context.getClusterWorkerContext().lookup(CpuMetricPersistenceWorker.WorkerRole.INSTANCE).tell(cpuMetric.toData());
        } catch (WorkerInvokeException | WorkerNotFoundException e) {
            logger.error(e.getMessage(), e);
        }
    }
}
