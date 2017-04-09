package com.a.eye.skywalking.collector.worker.config;

/**
 * @author pengys5
 */
public class WorkerConfig {

    public static class WorkerNum {
        public static class Node {
            public static class NodeCompAgg {
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

        public static class NodeRef {
            public static class NodeRefDayAgg {
                public static int Value = 10;
            }

            public static class NodeRefHourAgg {
                public static int Value = 10;
            }

            public static class NodeRefMinuteAgg {
                public static int Value = 10;
            }

            public static class NodeRefResSumDayAgg {
                public static int Value = 10;
            }

            public static class NodeRefResSumHourAgg {
                public static int Value = 10;
            }

            public static class NodeRefResSumMinuteAgg {
                public static int Value = 10;
            }
        }

        public static class GlobalTrace {
            public static class GlobalTraceAgg {
                public static int Value = 10;
            }
        }
    }

    public static class Queue {
        public static class GlobalTrace {
            public static class GlobalTraceSave {
                public static int Size = 1024;
            }

            public static class GlobalTraceAnalysis {
                public static int Size = 1024;
            }
        }

        public static class Segment {
            public static class SegmentPost {
                public static int Size = 1024;
            }

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
            public static class NodeRefDayAnalysis {
                public static int Size = 1024;
            }

            public static class NodeRefHourAnalysis {
                public static int Size = 1024;
            }

            public static class NodeRefMinuteAnalysis {
                public static int Size = 1024;
            }

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

            public static class NodeRefResSumDayAnalysis {
                public static int Size = 1024;
            }

            public static class NodeRefResSumHourAnalysis {
                public static int Size = 1024;
            }

            public static class NodeRefResSumMinuteAnalysis {
                public static int Size = 1024;
            }
        }
    }
}
