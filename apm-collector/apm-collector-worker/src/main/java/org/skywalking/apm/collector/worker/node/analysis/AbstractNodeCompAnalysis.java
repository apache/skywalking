package org.skywalking.apm.collector.worker.node.analysis;

import com.google.gson.JsonObject;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.skywalking.apm.collector.actor.ClusterWorkerContext;
import org.skywalking.apm.collector.actor.LocalWorkerContext;
import org.skywalking.apm.collector.actor.Role;
import org.skywalking.apm.collector.worker.RecordAnalysisMember;
import org.skywalking.apm.collector.worker.node.NodeCompIndex;
import org.skywalking.apm.collector.worker.segment.entity.Segment;
import org.skywalking.apm.collector.worker.segment.entity.Span;
import org.skywalking.apm.collector.worker.segment.entity.tag.Tags;
import org.skywalking.apm.collector.worker.tools.ClientSpanIsLeafTools;
import org.skywalking.apm.collector.worker.tools.CollectionTools;
import org.skywalking.apm.collector.worker.tools.SpanPeersTools;

/**
 * @author pengys5
 */
abstract class AbstractNodeCompAnalysis extends RecordAnalysisMember {

    private Logger logger = LogManager.getFormatterLogger(AbstractNodeCompAnalysis.class);

    AbstractNodeCompAnalysis(Role role, ClusterWorkerContext clusterContext,
        LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    final void analyseSpans(Segment segment) {
        List<Span> spanList = segment.getSpans();
        logger.debug("node analysis span isNotEmpty %s", CollectionTools.isNotEmpty(spanList));

        if (CollectionTools.isNotEmpty(spanList)) {
            logger.debug("node analysis span list SIZE: %s", spanList.size());
            for (Span span : spanList) {
                String kind = Tags.SPAN_KIND.get(span);
                if (Tags.SPAN_KIND_CLIENT.equals(kind) && ClientSpanIsLeafTools.isLeaf(span.getSpanId(), spanList)) {
                    String peers = SpanPeersTools.INSTANCE.getPeers(span);

                    JsonObject compJsonObj = new JsonObject();
                    compJsonObj.addProperty(NodeCompIndex.PEERS, peers);
                    compJsonObj.addProperty(NodeCompIndex.NAME, Tags.COMPONENT.get(span));

                    set(peers, compJsonObj);
                } else if (Tags.SPAN_KIND_SERVER.equals(kind) && span.getParentSpanId() == -1) {
                    String peers = segment.getApplicationCode();

                    JsonObject compJsonObj = new JsonObject();
                    compJsonObj.addProperty(NodeCompIndex.PEERS, peers);
                    compJsonObj.addProperty(NodeCompIndex.NAME, Tags.COMPONENT.get(span));

                    set(peers, compJsonObj);
                }
            }
        }
    }
}
