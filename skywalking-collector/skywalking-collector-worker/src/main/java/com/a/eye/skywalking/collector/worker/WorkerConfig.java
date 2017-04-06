package com.a.eye.skywalking.collector.worker;

import com.a.eye.skywalking.collector.cluster.ClusterConfig;

/**
 * @author pengys5
 */
public class WorkerConfig extends ClusterConfig {

    public static class Analysis {
        public static class Data {
            public static int size = 1000;
        }
    }

    public static class Persistence {
        public static class Data {
            public static int size = 1000;
        }
    }

    public static class Worker {
        public static class TraceSegmentReceiver {
            public static int Num = 10;
        }

        public static class DAGNodeReceiver {
            public static int Num = 10;
        }

        public static class NodeInstanceReceiver {
            public static int Num = 10;
        }

        public static class ResponseCostReceiver {
            public static int Num = 10;
        }

        public static class ResponseSummaryReceiver {
            public static int Num = 10;
        }

        public static class DAGNodeRefReceiver {
            public static int Num = 10;
        }
    }

    public static class WorkerNum {
        public static class Node {
            public static class NodeDayAgg {
                public static int Value = 10;
            }

            public static class NodeHourAgg {
                public static int Value = 10;
            }

            public static class NodeMinuteAgg {
                public static int Value = 10;
            }

            public static class NodeMappingDayAgg {
                public static int Value = 10;
            }

            public static class NodeMappingHourAgg {
                public static int Value = 10;
            }

            public static class NodeMappingMinuteAgg {
                public static int Value = 10;
            }
        }
    }

    public static class Queue {
        public static class Segment {
            public static class SegmentCostSave {
                public static int Size = 1024;
            }

            public static class SegmentSave {
                public static int Size = 1024;
            }

            public static class SegmentExceptionSave {
                public static int Size = 1024;
            }
        }

        public static class Node {
            public static class NodeCompAnalysis {
                public static int Size = 1024;
            }

            public static class NodeRefDayAnalysis {
                public static int Size = 1024;
            }

            public static class NodeRefHourAnalysis {
                public static int Size = 1024;
            }

            public static class NodeRefMinuteAnalysis {
                public static int Size = 1024;
            }

            public static class NodeMappingDayAnalysis {
                public static int Size = 1024;
            }

            public static class NodeMappingHourAnalysis {
                public static int Size = 1024;
            }

            public static class NodeMappingMinuteAnalysis {
                public static int Size = 1024;
            }

            public static class NodeCompSave {
                public static int Size = 1024;
            }

            public static class NodeMappingDaySave {
                public static int Size = 1024;
            }

            public static class NodeMappingHourSave {
                public static int Size = 1024;
            }

            public static class NodeMappingMinuteSave {
                public static int Size = 1024;
            }
        }

        public static class NodeRef {
            public static class NodeRefDaySave {
                public static int Size = 1024;
            }

            public static class NodeRefHourSave {
                public static int Size = 1024;
            }

            public static class NodeRefMinuteSave {
                public static int Size = 1024;
            }

            public static class NodeRefResSumDaySave {
                public static int Size = 1024;
            }

            public static class NodeRefResSumHourSave {
                public static int Size = 1024;
            }

            public static class NodeRefResSumMinuteSave {
                public static int Size = 1024;
            }
        }


        public static class Persistence {
            public static class DAGNodePersistence {
                public static int Size = 1024;
            }

            public static class NodeInstancePersistence {
                public static int Size = 1024;
            }

            public static class ResponseCostPersistence {
                public static int Size = 1024;
            }

            public static class ResponseSummaryPersistence {
                public static int Size = 1024;
            }

            public static class DAGNodeRefPersistence {
                public static int Size = 1024;
            }
        }


        public static class TraceSegmentRecordAnalysis {
            public static int Size = 1024;
        }

        public static class NodeInstanceAnalysis {
            public static int Size = 1024;
        }

        public static class DAGNodeAnalysis {
            public static int Size = 1024;
        }

        public static class ResponseCostAnalysis {
            public static int Size = 1024;
        }

        public static class ResponseSummaryAnalysis {
            public static int Size = 1024;
        }

        public static class DAGNodeRefAnalysis {
            public static int Size = 1024;
        }
    }
}
