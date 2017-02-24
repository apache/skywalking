package com.a.eye.skywalking.collector.cluster;

/**
 * @author pengys5
 */
public class ClusterConfig {

    public static class Cluster {
        public static class Current {
            public static String hostname = "127.0.0.1";
            public static String port = "2551";
            public static String roles = "";
        }

        public static String nodes = "127.0.0.1:2551";

        public static final String appname = "CollectorSystem";
        public static final String provider = "akka.cluster.ClusterActorRefProvider";
    }
}
