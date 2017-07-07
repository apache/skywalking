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
import org.skywalking.apm.collector.worker.tools.CollectionTools;
import org.skywalking.apm.collector.worker.tools.SpanPeersTools;
import org.skywalking.apm.network.proto.SpanObject;
import org.skywalking.apm.network.proto.SpanType;
import org.skywalking.apm.network.proto.TraceSegmentObject;

/**
 * @author pengys5
 */
abstract class AbstractNodeCompAnalysis extends RecordAnalysisMember {

    private Logger logger = LogManager.getFormatterLogger(AbstractNodeCompAnalysis.class);

    AbstractNodeCompAnalysis(Role role, ClusterWorkerContext clusterContext,
        LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    final void analyseSpans(TraceSegmentObject segment) {
        List<SpanObject> spanList = segment.getSpansList();
        logger.debug("node analysis span isNotEmpty %s", CollectionTools.isNotEmpty(spanList));

        if (CollectionTools.isNotEmpty(spanList)) {
            logger.debug("node analysis span list SIZE: %s", spanList.size());
            for (SpanObject span : spanList) {
                if (SpanType.Exit.equals(span.getSpanType())) {
                    int peers = SpanPeersTools.INSTANCE.getPeers(span);

                    JsonObject compJsonObj = new JsonObject();
                    compJsonObj.addProperty(NodeCompIndex.PEERS, peers);
                    compJsonObj.addProperty(NodeCompIndex.NAME, span.getComponent());

                    set(String.valueOf(peers), compJsonObj);
                } else if (SpanType.Entry.equals(span.getSpanType()) && span.getParentSpanId() == -1) {
                    int peers = segment.getApplicationId();

                    JsonObject compJsonObj = new JsonObject();
                    compJsonObj.addProperty(NodeCompIndex.PEERS, peers);
                    compJsonObj.addProperty(NodeCompIndex.NAME, span.getComponent());

                    set(String.valueOf(peers), compJsonObj);
                }
            }
        }
    }
}
