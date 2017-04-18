package com.a.eye.skywalking.collector.worker.globaltrace.persistence;

import com.a.eye.skywalking.api.util.StringUtil;
import com.a.eye.skywalking.collector.actor.*;
import com.a.eye.skywalking.collector.actor.selector.RollingSelector;
import com.a.eye.skywalking.collector.actor.selector.WorkerSelector;
import com.a.eye.skywalking.collector.worker.globaltrace.GlobalTraceIndex;
import com.a.eye.skywalking.collector.worker.segment.SegmentIndex;
import com.a.eye.skywalking.collector.worker.segment.logic.Segment;
import com.a.eye.skywalking.collector.worker.segment.logic.SegmentDeserialize;
import com.a.eye.skywalking.collector.worker.segment.logic.SpanView;
import com.a.eye.skywalking.collector.worker.storage.GetResponseFromEs;
import com.a.eye.skywalking.collector.worker.storage.MergeData;
import com.a.eye.skywalking.collector.worker.tools.CollectionTools;
import com.a.eye.skywalking.trace.Span;
import com.a.eye.skywalking.trace.TraceSegmentRef;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author pengys5
 */
public class GlobalTraceSearchWithGlobalId extends AbstractLocalSyncWorker {

    private Logger logger = LogManager.getFormatterLogger(this.getClass());

    private Gson gson = new Gson();

    GlobalTraceSearchWithGlobalId(Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    @Override
    protected void onWork(Object request, Object response) throws Exception {
        if (request instanceof String) {
            String globalId = (String)request;
            String globalTraceData = GetResponseFromEs.INSTANCE.get(GlobalTraceIndex.INDEX, GlobalTraceIndex.TYPE_RECORD, globalId).getSourceAsString();
            JsonObject globalTraceObj = gson.fromJson(globalTraceData, JsonObject.class);
            logger.debug("globalTraceObj: %s", globalTraceObj);

            String subSegIdsStr = globalTraceObj.get(GlobalTraceIndex.SUB_SEG_IDS).getAsString();
            String[] subSegIds = subSegIdsStr.split(MergeData.SPLIT);

            List<SpanView> spanViewList = new ArrayList<>();
            for (String subSegId : subSegIds) {
                logger.debug("subSegId: %s", subSegId);
                String segmentSource = GetResponseFromEs.INSTANCE.get(SegmentIndex.INDEX, SegmentIndex.TYPE_RECORD, subSegId).getSourceAsString();
                logger.debug("segmentSource: %s", segmentSource);
                Segment segment = SegmentDeserialize.INSTANCE.deserializeFromES(segmentSource);
                String segmentId = segment.getTraceSegmentId();
                List<TraceSegmentRef> refsList = segment.getRefs();

                for (Span span : segment.getSpans()) {
                    logger.debug(span.getOperationName());
                    spansDataBuild(span, segment.getApplicationCode(), segmentId, spanViewList, refsList);
                }
            }

            JsonObject responseObj = (JsonObject)response;
            responseObj.addProperty("result", buildTree(spanViewList));
        }
    }

    private String buildTree(List<SpanView> spanViewList) {
        SpanView rootSpan = findRoot(spanViewList);
        assert rootSpan != null;
        findChild(rootSpan, spanViewList, rootSpan.getStartTime());

        List<SpanView> viewList = new ArrayList<>();
        viewList.add(rootSpan);

        Gson gson = new Gson();
        return gson.toJson(viewList);
    }

    private SpanView findRoot(List<SpanView> spanViewList) {
        for (SpanView spanView : spanViewList) {
            if (StringUtil.isEmpty(spanView.getParentSpanSegId())) {
                spanView.setRelativeStartTime(0);
                spanView.setParentSpanSegId("-1");
                return spanView;
            }
        }
        return null;
    }

    private void findChild(SpanView parentSpan, List<SpanView> spanViewList, long rootStartTime) {
        String spanSegId = parentSpan.getSpanSegId();
        logger.debug("findChild spanSegId: %s", spanSegId);

        List<SpanView> childSpanSort = sortChildSpan(spanViewList, spanSegId);
        for (SpanView spanView : childSpanSort) {
            spanView.setRelativeStartTime(spanView.getStartTime() - rootStartTime);
            parentSpan.addChild(spanView);
            findChild(spanView, spanViewList, rootStartTime);
        }
    }

    private List<SpanView> sortChildSpan(List<SpanView> spanViewList, String parentSpanId) {
        List<SpanView> tempList = new ArrayList<>();

        for (SpanView spanView : spanViewList) {
            if (parentSpanId.equals(spanView.getParentSpanSegId())) {
                tempList.add(spanView);
            }
        }

        Collections.sort(tempList);
        return tempList;
    }

    private void spansDataBuild(Span span, String appCode, String segmentId, List<SpanView> spanViewList,
        List<TraceSegmentRef> refsList) {
        int spanId = span.getSpanId();
        String spanSegId = segmentId + "--" + String.valueOf(spanId);

        SpanView spanView = new SpanView();
        spanView.setSpanId(spanId);
        spanView.setSegId(segmentId);
        spanView.setSpanSegId(spanSegId);
        spanView.setStartTime(span.getStartTime());
        spanView.setOperationName(span.getOperationName());
        spanView.setAppCode(appCode);
        long cost = span.getEndTime() - span.getStartTime();
        if (cost == 0) {
            spanView.setCost(1);
        } else {
            spanView.setCost(cost);
        }

        if (spanId == 0) {
            if (CollectionTools.isNotEmpty(refsList)) {
                if (refsList.size() > 1) {
                    throw new UnsupportedOperationException("not support batch call");
                } else {
                    TraceSegmentRef segmentRef = refsList.get(0);
                    int parentSpanId = segmentRef.getSpanId();
                    String parentSegId = segmentRef.getTraceSegmentId();

                    String parentSpanSegId = parentSegId + "--" + String.valueOf(parentSpanId);
                    spanView.setParentSpanSegId(parentSpanSegId);
                }
            }
        } else {
            int parentSpanId = span.getParentSpanId();
            String parentSpanSegId = segmentId + "--" + String.valueOf(parentSpanId);
            spanView.setParentSpanSegId(parentSpanSegId);
        }

        spanViewList.add(spanView);
    }

    public static class Factory extends AbstractLocalSyncWorkerProvider<GlobalTraceSearchWithGlobalId> {
        public static Factory INSTANCE = new Factory();

        @Override
        public Role role() {
            return WorkerRole.INSTANCE;
        }

        @Override
        public GlobalTraceSearchWithGlobalId workerInstance(ClusterWorkerContext clusterContext) {
            return new GlobalTraceSearchWithGlobalId(role(), clusterContext, new LocalWorkerContext());
        }
    }

    public enum WorkerRole implements Role {
        INSTANCE;

        @Override
        public String roleName() {
            return GlobalTraceSearchWithGlobalId.class.getSimpleName();
        }

        @Override
        public WorkerSelector workerSelector() {
            return new RollingSelector();
        }
    }
}
