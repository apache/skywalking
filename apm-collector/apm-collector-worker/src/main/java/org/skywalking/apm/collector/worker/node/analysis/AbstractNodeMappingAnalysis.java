package org.skywalking.apm.collector.worker.node.analysis;

import com.google.gson.JsonObject;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.skywalking.apm.collector.actor.ClusterWorkerContext;
import org.skywalking.apm.collector.actor.LocalWorkerContext;
import org.skywalking.apm.collector.actor.Role;
import org.skywalking.apm.collector.worker.Const;
import org.skywalking.apm.collector.worker.RecordAnalysisMember;
import org.skywalking.apm.collector.worker.node.NodeMappingIndex;
import org.skywalking.apm.collector.worker.tools.CollectionTools;
import org.skywalking.apm.network.proto.TraceSegmentObject;
import org.skywalking.apm.network.proto.TraceSegmentReference;

/**
 * @author pengys5
 */
abstract class AbstractNodeMappingAnalysis extends RecordAnalysisMember {

    private Logger logger = LogManager.getFormatterLogger(AbstractNodeMappingAnalysis.class);

    AbstractNodeMappingAnalysis(Role role, ClusterWorkerContext clusterContext,
        LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    final void analyseRefs(TraceSegmentObject segment, long timeSlice) {
        List<TraceSegmentReference> segmentRefList = segment.getRefsList();
        logger.debug("node mapping analysis refs isNotEmpty %s", CollectionTools.isNotEmpty(segmentRefList));

        if (CollectionTools.isNotEmpty(segmentRefList)) {
            logger.debug("node mapping analysis refs list SIZE: %s", segmentRefList.size());
            for (TraceSegmentReference segmentRef : segmentRefList) {
                String peers = Const.PEERS_FRONT_SPLIT + segmentRef.getNetworkAddress() + Const.PEERS_BEHIND_SPLIT;
                int code = segment.getApplicationId();

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
