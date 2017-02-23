package com.a.eye.skywalking.collector.cluster.config;

/**
 * Created by pengys5 on 2017/2/22 0022.
 */
public class CollectorConfig {

    public static final String appname = "CollectorSystem";

    public static class Collector {
        public static String hostname = "127.0.0.1";
        public static String port = "2551";
        public static String cluster = "127.0.0.1:2551";

        public static class Actor {
            public static int ActorManagerActor_Num = 2;
        }
    }
}
