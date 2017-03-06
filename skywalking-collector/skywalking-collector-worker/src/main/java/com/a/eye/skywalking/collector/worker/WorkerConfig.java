package com.a.eye.skywalking.collector.worker;

import com.a.eye.skywalking.collector.cluster.ClusterConfig;

/**
 * @author pengys5
 */
public class WorkerConfig extends ClusterConfig {

    public static class Worker {
        public static class TraceSegmentReceiver {
            public static int Num = 5;
        }

        public static class DAGNodePersistence {
            public static int Num = 5;
        }

        public static class NodeInstancePersistence {
            public static int Num = 5;
        }

        public static class ResponseCostPersistence {
            public static int Num = 5;
        }

        public static class ResponseSummaryPersistence {
            public static int Num = 5;
        }

        public static class TraceSegmentRecordPersistence {
            public static int Num = 5;
        }

        public static class DAGNodeRefPersistence {
            public static int Num = 5;
        }
    }

}
