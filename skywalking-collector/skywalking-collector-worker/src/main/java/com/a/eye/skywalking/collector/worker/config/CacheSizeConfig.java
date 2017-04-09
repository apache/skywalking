package com.a.eye.skywalking.collector.worker.config;

/**
 * @author pengys5
 */
public class CacheSizeConfig {

    public static class Cache {
        public static class Analysis {
            public static int size = 1000;
        }

        public static class Persistence {
            public static int size = 1000;
        }
    }
}
