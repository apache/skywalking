package com.a.eye.skywalking.collector.worker.node.analysis;

import com.a.eye.skywalking.collector.actor.ClusterWorkerContext;
import com.a.eye.skywalking.collector.actor.LocalWorkerContext;
import com.a.eye.skywalking.collector.worker.RecordAnalysisMember;
import com.a.eye.skywalking.collector.worker.node.NodeCompIndex;
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
abstract class AbstractNodeCompAnalysis extends RecordAnalysisMember {

    private Logger logger = LogManager.getFormatterLogger(AbstractNodeCompAnalysis.class);

    AbstractNodeCompAnalysis(com.a.eye.skywalking.collector.actor.Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    void analyseSpans(TraceSegment segment) throws Exception {
        List<Span> spanList = segment.getSpans();
        logger.debug("node analysis span isNotEmpty %s", CollectionTools.isNotEmpty(spanList));

        if (CollectionTools.isNotEmpty(spanList)) {
            logger.debug("node analysis span list size: %s", spanList.size());
            for (Span span : spanList) {
                String kind = Tags.SPAN_KIND.get(span);
                if (Tags.SPAN_KIND_CLIENT.equals(kind) && ClientSpanIsLeafTools.isLeaf(span.getSpanId(), spanList)) {
                    String peers = SpanPeersTools.getPeers(span);

                    JsonObject compJsonObj = new JsonObject();
                    compJsonObj.addProperty(NodeCompIndex.Peers, peers);
                    compJsonObj.addProperty(NodeCompIndex.Name, Tags.COMPONENT.get(span));

                    setRecord(peers, compJsonObj);
                } else if (Tags.SPAN_KIND_SERVER.equals(kind) && span.getParentSpanId() == -1) {
                    String peers = segment.getApplicationCode();

                    JsonObject compJsonObj = new JsonObject();
                    compJsonObj.addProperty(NodeCompIndex.Peers, peers);
                    compJsonObj.addProperty(NodeCompIndex.Name, Tags.COMPONENT.get(span));

                    setRecord(peers, compJsonObj);
                } else {
                    logger.error("The span kind value is incorrect which segment record id is %s, the value must client or server", segment.getTraceSegmentId());
                }
            }
        }
    }
}