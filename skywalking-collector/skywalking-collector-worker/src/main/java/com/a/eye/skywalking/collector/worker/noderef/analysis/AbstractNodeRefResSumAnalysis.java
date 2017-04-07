package com.a.eye.skywalking.collector.worker.noderef.analysis;

import com.a.eye.skywalking.collector.actor.ClusterWorkerContext;
import com.a.eye.skywalking.collector.actor.LocalWorkerContext;
import com.a.eye.skywalking.collector.worker.MetricAnalysisMember;
import com.a.eye.skywalking.collector.worker.noderef.NodeRefResSumIndex;
import com.a.eye.skywalking.collector.worker.storage.AbstractTimeSlice;

/**
 * @author pengys5
 */
abstract class AbstractNodeRefResSumAnalysis extends MetricAnalysisMember {

    AbstractNodeRefResSumAnalysis(com.a.eye.skywalking.collector.actor.Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    final void analyseResSum(NodeRefResRecord nodeRefRes) throws Exception {
        long startTime = nodeRefRes.startTime;
        long endTime = nodeRefRes.endTime;
        boolean isError = nodeRefRes.isError;
        long cost = endTime - startTime;

        setMetric(nodeRefRes.nodeRefId, NodeRefResSumIndex.OneSecondLess, 0L);
        setMetric(nodeRefRes.nodeRefId, NodeRefResSumIndex.ThreeSecondLess, 0L);
        setMetric(nodeRefRes.nodeRefId, NodeRefResSumIndex.FiveSecondLess, 0L);
        setMetric(nodeRefRes.nodeRefId, NodeRefResSumIndex.FiveSecondGreater, 0L);
        setMetric(nodeRefRes.nodeRefId, NodeRefResSumIndex.Error, 0L);
        if (cost <= 1000 && !isError) {
            setMetric(nodeRefRes.nodeRefId, NodeRefResSumIndex.OneSecondLess, 1L);
        } else if (1000 < cost && cost <= 3000 && !isError) {
            setMetric(nodeRefRes.nodeRefId, NodeRefResSumIndex.ThreeSecondLess, 1L);
        } else if (3000 < cost && cost <= 5000 && !isError) {
            setMetric(nodeRefRes.nodeRefId, NodeRefResSumIndex.FiveSecondLess, 1L);
        } else if (5000 < cost && !isError) {
            setMetric(nodeRefRes.nodeRefId, NodeRefResSumIndex.FiveSecondGreater, 1L);
        } else {
            setMetric(nodeRefRes.nodeRefId, NodeRefResSumIndex.Error, 1L);
        }
        setMetric(nodeRefRes.nodeRefId, NodeRefResSumIndex.Summary, 1L);
    }

    public static class NodeRefResRecord extends AbstractTimeSlice {
        private String nodeRefId;
        private long startTime;
        private long endTime;
        private Boolean isError;

        NodeRefResRecord(long minute, long hour, long day, int second) {
            super(minute, hour, day, second);
        }

        void setNodeRefId(String nodeRefId) {
            this.nodeRefId = nodeRefId;
        }

        void setStartTime(long startTime) {
            this.startTime = startTime;
        }

        void setEndTime(long endTime) {
            this.endTime = endTime;
        }

        void setError(Boolean error) {
            isError = error;
        }

        String getNodeRefId() {
            return nodeRefId;
        }
    }
}
