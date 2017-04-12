package com.a.eye.skywalking.collector.worker.config;

/**
 * @author pengys5
 */
public class EsConfig {

    public static class Es {
        public static class Cluster {
            public static String name = "";
            public static String nodes = "";

            public static class Transport {
                public static String sniffer = "";
            }
        }

        public static class Index {

            public static class Initialize {
                public static String model = "";
            }

            public static class Shards {
                public static String number = "";
            }

            public static class Replicas {
                public static String number = "";
            }
        }
    }

    public enum IndexInitModel {
        auto, forced, manual
    }
}
