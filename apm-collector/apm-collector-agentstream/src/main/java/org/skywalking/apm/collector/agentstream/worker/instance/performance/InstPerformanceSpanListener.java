package org.skywalking.apm.collector.agentstream.worker.instance.performance;

import org.skywalking.apm.collector.agentstream.worker.instance.performance.define.InstPerformanceDataDefine;
import org.skywalking.apm.collector.agentstream.worker.segment.EntrySpanListener;
import org.skywalking.apm.collector.agentstream.worker.segment.FirstSpanListener;
import org.skywalking.apm.collector.core.framework.CollectorContextHelper;
import org.skywalking.apm.collector.core.util.Const;
import org.skywalking.apm.collector.stream.StreamModuleContext;
import org.skywalking.apm.collector.stream.StreamModuleGroupDefine;
import org.skywalking.apm.collector.stream.worker.WorkerInvokeException;
import org.skywalking.apm.collector.stream.worker.WorkerNotFoundException;
import org.skywalking.apm.collector.stream.worker.util.TimeBucketUtils;
import org.skywalking.apm.network.proto.SpanObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pengys5
 */
public class InstPerformanceSpanListener implements EntrySpanListener, FirstSpanListener {

    private final Logger logger = LoggerFactory.getLogger(InstPerformanceSpanListener.class);

    private int applicationId;
    private int applicationInstanceId;
    private long cost;
    private long timeBucket;

    @Override
    public void parseEntry(SpanObject spanObject, int applicationId, int applicationInstanceId, String segmentId) {
    }

    @Override
    public void parseFirst(SpanObject spanObject, int applicationId, int applicationInstanceId, String segmentId) {
        this.applicationId = applicationId;
        this.applicationInstanceId = applicationInstanceId;
        this.cost = spanObject.getEndTime() - spanObject.getStartTime();
        timeBucket = TimeBucketUtils.INSTANCE.getSecondTimeBucket(spanObject.getStartTime());
    }

    @Override public void build() {
        StreamModuleContext context = (StreamModuleContext)CollectorContextHelper.INSTANCE.getContext(StreamModuleGroupDefine.GROUP_NAME);

        InstPerformanceDataDefine.InstPerformance instPerformance = new InstPerformanceDataDefine.InstPerformance();
        instPerformance.setId(timeBucket + Const.ID_SPLIT + applicationInstanceId);
        instPerformance.setApplicationId(applicationId);
        instPerformance.setInstanceId(applicationInstanceId);
        instPerformance.setCallTimes(1);
        instPerformance.setCostTotal(cost);
        instPerformance.setTimeBucket(timeBucket);

        try {
            logger.debug("send to instance performance persistence worker, id: {}", instPerformance.getId());
            context.getClusterWorkerContext().lookup(InstPerformancePersistenceWorker.WorkerRole.INSTANCE).tell(instPerformance.toData());
        } catch (WorkerInvokeException | WorkerNotFoundException e) {
            logger.error(e.getMessage(), e);
        }
    }
}
