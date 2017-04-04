package com.a.eye.skywalking.collector.worker.node.analysis;

import com.a.eye.skywalking.collector.actor.ClusterWorkerContext;
import com.a.eye.skywalking.collector.actor.LocalWorkerContext;
import com.a.eye.skywalking.collector.worker.Const;
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
        logger.debug("node analysis span isNotEmpty %s", CollectionTools.isNotEmpty(spanList));

        if (CollectionTools.isNotEmpty(spanList)) {
            logger.debug("node analysis span list size: %s", spanList.size());
            String id = timeSlice + Const.ID_SPLIT + segment.getApplicationCode();
            JsonObject appDataJsonObj = new JsonObject();
            appDataJsonObj.addProperty(NodeIndex.Code, segment.getApplicationCode());
            appDataJsonObj.addProperty(NodeIndex.NickName, segment.getApplicationCode());
            appDataJsonObj.addProperty(NodeIndex.Time_Slice, timeSlice);
            setRecord(id, appDataJsonObj);

            for (Span span : spanList) {
                JsonObject dataJsonObj = new JsonObject();
                String kind = Tags.SPAN_KIND.get(span);
                String component = Tags.COMPONENT.get(span);
                dataJsonObj.addProperty(NodeIndex.Component, component);

                String code = segment.getApplicationCode();
                dataJsonObj.addProperty(NodeIndex.Code, code);

                dataJsonObj.addProperty(NodeIndex.Time_Slice, timeSlice);
                logger.debug("span id=%s, kind=%s, component=%s, code=%s", span.getSpanId(), kind, component, code);

                if (Tags.SPAN_KIND_CLIENT.equals(kind) && ClientSpanIsLeafTools.isLeaf(span.getSpanId(), spanList)) {
                    logger.debug("The span id %s which kind is client and is a leaf span", span.getSpanId());
                    code = SpanPeersTools.getPeers(span);
                    dataJsonObj.addProperty(NodeIndex.Code, code);
                    dataJsonObj.addProperty(NodeIndex.NickName, code);

                    id = timeSlice + Const.ID_SPLIT + code;
                    logger.debug("leaf client node: %s", dataJsonObj.toString());
                    setRecord(id, dataJsonObj);
                } else if (Tags.SPAN_KIND_SERVER.equals(kind) && span.getParentSpanId() == -1) {
                    logger.debug("The span id %s which kind is server and is top span", span.getSpanId());
                    if (CollectionTools.isEmpty(segment.getRefs())) {
                        JsonObject userDataJsonObj = new JsonObject();
                        userDataJsonObj.addProperty(NodeIndex.Code, Const.USER_CODE);
                        userDataJsonObj.addProperty(NodeIndex.Component, Const.USER_CODE);
                        userDataJsonObj.addProperty(NodeIndex.NickName, Const.USER_CODE);
                        userDataJsonObj.addProperty(NodeIndex.Time_Slice, timeSlice);
                        String userId = timeSlice + Const.ID_SPLIT + Const.USER_CODE;
                        logger.debug("user node: %s", userDataJsonObj.toString());
                        setRecord(userId, userDataJsonObj);

                        id = timeSlice + Const.ID_SPLIT + code;
                        dataJsonObj.addProperty(NodeIndex.NickName, code);
                        logger.debug("refs node: %s", dataJsonObj.toString());
                        setRecord(id, dataJsonObj);
                    } else {
                        for (TraceSegmentRef segmentRef : segment.getRefs()) {
                            String nickName = Const.PEERS_FRONT_SPLIT + segmentRef.getPeerHost() + Const.PEERS_BEHIND_SPLIT;
                            dataJsonObj.addProperty(NodeIndex.NickName, nickName);
                            id = timeSlice + Const.ID_SPLIT + code;
                            logger.debug("refs node: %s", dataJsonObj.toString());
                            setRecord(id, dataJsonObj);
                        }
                    }
                } else {
                    logger.error("The span kind value is incorrect which segment record id is %s, the value must client or server", segment.getTraceSegmentId());
                }
            }
        }
    }
}