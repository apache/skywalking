package com.a.eye.skywalking.collector.worker.node.analysis;

import com.a.eye.skywalking.collector.actor.ClusterWorkerContext;
import com.a.eye.skywalking.collector.actor.LocalWorkerContext;
import com.a.eye.skywalking.collector.worker.Const;
import com.a.eye.skywalking.collector.worker.RecordAnalysisMember;
import com.a.eye.skywalking.collector.worker.node.NodeMappingIndex;
import com.a.eye.skywalking.collector.worker.segment.entity.Segment;
import com.a.eye.skywalking.collector.worker.segment.entity.TraceSegmentRef;
import com.a.eye.skywalking.collector.worker.tools.CollectionTools;
import com.google.gson.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

/**
 * @author pengys5
 */
abstract class AbstractNodeMappingAnalysis extends RecordAnalysisMember {

    private Logger logger = LogManager.getFormatterLogger(AbstractNodeMappingAnalysis.class);

    AbstractNodeMappingAnalysis(com.a.eye.skywalking.collector.actor.Role role, ClusterWorkerContext clusterContext,
        LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    final void analyseRefs(Segment segment, long timeSlice) throws Exception {
        List<TraceSegmentRef> segmentRefList = segment.getRefs();
        logger.debug("node mapping analysis refs isNotEmpty %s", CollectionTools.isNotEmpty(segmentRefList));

        if (CollectionTools.isNotEmpty(segmentRefList)) {
            logger.debug("node mapping analysis refs list SIZE: %s", segmentRefList.size());
            for (TraceSegmentRef segmentRef : segmentRefList) {
                String peers = Const.PEERS_FRONT_SPLIT + segmentRef.getPeerHost() + Const.PEERS_BEHIND_SPLIT;
                String code = segment.getApplicationCode();

                JsonObject nodeMappingJsonObj = new JsonObject();
                nodeMappingJsonObj.addProperty(NodeMappingIndex.CODE, code);
                nodeMappingJsonObj.addProperty(NodeMappingIndex.PEERS, peers);
                nodeMappingJsonObj.addProperty(NodeMappingIndex.TIME_SLICE, timeSlice);

                String id = timeSlice + Const.ID_SPLIT + code + Const.ID_SPLIT + peers;
                set(id, nodeMappingJsonObj);
            }
        }
    }
}
