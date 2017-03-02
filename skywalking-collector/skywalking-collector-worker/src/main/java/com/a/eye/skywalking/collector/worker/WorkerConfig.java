package com.a.eye.skywalking.collector.worker;

import com.a.eye.skywalking.collector.cluster.ClusterConfig;

/**
 * @author pengys5
 */
public class WorkerConfig extends ClusterConfig {

    public static class WorkerNum {
        public static int TraceSegmentReceiver_Num = 1;

        public static int DAGNodePersistence_Num = 5;
        public static int DAGNodeRefPersistence_Num = 5;
        public static int NodeInstancePersistence_Num = 5;
        public static int ResponseCostPersistence_Num = 5;
        public static int ResponseSummaryPersistence_Num = 5;
        public static int TraceSegmentRecordPersistence_Num = 5;
    }

}
