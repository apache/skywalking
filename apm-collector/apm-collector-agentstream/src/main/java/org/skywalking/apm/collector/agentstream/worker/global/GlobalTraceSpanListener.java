package org.skywalking.apm.collector.agentstream.worker.global;

import java.util.ArrayList;
import java.util.List;
import org.skywalking.apm.collector.storage.define.global.GlobalTraceDataDefine;
import org.skywalking.apm.collector.agentstream.worker.segment.FirstSpanListener;
import org.skywalking.apm.collector.agentstream.worker.segment.GlobalTraceIdsListener;
import org.skywalking.apm.collector.core.util.TimeBucketUtils;
import org.skywalking.apm.collector.core.framework.CollectorContextHelper;
import org.skywalking.apm.collector.stream.StreamModuleContext;
import org.skywalking.apm.collector.stream.StreamModuleGroupDefine;
import org.skywalking.apm.collector.stream.worker.WorkerInvokeException;
import org.skywalking.apm.collector.stream.worker.WorkerNotFoundException;
import org.skywalking.apm.network.proto.SpanObject;
import org.skywalking.apm.network.proto.UniqueId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pengys5
 */
public class GlobalTraceSpanListener implements FirstSpanListener, GlobalTraceIdsListener {

    private final Logger logger = LoggerFactory.getLogger(GlobalTraceSpanListener.class);

    private List<String> globalTraceIds = new ArrayList<>();
    private String segmentId;
    private long timeBucket;

    @Override
    public void parseFirst(SpanObject spanObject, int applicationId, int applicationInstanceId, String segmentId) {
        this.timeBucket = TimeBucketUtils.INSTANCE.getMinuteTimeBucket(spanObject.getStartTime());
        this.segmentId = segmentId;
    }

    @Override public void parseGlobalTraceId(UniqueId uniqueId) {
        StringBuilder globalTraceIdBuilder = new StringBuilder();
        uniqueId.getIdPartsList().forEach(globalTraceIdBuilder::append);
        globalTraceIds.add(globalTraceIdBuilder.toString());
    }

    @Override public void build() {
        logger.debug("global trace listener build");
        StreamModuleContext context = (StreamModuleContext)CollectorContextHelper.INSTANCE.getContext(StreamModuleGroupDefine.GROUP_NAME);

        for (String globalTraceId : globalTraceIds) {
            GlobalTraceDataDefine.GlobalTrace globalTrace = new GlobalTraceDataDefine.GlobalTrace();
            globalTrace.setGlobalTraceId(globalTraceId);
            globalTrace.setId(segmentId + globalTraceId);
            globalTrace.setSegmentId(segmentId);
            globalTrace.setTimeBucket(timeBucket);
            try {
                logger.debug("send to global trace persistence worker, id: {}", globalTrace.getId());
                context.getClusterWorkerContext().lookup(GlobalTracePersistenceWorker.WorkerRole.INSTANCE).tell(globalTrace.toData());
            } catch (WorkerInvokeException | WorkerNotFoundException e) {
                logger.error(e.getMessage(), e);
            }
        }
    }
}