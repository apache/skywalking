package com.a.eye.skywalking.collector.worker.noderef.analysis;

import com.a.eye.skywalking.collector.actor.ClusterWorkerContext;
import com.a.eye.skywalking.collector.actor.LocalWorkerContext;
import com.a.eye.skywalking.collector.worker.RecordAnalysisMember;
import com.a.eye.skywalking.collector.worker.noderef.NodeRefIndex;
import com.a.eye.skywalking.collector.worker.tools.ClientSpanIsLeafTools;
import com.a.eye.skywalking.collector.worker.tools.CollectionTools;
import com.a.eye.skywalking.trace.Span;
import com.a.eye.skywalking.trace.TraceSegment;
import com.a.eye.skywalking.trace.TraceSegmentRef;
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

    void analyseNodeRef(TraceSegment segment, long timeSlice) throws Exception {
        List<Span> spanList = segment.getSpans();
        if (CollectionTools.isNotEmpty(spanList)) {
            for (Span span : spanList) {
                JsonObject dataJsonObj = new JsonObject();
                String component = Tags.COMPONENT.get(span);
                String peers = Tags.PEERS.get(span);
                dataJsonObj.addProperty(NodeRefIndex.Time_Slice, timeSlice);
                dataJsonObj.addProperty(NodeRefIndex.FrontIsRealCode, true);
                dataJsonObj.addProperty(NodeRefIndex.BehindIsRealCode, true);

                if (Tags.SPAN_KIND_CLIENT.equals(Tags.SPAN_KIND.get(span)) && ClientSpanIsLeafTools.isLeaf(span.getSpanId(), spanList)) {
                    String front = segment.getApplicationCode();
                    dataJsonObj.addProperty(NodeRefIndex.Front, front);

                    String behind = component + "-" + peers;
                    dataJsonObj.addProperty(NodeRefIndex.Behind, behind);
                    dataJsonObj.addProperty(NodeRefIndex.BehindIsRealCode, false);

                    String id = timeSlice + "-" + front + "-" + behind;
                    logger.debug("dag node ref: %s", dataJsonObj.toString());
                    setRecord(id, dataJsonObj);
                } else if (Tags.SPAN_KIND_SERVER.equals(Tags.SPAN_KIND.get(span))) {
                    if (span.getParentSpanId() == -1 && CollectionTools.isEmpty(segment.getRefs())) {
                        String behind = segment.getApplicationCode();
                        dataJsonObj.addProperty(NodeRefIndex.Behind, behind);

                        String front = "User";
                        dataJsonObj.addProperty(NodeRefIndex.Front, front);

                        String id = timeSlice + "-" + front + "-" + behind;
                        setRecord(id, dataJsonObj);
                    } else if (span.getParentSpanId() == -1 && CollectionTools.isNotEmpty(segment.getRefs())) {
                        for (TraceSegmentRef segmentRef : segment.getRefs()) {
                            String front = segmentRef.getApplicationCode();
                            String behind = component + "-" + segmentRef.getPeerHost();
                            String id = timeSlice + "-" + front + "-" + behind;

                            JsonObject refDataJsonObj = new JsonObject();
                            refDataJsonObj.addProperty(NodeRefIndex.Front, front);
                            refDataJsonObj.addProperty(NodeRefIndex.FrontIsRealCode, true);
                            refDataJsonObj.addProperty(NodeRefIndex.Behind, behind);
                            refDataJsonObj.addProperty(NodeRefIndex.BehindIsRealCode, false);
                            refDataJsonObj.addProperty(NodeRefIndex.Time_Slice, timeSlice);
                            logger.debug("dag node ref: %s", refDataJsonObj.toString());
                            setRecord(id, refDataJsonObj);
                        }
                    }
                }
            }
        }
    }
}
