package com.a.eye.skywalking.collector.worker.config;

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
        }
    }

    public enum IndexInitMode {
        AUTO,
        FORCED,
        MANUAL
    }
}
