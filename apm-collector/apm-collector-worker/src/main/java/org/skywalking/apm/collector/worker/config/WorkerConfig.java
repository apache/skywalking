package org.skywalking.apm.collector.worker.config;

/**
 * @author pengys5
 */
public class WorkerConfig {

    public static class WorkerNum {
        public static class Node {
            public static class NodeCompAgg {
                public static int VALUE = 2;
            }

            public static class NodeMappingDayAgg {
                public static int VALUE = 2;
            }

            public static class NodeMappingHourAgg {
                public static int VALUE = 2;
            }

            public static class NodeMappingMinuteAgg {
                public static int VALUE = 2;
            }
        }

        public static class NodeRef {
            public static class NodeRefDayAgg {
                public static int VALUE = 2;
            }

            public static class NodeRefHourAgg {
                public static int VALUE = 2;
            }

            public static class NodeRefMinuteAgg {
                public static int VALUE = 2;
            }

            public static class NodeRefResSumDayAgg {
                public static int VALUE = 2;
            }

            public static class NodeRefResSumHourAgg {
                public static int VALUE = 2;
            }

            public static class NodeRefResSumMinuteAgg {
                public static int VALUE = 2;
            }
        }

        public static class GlobalTrace {
            public static class GlobalTraceAgg {
                public static int VALUE = 2;
            }
        }
    }

    public static class Queue {
        public static class GlobalTrace {
            public static class GlobalTraceAnalysis {
                public static int SIZE = 1024;
            }
        }

        public static class Segment {
            public static class SegmentAnalysis {
                public static int SIZE = 1024;
            }

            public static class SegmentCostAnalysis {
                public static int SIZE = 4096;
            }

            public static class SegmentExceptionAnalysis {
                public static int SIZE = 4096;
            }
        }

        public static class Node {
            public static class NodeCompAnalysis {
                public static int SIZE = 1024;
            }

            public static class NodeMappingDayAnalysis {
                public static int SIZE = 1024;
            }

            public static class NodeMappingHourAnalysis {
                public static int SIZE = 1024;
            }

            public static class NodeMappingMinuteAnalysis {
                public static int SIZE = 1024;
            }
        }

        public static class NodeRef {
            public static class NodeRefDayAnalysis {
                public static int SIZE = 1024;
            }

            public static class NodeRefHourAnalysis {
                public static int SIZE = 1024;
            }

            public static class NodeRefMinuteAnalysis {
                public static int SIZE = 1024;
            }

            public static class NodeRefResSumDayAnalysis {
                public static int SIZE = 1024;
            }

            public static class NodeRefResSumHourAnalysis {
                public static int SIZE = 1024;
            }

            public static class NodeRefResSumMinuteAnalysis {
                public static int SIZE = 1024;
            }
        }
    }
}
