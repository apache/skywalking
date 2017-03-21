package com.a.eye.skywalking.collector.worker.node.analysis;

import com.a.eye.skywalking.collector.actor.ClusterWorkerContext;
import com.a.eye.skywalking.collector.actor.LocalWorkerContext;
import com.a.eye.skywalking.collector.worker.RecordAnalysisMember;
import com.a.eye.skywalking.collector.worker.node.NodeIndex;
import com.a.eye.skywalking.collector.worker.tools.ClientSpanIsLeafTools;
import com.a.eye.skywalking.collector.worker.tools.CollectionTools;
import com.a.eye.skywalking.collector.worker.tools.SpanPeersTools;
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
abstract class AbstractNodeAnalysis extends RecordAnalysisMember {

    private Logger logger = LogManager.getFormatterLogger(AbstractNodeAnalysis.class);

    AbstractNodeAnalysis(com.a.eye.skywalking.collector.actor.Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    void analyseSpans(TraceSegment segment, long timeSlice) throws Exception {
        List<Span> spanList = segment.getSpans();

        if (CollectionTools.isNotEmpty(spanList)) {
            for (Span span : spanList) {
                JsonObject dataJsonObj = new JsonObject();
                String kind = Tags.SPAN_KIND.get(span);
                dataJsonObj.addProperty(NodeIndex.Kind, kind);

                String layer = Tags.SPAN_LAYER.get(span);
                dataJsonObj.addProperty(NodeIndex.Layer, layer);

                String component = Tags.COMPONENT.get(span);
                dataJsonObj.addProperty(NodeIndex.Component, component);

                String code = segment.getApplicationCode();
                dataJsonObj.addProperty(NodeIndex.Code, code);

                dataJsonObj.addProperty(NodeIndex.Time_Slice, timeSlice);

                if (Tags.SPAN_KIND_CLIENT.equals(kind) && ClientSpanIsLeafTools.isLeaf(span.getSpanId(), spanList)) {
                    code = component + "[" + SpanPeersTools.getPeers(span) + "]";
                    dataJsonObj.addProperty(NodeIndex.Code, code);
                    dataJsonObj.addProperty(NodeIndex.NickName, code);

                    String id = timeSlice + "-" + code;
                    logger.debug("leaf client node: %s", dataJsonObj.toString());
                    setRecord(id, dataJsonObj);
                } else if (Tags.SPAN_KIND_SERVER.equals(kind) && span.getParentSpanId() == -1) {
                    if (CollectionTools.isEmpty(segment.getRefs())) {
                        JsonObject userDataJsonObj = new JsonObject();
                        userDataJsonObj.addProperty(NodeIndex.Code, "User");
                        userDataJsonObj.addProperty(NodeIndex.Layer, "User");
                        userDataJsonObj.addProperty(NodeIndex.Kind, Tags.SPAN_KIND_CLIENT);
                        userDataJsonObj.addProperty(NodeIndex.Component, "User");
                        userDataJsonObj.addProperty(NodeIndex.NickName, "User");
                        userDataJsonObj.addProperty(NodeIndex.Time_Slice, timeSlice);
                        String userId = timeSlice + "-" + "User";
                        logger.debug("user node: %s", userDataJsonObj.toString());
                        setRecord(userId, userDataJsonObj);

                        String id = timeSlice + "-" + code;
                        dataJsonObj.addProperty(NodeIndex.NickName, code);
                        logger.debug("refs node: %s", dataJsonObj.toString());
                        setRecord(id, dataJsonObj);
                    } else {
                        for (TraceSegmentRef segmentRef : segment.getRefs()) {
                            String nickName = component + "[" + segmentRef.getPeerHost() + "]";
                            dataJsonObj.addProperty(NodeIndex.NickName, nickName);
                            String id = timeSlice + "-" + code;
                            logger.debug("refs node: %s", dataJsonObj.toString());
                            setRecord(id, dataJsonObj);
                        }
                    }
                } else {
                    logger.error("The span kind value is incorrect which segment record id is %s, the value must client or server", segment.getTraceSegmentId());
                    return;
                }
            }
        }
    }
}