package com.a.eye.skywalking.collector.worker.noderef.analysis;

import com.a.eye.skywalking.collector.actor.ClusterWorkerContext;
import com.a.eye.skywalking.collector.actor.LocalWorkerContext;
import com.a.eye.skywalking.collector.worker.Const;
import com.a.eye.skywalking.collector.worker.RecordAnalysisMember;
import com.a.eye.skywalking.collector.worker.noderef.NodeRefIndex;
import com.a.eye.skywalking.collector.worker.tools.ClientSpanIsLeafTools;
import com.a.eye.skywalking.collector.worker.tools.CollectionTools;
import com.a.eye.skywalking.collector.worker.tools.SpanPeersTools;
import com.a.eye.skywalking.trace.Span;
import com.a.eye.skywalking.trace.TraceSegment;
import com.a.eye.skywalking.trace.tag.Tags;
import com.google.gson.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

/**
 * @author pengys5
 */
abstract class AbstractNodeRefAnalysis extends RecordAnalysisMember {

    private Logger logger = LogManager.getFormatterLogger(AbstractNodeRefAnalysis.class);

    AbstractNodeRefAnalysis(com.a.eye.skywalking.collector.actor.Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    final void analyseNodeRef(TraceSegment segment, long timeSlice, long minute, long hour, long day, int second) throws Exception {
        List<Span> spanList = segment.getSpans();
        if (CollectionTools.isNotEmpty(spanList)) {
            for (Span span : spanList) {
                JsonObject dataJsonObj = new JsonObject();
                dataJsonObj.addProperty(NodeRefIndex.Time_Slice, timeSlice);
                dataJsonObj.addProperty(NodeRefIndex.FrontIsRealCode, true);
                dataJsonObj.addProperty(NodeRefIndex.BehindIsRealCode, true);

                if (Tags.SPAN_KIND_CLIENT.equals(Tags.SPAN_KIND.get(span)) && ClientSpanIsLeafTools.isLeaf(span.getSpanId(), spanList)) {
                    String front = segment.getApplicationCode();
                    dataJsonObj.addProperty(NodeRefIndex.Front, front);

                    String behind = SpanPeersTools.getPeers(span);
                    dataJsonObj.addProperty(NodeRefIndex.Behind, behind);
                    dataJsonObj.addProperty(NodeRefIndex.BehindIsRealCode, false);

                    String id = timeSlice + Const.ID_SPLIT + front + Const.ID_SPLIT + behind;
                    logger.debug("dag node ref: %s", dataJsonObj.toString());
                    setRecord(id, dataJsonObj);
                    buildNodeRefResRecordData(id, span, minute, hour, day, second);
                } else if (Tags.SPAN_KIND_SERVER.equals(Tags.SPAN_KIND.get(span))) {
                    if (span.getParentSpanId() == -1 && CollectionTools.isEmpty(segment.getRefs())) {
                        String behind = segment.getApplicationCode();
                        dataJsonObj.addProperty(NodeRefIndex.Behind, behind);

                        String front = Const.USER_CODE;
                        dataJsonObj.addProperty(NodeRefIndex.Front, front);

                        String id = timeSlice + Const.ID_SPLIT + front + Const.ID_SPLIT + behind;
                        setRecord(id, dataJsonObj);
                        buildNodeRefResRecordData(id, span, minute, hour, day, second);
                    }
                }
            }
        }
    }

    private void buildNodeRefResRecordData(String nodeRefId, Span span, long minute, long hour, long day, int second) throws Exception {
        AbstractNodeRefResSumAnalysis.NodeRefResRecord refResRecord = new AbstractNodeRefResSumAnalysis.NodeRefResRecord(minute, hour, day, second);
        refResRecord.setStartTime(span.getStartTime());
        refResRecord.setEndTime(span.getEndTime());
        refResRecord.setNodeRefId(nodeRefId);
        refResRecord.setError(Tags.ERROR.get(span));
        sendToResSumAnalysis(refResRecord);
    }

    protected abstract void sendToResSumAnalysis(AbstractNodeRefResSumAnalysis.NodeRefResRecord refResRecord) throws Exception;
}
