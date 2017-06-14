package org.skywalking.apm.collector.worker.noderef.analysis;

import com.google.gson.JsonObject;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.skywalking.apm.collector.actor.ClusterWorkerContext;
import org.skywalking.apm.collector.actor.LocalWorkerContext;
import org.skywalking.apm.collector.actor.Role;
import org.skywalking.apm.collector.worker.Const;
import org.skywalking.apm.collector.worker.RecordAnalysisMember;
import org.skywalking.apm.collector.worker.noderef.NodeRefIndex;
import org.skywalking.apm.collector.worker.segment.entity.Segment;
import org.skywalking.apm.collector.worker.segment.entity.Span;
import org.skywalking.apm.collector.worker.segment.entity.tag.Tags;
import org.skywalking.apm.collector.worker.tools.ClientSpanIsLeafTools;
import org.skywalking.apm.collector.worker.tools.CollectionTools;
import org.skywalking.apm.collector.worker.tools.SpanPeersTools;

/**
 * @author pengys5
 */
abstract class AbstractNodeRefAnalysis extends RecordAnalysisMember {

    private Logger logger = LogManager.getFormatterLogger(AbstractNodeRefAnalysis.class);

    AbstractNodeRefAnalysis(Role role, ClusterWorkerContext clusterContext,
        LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    final void analyseNodeRef(Segment segment, long timeSlice, long minute, long hour, long day,
        int second) {
        List<Span> spanList = segment.getSpans();
        if (CollectionTools.isNotEmpty(spanList)) {
            for (Span span : spanList) {
                JsonObject dataJsonObj = new JsonObject();
                dataJsonObj.addProperty(NodeRefIndex.TIME_SLICE, timeSlice);
                dataJsonObj.addProperty(NodeRefIndex.FRONT_IS_REAL_CODE, true);
                dataJsonObj.addProperty(NodeRefIndex.BEHIND_IS_REAL_CODE, true);

                if (Tags.SPAN_KIND_CLIENT.equals(Tags.SPAN_KIND.get(span)) && ClientSpanIsLeafTools.isLeaf(span.getSpanId(), spanList)) {
                    String front = segment.getApplicationCode();
                    dataJsonObj.addProperty(NodeRefIndex.FRONT, front);

                    String behind = SpanPeersTools.INSTANCE.getPeers(span);
                    dataJsonObj.addProperty(NodeRefIndex.BEHIND, behind);
                    dataJsonObj.addProperty(NodeRefIndex.BEHIND_IS_REAL_CODE, false);

                    String id = timeSlice + Const.ID_SPLIT + front + Const.ID_SPLIT + behind;
                    logger.debug("dag node ref: %s", dataJsonObj.toString());
                    set(id, dataJsonObj);
                    buildNodeRefResRecordData(id, span, minute, hour, day, second);
                } else if (Tags.SPAN_KIND_SERVER.equals(Tags.SPAN_KIND.get(span))) {
                    if (span.getParentSpanId() == -1 && CollectionTools.isEmpty(segment.getRefs())) {
                        String behind = segment.getApplicationCode();
                        dataJsonObj.addProperty(NodeRefIndex.BEHIND, behind);

                        String front = Const.USER_CODE;
                        dataJsonObj.addProperty(NodeRefIndex.FRONT, front);

                        String id = timeSlice + Const.ID_SPLIT + front + Const.ID_SPLIT + behind;
                        set(id, dataJsonObj);
                        buildNodeRefResRecordData(id, span, minute, hour, day, second);
                    }
                }
            }
        }
    }

    private void buildNodeRefResRecordData(String nodeRefId, Span span, long minute, long hour, long day,
        int second) {
        AbstractNodeRefResSumAnalysis.NodeRefResRecord refResRecord = new AbstractNodeRefResSumAnalysis.NodeRefResRecord(minute, hour, day, second);
        refResRecord.setStartTime(span.getStartTime());
        refResRecord.setEndTime(span.getEndTime());
        refResRecord.setNodeRefId(nodeRefId);
        refResRecord.setError(Tags.ERROR.get(span));
        sendToResSumAnalysis(refResRecord);
    }

    protected abstract void sendToResSumAnalysis(
        AbstractNodeRefResSumAnalysis.NodeRefResRecord refResRecord);
}
