package org.skywalking.apm.collector.worker.config;

/**
 * @author pengys5
 */
public class EsConfig {

    public static class Es {
        public static class Cluster {
            public static String NAME = "";
            public static String NODES = "";

            public static class Transport {
                public static String SNIFFER = "";
            }
        }

        public static class Persistence {
            public static class Timer {
                public static Integer VALUE = 3;
            }
        }

        public static class Index {

            public static class Initialize {
                public static IndexInitMode MODE;
            }

            public static class Shards {
                public static String NUMBER = "";
            }

            public static class Replicas {
                public static String NUMBER = "";
            }

            public static class RefreshInterval {
                public static class GlobalTraceIndex {
                    public static Integer VALUE = 1;
                }

                public static class NodeCompIndex {
                    public static Integer VALUE = 1;
                }

                public static class NodeMappingIndex {
                    public static Integer VALUE = 1;
                }

                public static class NodeRefIndex {
                    public static Integer VALUE = 1;
                }

                public static class NodeRefResSumIndex {
                    public static Integer VALUE = 1;
                }

                public static class SegmentCostIndex {
                    public static Integer VALUE = 10;
                }

                public static class SegmentExceptionIndex {
                    public static Integer VALUE = 10;
                }

                public static class SegmentIndex {
                    public static Integer VALUE = 10;
                }
            }
        }
    }

    public enum IndexInitMode {
        AUTO,
        FORCED,
        MANUAL
    }
}
